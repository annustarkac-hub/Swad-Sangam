-- =====================================================================
-- Swad Sangam ERP — employees, attendance, salary advances, payroll
-- Attendance rule (simple and common for small restaurants): days without
-- a mark count as present; ABSENT and unpaid LEAVE are deducted, HALF_DAY
-- counts as half. Salary = monthly × payable days ÷ days in month.
-- =====================================================================

create table public.employees (
  id                   bigint generated always as identity primary key,
  name                 text not null,
  phone                text,
  designation          text not null default 'Staff',
  staff_user_id        uuid references public.staff(user_id),
  monthly_salary_paise bigint not null default 0 check (monthly_salary_paise >= 0),
  joined_on            date,
  weekly_off           smallint check (weekly_off between 1 and 7),   -- ISO day
  is_active            boolean not null default true,
  notes                text,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);
create trigger trg_employees_touch before update on public.employees for each row execute function private.touch_updated_at();

create table public.attendance (
  employee_id bigint not null references public.employees(id),
  day         date not null,
  status      text not null check (status in ('PRESENT','ABSENT','HALF_DAY','PAID_LEAVE','LEAVE','WEEK_OFF')),
  clock_in    timestamptz,
  clock_out   timestamptz,
  overtime_hours numeric(5,2) not null default 0,
  note        text,
  marked_by   uuid,
  primary key (employee_id, day)
);

create table public.salary_advances (
  id           bigint generated always as identity primary key,
  employee_id  bigint not null references public.employees(id),
  amount_paise bigint not null check (amount_paise > 0),
  tender       public.tender not null check (tender in ('CASH','UPI','BANK')),
  given_on     date not null,
  note         text,
  journal_entry_id bigint references public.journal_entries(id),
  created_by   uuid,
  created_at   timestamptz not null default now()
);

create table public.payroll_lines (
  id                 bigint generated always as identity primary key,
  month              date not null check (extract(day from month) = 1),
  employee_id        bigint not null references public.employees(id),
  days_in_month      int not null,
  payable_days       numeric(5,1) not null,
  gross_paise        bigint not null,
  overtime_paise     bigint not null default 0,
  bonus_paise        bigint not null default 0,
  advance_recovered_paise bigint not null default 0,
  other_deduction_paise   bigint not null default 0,
  net_paise          bigint not null,
  tender             public.tender not null,
  journal_entry_id   bigint references public.journal_entries(id),
  created_by         uuid,
  created_at         timestamptz not null default now(),
  unique (month, employee_id)
);

create or replace function public.mark_attendance(p_employee_id bigint, p_day date, p_status text,
    p_clock_in timestamptz default null, p_clock_out timestamptz default null, p_overtime numeric default 0, p_note text default null)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  if exists (select 1 from public.payroll_lines where employee_id = p_employee_id and month = date_trunc('month', p_day)::date) then
    raise exception 'PAYROLL_ALREADY_RUN for this month' using errcode = '22023';
  end if;
  insert into public.attendance(employee_id, day, status, clock_in, clock_out, overtime_hours, note, marked_by)
  values (p_employee_id, p_day, p_status, p_clock_in, p_clock_out, coalesce(p_overtime,0), p_note, auth.uid())
  on conflict (employee_id, day) do update set status = excluded.status, clock_in = excluded.clock_in,
     clock_out = excluded.clock_out, overtime_hours = excluded.overtime_hours, note = excluded.note, marked_by = excluded.marked_by;
end $$;

create or replace function public.give_salary_advance(p_employee_id bigint, p_amount_paise bigint, p_tender public.tender,
                                                      p_note text default null) returns bigint
language plpgsql volatile security definer set search_path = '' as $$
declare v_id bigint; v_je bigint;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  insert into public.salary_advances(employee_id, amount_paise, tender, given_on, note, created_by)
  values (p_employee_id, p_amount_paise, p_tender, (now() at time zone 'Asia/Kolkata')::date, p_note, auth.uid()) returning id into v_id;
  v_je := private.post_journal(null, 'Salary advance to ' || (select name from public.employees where id = p_employee_id), 'ADVANCE', v_id::text,
    jsonb_build_array(jsonb_build_object('account','1400','debit',p_amount_paise,'partyType','EMPLOYEE','partyId',p_employee_id),
                      jsonb_build_object('account', private.tender_account(p_tender), 'credit', p_amount_paise)));
  update public.salary_advances set journal_entry_id = v_je where id = v_id;
  return v_id;
end $$;

/** Calculates payroll for a month (preview) without posting. */
create or replace function public.payroll_preview(p_month date, p_adjust jsonb default '{}'::jsonb) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_m date := date_trunc('month', p_month)::date; v_days int; e record; v_out jsonb := '[]'::jsonb;
        v_absent numeric; v_pay numeric; v_gross bigint; v_ot bigint; v_adv_due bigint; v_adv bigint; v_bonus bigint; v_ded bigint; a jsonb;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  v_days := extract(day from (v_m + interval '1 month - 1 day'))::int;
  for e in select * from public.employees where is_active order by name loop
    select coalesce(sum(case status when 'ABSENT' then 1 when 'LEAVE' then 1 when 'HALF_DAY' then 0.5 else 0 end), 0),
           coalesce(sum(overtime_hours), 0)
      into v_absent, v_pay
      from public.attendance where employee_id = e.id and day >= v_m and day < v_m + interval '1 month';
    a := coalesce(p_adjust->(e.id::text), '{}'::jsonb);
    -- hourly overtime rate = monthly / (days × 8 h)
    v_ot := round(v_pay * e.monthly_salary_paise::numeric / (v_days * 8))::bigint;
    v_gross := round(e.monthly_salary_paise::numeric * (v_days - v_absent) / v_days)::bigint;
    v_adv_due := private.party_balance('1400','EMPLOYEE', e.id);
    v_bonus := coalesce((a->>'bonusPaise')::bigint, 0);
    v_ded := coalesce((a->>'otherDeductionPaise')::bigint, 0);
    v_adv := least(greatest(v_adv_due, 0), coalesce((a->>'advanceRecoveryPaise')::bigint, v_adv_due), v_gross + v_ot + v_bonus - v_ded);
    v_out := v_out || jsonb_build_array(jsonb_build_object(
      'employeeId', e.id, 'name', e.name, 'designation', e.designation, 'monthlyPaise', e.monthly_salary_paise,
      'daysInMonth', v_days, 'payableDays', v_days - v_absent, 'grossPaise', v_gross, 'overtimePaise', v_ot,
      'bonusPaise', v_bonus, 'advanceOutstandingPaise', v_adv_due, 'advanceRecoveredPaise', greatest(v_adv, 0),
      'otherDeductionPaise', v_ded, 'netPaise', v_gross + v_ot + v_bonus - v_ded - greatest(v_adv, 0),
      'alreadyPaid', exists (select 1 from public.payroll_lines where month = v_m and employee_id = e.id)));
  end loop;
  return v_out;
end $$;

/** Posts and pays the month's salaries (once per employee per month). */
create or replace function public.run_payroll(p_month date, p_tender public.tender, p_adjust jsonb default '{}'::jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v_m date := date_trunc('month', p_month)::date; x jsonb; v_id bigint; v_je bigint; n int := 0; v_total bigint := 0;
begin
  perform private.require_role('OWNER','ACCOUNTANT');
  if p_tender not in ('CASH','UPI','BANK') then raise exception 'INVALID_TENDER' using errcode = '22023'; end if;
  for x in select * from jsonb_array_elements(public.payroll_preview(v_m, p_adjust)) loop
    continue when (x->>'alreadyPaid')::boolean or (x->>'netPaise')::bigint < 0
              or ((x->>'grossPaise')::bigint + (x->>'overtimePaise')::bigint + (x->>'bonusPaise')::bigint) = 0;
    insert into public.payroll_lines(month, employee_id, days_in_month, payable_days, gross_paise, overtime_paise, bonus_paise,
        advance_recovered_paise, other_deduction_paise, net_paise, tender, created_by)
    values (v_m, (x->>'employeeId')::bigint, (x->>'daysInMonth')::int, (x->>'payableDays')::numeric, (x->>'grossPaise')::bigint,
        (x->>'overtimePaise')::bigint, (x->>'bonusPaise')::bigint, (x->>'advanceRecoveredPaise')::bigint,
        (x->>'otherDeductionPaise')::bigint, (x->>'netPaise')::bigint, p_tender, auth.uid())
    returning id into v_id;
    v_je := private.post_journal((v_m + interval '1 month - 1 day')::date,
      'Salary ' || to_char(v_m, 'Mon YYYY') || ' — ' || (x->>'name'), 'PAYROLL', v_id::text,
      jsonb_build_array(
        jsonb_build_object('account','5200','debit', (x->>'grossPaise')::bigint + (x->>'overtimePaise')::bigint + (x->>'bonusPaise')::bigint),
        jsonb_build_object('account','1400','credit', (x->>'advanceRecoveredPaise')::bigint, 'partyType','EMPLOYEE','partyId',(x->>'employeeId')::bigint),
        jsonb_build_object('account','4100','credit', (x->>'otherDeductionPaise')::bigint),
        jsonb_build_object('account', private.tender_account(p_tender), 'credit', (x->>'netPaise')::bigint)));
    update public.payroll_lines set journal_entry_id = v_je where id = v_id;
    n := n + 1; v_total := v_total + (x->>'netPaise')::bigint;
  end loop;
  return jsonb_build_object('employeesPaid', n, 'totalNetPaise', v_total);
end $$;
