-- =====================================================================
-- Swad Sangam ERP — double-entry accounting core
-- Rules: every journal entry balances (total debit = total credit);
-- posted entries and lines are never edited or deleted — corrections are
-- made with reversal entries. Amounts are integer paise.
-- =====================================================================

-- Role helpers: CASHIER and WAITER count as front-of-house STAFF wherever a
-- function allows STAFF. ACCOUNTANT is only allowed where listed explicitly.
create or replace function private.role_matches(r public.staff_role, p_roles public.staff_role[]) returns boolean
language sql immutable as $$
  select r is not null and (r = any(p_roles)
    or (r::text in ('CASHIER','WAITER') and 'STAFF'::public.staff_role = any(p_roles)))
$$;

create or replace function private.has_role(variadic p_roles public.staff_role[]) returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce(private.role_matches(private.my_role(), p_roles), false)
$$;

create or replace function private.require_role(variadic p_roles public.staff_role[]) returns public.staff_role
language plpgsql stable security definer set search_path = '' as $$
declare r public.staff_role;
begin
  r := private.my_role();
  if not coalesce(private.role_matches(r, p_roles), false) then
    raise exception 'NOT_AUTHORIZED' using errcode = '42501';
  end if;
  return r;
end $$;
grant execute on function private.role_matches(public.staff_role, public.staff_role[]) to anon, authenticated;

-- GST settings (never assumed — owner/accountant must configure and verify)
alter table public.restaurant_settings
  add column if not exists gst_registered boolean not null default false,
  add column if not exists gst_scheme text not null default 'REGULAR' check (gst_scheme in ('REGULAR','COMPOSITION')),
  add column if not exists gst_input_credit boolean not null default false,
  add column if not exists sac_code text not null default '996331',
  add column if not exists place_of_supply text not null default 'Uttarakhand',
  add column if not exists invoice_footer text,
  add column if not exists receipt_width_mm int not null default 80 check (receipt_width_mm in (58, 80));

-- Generic document counters (JV-2026-00001, EXP-2026-0001 …)
create table private.counters (name text, year int, last int not null default 0, primary key (name, year));

create or replace function private.next_number(p_prefix text, p_pad int default 5) returns text
language plpgsql volatile security definer set search_path = '' as $$
declare v_year int := extract(year from (now() at time zone 'Asia/Kolkata'))::int; v int;
begin
  insert into private.counters(name, year, last) values (p_prefix, v_year, 1)
  on conflict (name, year) do update set last = private.counters.last + 1 returning last into v;
  return format('%s-%s-%s', p_prefix, v_year, lpad(v::text, p_pad, '0'));
end $$;

-- ---------------------------------------------------------------------
-- Chart of accounts
-- ---------------------------------------------------------------------
create type public.account_type as enum ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE');
create type public.party_type   as enum ('CUSTOMER','SUPPLIER','EMPLOYEE');
create type public.tender       as enum ('CASH','UPI','CARD','BANK','CREDIT');

create table public.accounts (
  id         int generated always as identity primary key,
  code       text not null unique check (code ~ '^[0-9]{4}$'),
  name       text not null,
  type       public.account_type not null,
  is_system  boolean not null default false,  -- used by automatic postings; cannot be deactivated
  is_active  boolean not null default true,
  created_at timestamptz not null default now()
);

insert into public.accounts(code, name, type, is_system) values
  ('1000','Cash in hand','ASSET',true),
  ('1010','Bank account','ASSET',true),
  ('1020','UPI clearing','ASSET',true),
  ('1100','Accounts receivable — customers (khata)','ASSET',true),
  ('1150','Bills pending payment','ASSET',true),
  ('1200','Inventory — stock','ASSET',true),
  ('1300','Input GST (purchases)','ASSET',true),
  ('1400','Staff salary advances','ASSET',true),
  ('1500','Fixed assets (equipment, furniture)','ASSET',false),
  ('2000','Accounts payable — suppliers','LIABILITY',true),
  ('2100','Output CGST payable','LIABILITY',true),
  ('2101','Output SGST payable','LIABILITY',true),
  ('2102','Output IGST payable','LIABILITY',false),
  ('2200','Loans','LIABILITY',false),
  ('2300','Salary payable','LIABILITY',true),
  ('3000','Owner capital','EQUITY',true),
  ('3100','Owner drawings','EQUITY',false),
  ('3900','Retained earnings','EQUITY',true),
  ('4000','Food sales','INCOME',true),
  ('4010','Packaging & delivery charges collected','INCOME',true),
  ('4100','Other income','INCOME',false),
  ('5000','Food cost (ingredients consumed)','EXPENSE',true),
  ('5010','Purchases — non-stock','EXPENSE',true),
  ('5100','Packaging','EXPENSE',false),
  ('5200','Salaries & wages','EXPENSE',true),
  ('5300','Rent','EXPENSE',false),
  ('5400','Electricity','EXPENSE',false),
  ('5410','Water','EXPENSE',false),
  ('5420','Gas / LPG','EXPENSE',false),
  ('5430','Internet & phone','EXPENSE',false),
  ('5500','Repairs & maintenance','EXPENSE',false),
  ('5600','Transport','EXPENSE',false),
  ('5700','Marketing','EXPENSE',false),
  ('5800','Cleaning','EXPENSE',false),
  ('5850','Wastage & spoilage','EXPENSE',true),
  ('5860','Stock count differences','EXPENSE',true),
  ('5900','Discounts allowed','EXPENSE',true),
  ('5910','Sales refunds','EXPENSE',true),
  ('5950','Cash shortage / (excess)','EXPENSE',true),
  ('5990','Miscellaneous expenses','EXPENSE',false);

create or replace function private.acct(p_code text) returns int
language plpgsql stable security definer set search_path = '' as $$
declare v int;
begin
  select id into v from public.accounts where code = p_code;
  if v is null then raise exception 'ACCOUNT_NOT_FOUND: %', p_code; end if;
  return v;
end $$;

create or replace function private.tender_account(p public.tender) returns text
language sql immutable as $$
  select case p when 'CASH' then '1000' when 'UPI' then '1020' when 'CARD' then '1010'
                when 'BANK' then '1010' when 'CREDIT' then '1100' end
$$;

-- ---------------------------------------------------------------------
-- Journal
-- ---------------------------------------------------------------------
create table public.journal_entries (
  id             bigint generated always as identity primary key,
  entry_no       text not null unique,
  entry_date     date not null,
  narration      text not null,
  source         text not null,       -- SALE, PAYMENT, REFUND, EXPENSE, PURCHASE, SUPPLIER_PAYMENT, CUSTOMER_RECEIPT, COGS, STOCK, CASH_SESSION, PAYROLL, ADVANCE, MANUAL, REVERSAL
  source_ref     text not null,
  status         text not null default 'POSTED' check (status in ('POSTED','REVERSED')),
  reverses_id    bigint references public.journal_entries(id),
  reversed_by_id bigint references public.journal_entries(id),
  created_by     uuid,
  created_at     timestamptz not null default now(),
  unique (source, source_ref)           -- automatic postings are idempotent
);
create index journal_entries_date_idx on public.journal_entries(entry_date);

create table public.journal_lines (
  id           bigint generated always as identity primary key,
  entry_id     bigint not null references public.journal_entries(id),
  account_id   int not null references public.accounts(id),
  debit_paise  bigint not null default 0 check (debit_paise >= 0),
  credit_paise bigint not null default 0 check (credit_paise >= 0),
  party_type   public.party_type,
  party_id     bigint,
  memo         text,
  check ((debit_paise > 0) <> (credit_paise > 0)),
  check ((party_type is null) = (party_id is null))
);
create index journal_lines_entry_idx on public.journal_lines(entry_id);
create index journal_lines_account_idx on public.journal_lines(account_id);
create index journal_lines_party_idx on public.journal_lines(party_type, party_id);

-- Balance check at commit time (defence in depth — post_journal also checks)
create or replace function private.check_entry_balanced() returns trigger
language plpgsql as $$
declare d bigint; c bigint;
begin
  select coalesce(sum(debit_paise),0), coalesce(sum(credit_paise),0) into d, c
    from public.journal_lines where entry_id = new.entry_id;
  if d <> c then raise exception 'UNBALANCED_ENTRY: entry % debit % credit %', new.entry_id, d, c; end if;
  return null;
end $$;
create constraint trigger trg_journal_balanced after insert on public.journal_lines
  deferrable initially deferred for each row execute function private.check_entry_balanced();

create trigger trg_journal_lines_immutable before update or delete on public.journal_lines
  for each row execute function private.block_change();

create or replace function private.journal_entry_guard() returns trigger
language plpgsql as $$
begin
  if tg_op = 'DELETE' then raise exception 'journal entries cannot be deleted — post a reversal'; end if;
  if (new.entry_no, new.entry_date, new.narration, new.source, new.source_ref, new.reverses_id, new.created_at)
     is distinct from (old.entry_no, old.entry_date, old.narration, old.source, old.source_ref, old.reverses_id, old.created_at)
     or old.status = 'REVERSED' then
    raise exception 'posted journal entries cannot be edited — post a reversal';
  end if;
  return new;
end $$;
create trigger trg_journal_entries_guard before update or delete on public.journal_entries
  for each row execute function private.journal_entry_guard();

/**
 * Posts a balanced entry. p_lines: [{"account":"1000","debit":100,"credit":0,"partyType":"CUSTOMER","partyId":1,"memo":""}]
 * Zero lines are skipped. Returns the existing entry id when (source, ref) was already posted.
 */
create or replace function private.post_journal(p_date date, p_narration text, p_source text, p_ref text, p_lines jsonb)
returns bigint language plpgsql volatile security definer set search_path = '' as $$
declare v_id bigint; l jsonb; d bigint := 0; c bigint := 0; n int := 0;
begin
  select id into v_id from public.journal_entries where source = p_source and source_ref = p_ref;
  if v_id is not null then return v_id; end if;
  for l in select * from jsonb_array_elements(p_lines) loop
    if coalesce((l->>'debit')::bigint,0) < 0 or coalesce((l->>'credit')::bigint,0) < 0 then
      raise exception 'NEGATIVE_AMOUNT';
    end if;
    d := d + coalesce((l->>'debit')::bigint,0);
    c := c + coalesce((l->>'credit')::bigint,0);
    if coalesce((l->>'debit')::bigint,0) + coalesce((l->>'credit')::bigint,0) > 0 then n := n + 1; end if;
  end loop;
  if n = 0 then return null; end if;          -- nothing to post (all zero)
  if d <> c then raise exception 'UNBALANCED_ENTRY: debit % credit %', d, c using errcode = '22023'; end if;

  insert into public.journal_entries(entry_no, entry_date, narration, source, source_ref, created_by)
  values (private.next_number('JV'), coalesce(p_date, (now() at time zone 'Asia/Kolkata')::date),
          left(p_narration, 300), p_source, p_ref, auth.uid())
  returning id into v_id;

  insert into public.journal_lines(entry_id, account_id, debit_paise, credit_paise, party_type, party_id, memo)
  select v_id, private.acct(x->>'account'),
         case when coalesce((x->>'debit')::bigint,0) > 0 then (x->>'debit')::bigint else 0 end,
         case when coalesce((x->>'credit')::bigint,0) > 0 then (x->>'credit')::bigint else 0 end,
         (x->>'partyType')::public.party_type, (x->>'partyId')::bigint, left(x->>'memo', 200)
    from jsonb_array_elements(p_lines) x
   where coalesce((x->>'debit')::bigint,0) + coalesce((x->>'credit')::bigint,0) > 0;
  return v_id;
end $$;

-- Manual journal (opening balances, owner capital, loans, depreciation …)
create or replace function public.create_manual_journal(p jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v_id bigint; v_lines jsonb := '[]'::jsonb; l jsonb;
begin
  perform private.require_role('OWNER','ACCOUNTANT');
  if length(trim(coalesce(p->>'narration',''))) < 3 then raise exception 'NARRATION_REQUIRED' using errcode = '22023'; end if;
  for l in select * from jsonb_array_elements(p->'lines') loop
    v_lines := v_lines || jsonb_build_array(jsonb_build_object(
      'account', l->>'accountCode', 'debit', coalesce((l->>'debitPaise')::bigint,0),
      'credit', coalesce((l->>'creditPaise')::bigint,0), 'partyType', l->>'partyType',
      'partyId', l->>'partyId', 'memo', l->>'memo'));
  end loop;
  v_id := private.post_journal((p->>'date')::date, p->>'narration', 'MANUAL', gen_random_uuid()::text, v_lines);
  if v_id is null then raise exception 'EMPTY_ENTRY' using errcode = '22023'; end if;
  return jsonb_build_object('id', v_id, 'entryNo', (select entry_no from public.journal_entries where id = v_id));
end $$;

-- Corrections: a reversal swaps debits and credits; then post the correct entry.
create or replace function public.reverse_journal(p_entry_id bigint, p_reason text) returns bigint
language plpgsql volatile security definer set search_path = '' as $$
declare e public.journal_entries; v_id bigint;
begin
  perform private.require_role('OWNER','ACCOUNTANT');
  if length(trim(coalesce(p_reason,''))) < 3 then raise exception 'REASON_REQUIRED' using errcode = '22023'; end if;
  select * into e from public.journal_entries where id = p_entry_id for update;
  if not found then raise exception 'ENTRY_NOT_FOUND'; end if;
  if e.status = 'REVERSED' or e.source = 'REVERSAL' then raise exception 'ALREADY_REVERSED' using errcode = '22023'; end if;
  v_id := private.post_journal((now() at time zone 'Asia/Kolkata')::date,
            'Reversal of ' || e.entry_no || ': ' || p_reason, 'REVERSAL', e.id::text,
            (select jsonb_agg(jsonb_build_object('account', a.code, 'debit', l.credit_paise, 'credit', l.debit_paise,
                     'partyType', l.party_type, 'partyId', l.party_id, 'memo', l.memo))
               from public.journal_lines l join public.accounts a on a.id = l.account_id where l.entry_id = e.id));
  update public.journal_entries set status = 'REVERSED', reversed_by_id = v_id where id = e.id;
  return v_id;
end $$;
