-- =====================================================================
-- Swad Sangam ERP — financial reports, business insights, security
-- =====================================================================

-- ---------------------------------------------------------------------
-- Financial statements (from the journal — the single source of truth)
-- ---------------------------------------------------------------------
create or replace function public.trial_balance(p_as_of date) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  return (select jsonb_build_object(
    'asOf', p_as_of,
    'rows', coalesce(jsonb_agg(jsonb_build_object('code', code, 'name', name, 'type', type,
               'debitPaise', greatest(bal, 0), 'creditPaise', greatest(-bal, 0)) order by code) filter (where bal <> 0), '[]'::jsonb),
    'totalDebitPaise', coalesce(sum(greatest(bal,0)),0), 'totalCreditPaise', coalesce(sum(greatest(-bal,0)),0))
    from (select a.code, a.name, a.type, coalesce(sum(l.debit_paise - l.credit_paise), 0) bal
            from public.accounts a
            left join public.journal_lines l on l.account_id = a.id
            left join public.journal_entries e on e.id = l.entry_id
           where e.id is null or e.entry_date <= p_as_of
           group by a.code, a.name, a.type) t);
end $$;

create or replace function public.profit_and_loss(p_from date, p_to date) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_income jsonb; v_exp jsonb; ti bigint; te bigint; v_cogs bigint;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  with b as (
    select a.code, a.name, a.type, sum(l.credit_paise - l.debit_paise) cr
      from public.journal_lines l join public.journal_entries e on e.id = l.entry_id
      join public.accounts a on a.id = l.account_id
     where e.entry_date between p_from and p_to and a.type in ('INCOME','EXPENSE')
     group by a.code, a.name, a.type)
  select coalesce(jsonb_agg(jsonb_build_object('code',code,'name',name,'amountPaise',cr) order by code) filter (where type='INCOME' and cr <> 0), '[]'),
         coalesce(jsonb_agg(jsonb_build_object('code',code,'name',name,'amountPaise',-cr) order by code) filter (where type='EXPENSE' and cr <> 0), '[]'),
         coalesce(sum(cr) filter (where type='INCOME'),0), coalesce(sum(-cr) filter (where type='EXPENSE'),0),
         coalesce(sum(-cr) filter (where code = '5000'),0)
    into v_income, v_exp, ti, te, v_cogs from b;
  return jsonb_build_object('from', p_from, 'to', p_to, 'income', v_income, 'expenses', v_exp,
    'totalIncomePaise', ti, 'totalExpensePaise', te, 'grossProfitPaise', ti - v_cogs, 'netProfitPaise', ti - te);
end $$;

create or replace function public.balance_sheet(p_as_of date) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v jsonb; v_earn bigint;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  select coalesce(sum(l.credit_paise - l.debit_paise),0) into v_earn
    from public.journal_lines l join public.journal_entries e on e.id = l.entry_id join public.accounts a on a.id = l.account_id
   where e.entry_date <= p_as_of and a.type in ('INCOME','EXPENSE');
  with b as (
    select a.code, a.name, a.type, sum(l.debit_paise - l.credit_paise) dr
      from public.journal_lines l join public.journal_entries e on e.id = l.entry_id join public.accounts a on a.id = l.account_id
     where e.entry_date <= p_as_of and a.type in ('ASSET','LIABILITY','EQUITY')
     group by a.code, a.name, a.type having sum(l.debit_paise - l.credit_paise) <> 0)
  select jsonb_build_object('asOf', p_as_of,
    'assets', coalesce((select jsonb_agg(jsonb_build_object('code',code,'name',name,'amountPaise',dr) order by code) from b where type='ASSET'),'[]'),
    'liabilities', coalesce((select jsonb_agg(jsonb_build_object('code',code,'name',name,'amountPaise',-dr) order by code) from b where type='LIABILITY'),'[]'),
    'equity', coalesce((select jsonb_agg(jsonb_build_object('code',code,'name',name,'amountPaise',-dr) order by code) from b where type='EQUITY'),'[]')
              || jsonb_build_array(jsonb_build_object('code','—','name','Profit / (loss) to date','amountPaise', v_earn)),
    'totalAssetsPaise', coalesce((select sum(dr) from b where type='ASSET'),0),
    'totalLiabilitiesPaise', coalesce((select -sum(dr) from b where type='LIABILITY'),0),
    'totalEquityPaise', coalesce((select -sum(dr) from b where type='EQUITY'),0) + v_earn) into v;
  return v || jsonb_build_object('balanced', (v->>'totalAssetsPaise')::bigint = (v->>'totalLiabilitiesPaise')::bigint + (v->>'totalEquityPaise')::bigint);
end $$;

/** Account / party ledger with opening balance and running balance (debit positive). */
create or replace function public.account_ledger(p_code text, p_from date, p_to date,
    p_party_type public.party_type default null, p_party_id bigint default null) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_acc int := private.acct(p_code); v_open bigint;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  select coalesce(sum(l.debit_paise - l.credit_paise),0) into v_open
    from public.journal_lines l join public.journal_entries e on e.id = l.entry_id
   where l.account_id = v_acc and e.entry_date < p_from
     and (p_party_type is null or (l.party_type = p_party_type and l.party_id = p_party_id));
  return jsonb_build_object('account', (select to_jsonb(a) from public.accounts a where a.id = v_acc),
    'openingPaise', v_open,
    'lines', coalesce((select jsonb_agg(jsonb_build_object('date', entry_date, 'entryNo', entry_no, 'narration', narration,
                 'source', source, 'debitPaise', debit_paise, 'creditPaise', credit_paise, 'memo', memo,
                 'balancePaise', v_open + run) order by entry_date, lid)
               from (select e.entry_date, e.entry_no, e.narration, e.source, l.debit_paise, l.credit_paise, l.memo, l.id lid,
                            sum(l.debit_paise - l.credit_paise) over (order by e.entry_date, l.id) run
                       from public.journal_lines l join public.journal_entries e on e.id = l.entry_id
                      where l.account_id = v_acc and e.entry_date between p_from and p_to
                        and (p_party_type is null or (l.party_type = p_party_type and l.party_id = p_party_id))) x), '[]'::jsonb),
    'closingPaise', v_open + coalesce((select sum(l.debit_paise - l.credit_paise) from public.journal_lines l
                      join public.journal_entries e on e.id = l.entry_id
                     where l.account_id = v_acc and e.entry_date between p_from and p_to
                       and (p_party_type is null or (l.party_type = p_party_type and l.party_id = p_party_id))), 0));
end $$;

/**
 * Outstanding balances with FIFO ageing (0–30, 31–60, 61–90, 90+ days).
 * CUSTOMER → receivables (1100), SUPPLIER → payables (2000), EMPLOYEE → advances (1400).
 */
create or replace function public.party_balances(p_type public.party_type) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_acc int; v_sign int;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  v_acc := private.acct(case p_type when 'CUSTOMER' then '1100' when 'SUPPLIER' then '2000' else '1400' end);
  v_sign := case when p_type = 'SUPPLIER' then -1 else 1 end;   -- payables are credit balances
  return coalesce((
    with mv as (
      select l.party_id, e.entry_date, l.id, (l.debit_paise - l.credit_paise) * v_sign amt
        from public.journal_lines l join public.journal_entries e on e.id = l.entry_id
       where l.account_id = v_acc and l.party_type = p_type),
    inc as (select party_id, entry_date, id, amt, sum(amt) over (partition by party_id order by entry_date, id) cum
              from mv where amt > 0),
    dec as (select party_id, -sum(amt) paid from mv where amt < 0 group by party_id),
    open as (select i.party_id, i.entry_date, least(i.amt, i.cum - coalesce(d.paid,0)) due
               from inc i left join dec d on d.party_id = i.party_id where i.cum > coalesce(d.paid,0)),
    tot as (select party_id, sum(amt) bal, max(entry_date) last_date from mv group by party_id)
    select jsonb_agg(jsonb_build_object(
      'partyId', t.party_id,
      'name', case p_type when 'CUSTOMER' then (select name from public.customers where id = t.party_id)
                          when 'SUPPLIER' then (select name from public.suppliers where id = t.party_id)
                          else (select name from public.employees where id = t.party_id) end,
      'balancePaise', t.bal, 'lastActivity', t.last_date,
      'd0_30', coalesce((select sum(due) from open o where o.party_id = t.party_id and current_date - o.entry_date <= 30),0),
      'd31_60', coalesce((select sum(due) from open o where o.party_id = t.party_id and current_date - o.entry_date between 31 and 60),0),
      'd61_90', coalesce((select sum(due) from open o where o.party_id = t.party_id and current_date - o.entry_date between 61 and 90),0),
      'd90plus', coalesce((select sum(due) from open o where o.party_id = t.party_id and current_date - o.entry_date > 90),0))
      order by t.bal desc)
      from tot t where t.bal <> 0), '[]'::jsonb);
end $$;

/** Direct-method cash flow for cash, bank and UPI accounts. */
create or replace function public.cash_flow(p_from date, p_to date) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  return jsonb_build_object('from', p_from, 'to', p_to,
    'opening', (select jsonb_object_agg(a.code, coalesce((select sum(l.debit_paise - l.credit_paise) from public.journal_lines l
                  join public.journal_entries e on e.id = l.entry_id where l.account_id = a.id and e.entry_date < p_from),0))
                from public.accounts a where a.code in ('1000','1010','1020')),
    'bySource', coalesce((select jsonb_agg(jsonb_build_object('source', source, 'inPaise', i, 'outPaise', o) order by source) from (
                  select e.source, sum(l.debit_paise) i, sum(l.credit_paise) o
                    from public.journal_lines l join public.journal_entries e on e.id = l.entry_id
                    join public.accounts a on a.id = l.account_id
                   where a.code in ('1000','1010','1020') and e.entry_date between p_from and p_to
                   group by e.source) x), '[]'::jsonb),
    'closing', (select jsonb_object_agg(a.code, coalesce((select sum(l.debit_paise - l.credit_paise) from public.journal_lines l
                  join public.journal_entries e on e.id = l.entry_id where l.account_id = a.id and e.entry_date <= p_to),0))
                from public.accounts a where a.code in ('1000','1010','1020')));
end $$;

/** GST summary (outward from completed orders, inward from purchases). Verify with your accountant. */
create or replace function public.gst_report(p_from date, p_to date) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_tz text;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  select timezone into v_tz from public.restaurant_settings where id = 1;
  return jsonb_build_object('from', p_from, 'to', p_to,
    'outward', coalesce((select jsonb_agg(jsonb_build_object('ratePercent', tax_rate_bps / 100.0, 'invoices', n,
                  'taxablePaise', taxable, 'cgstPaise', tax / 2, 'sgstPaise', tax - tax / 2, 'totalPaise', taxable + tax)) from (
                  select tax_rate_bps, count(*) n,
                         sum(subtotal_paise - discount_paise - tax_included_paise + packaging_fee_paise + delivery_fee_paise) taxable,
                         sum(tax_paise + tax_included_paise) tax
                    from public.orders
                   where order_status = 'COMPLETED' and (completed_at at time zone v_tz)::date between p_from and p_to
                   group by tax_rate_bps) x), '[]'::jsonb),
    'inward', jsonb_build_object(
      'invoices', (select count(*) from public.purchase_invoices where invoice_date between p_from and p_to),
      'taxablePaise', (select coalesce(sum(subtotal_paise),0) from public.purchase_invoices where invoice_date between p_from and p_to),
      'taxPaise', (select coalesce(sum(tax_paise),0) from public.purchase_invoices where invoice_date between p_from and p_to)),
    'outputTaxLedgerPaise', (select coalesce(sum(l.credit_paise - l.debit_paise),0) from public.journal_lines l
        join public.journal_entries e on e.id = l.entry_id join public.accounts a on a.id = l.account_id
       where a.code in ('2100','2101','2102') and e.entry_date between p_from and p_to),
    'inputTaxLedgerPaise', (select coalesce(sum(l.debit_paise - l.credit_paise),0) from public.journal_lines l
        join public.journal_entries e on e.id = l.entry_id join public.accounts a on a.id = l.account_id
       where a.code = '1300' and e.entry_date between p_from and p_to));
end $$;

/** Recipe cost, food-cost % and margin per dish (uses current average stock cost). */
create or replace function public.menu_costing() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT','KITCHEN');
  return coalesce((select jsonb_agg(jsonb_build_object('itemId', m.id, 'name', m.name, 'pricePaise', m.price_paise,
      'hasRecipe', c.cost is not null, 'recipeCostPaise', coalesce(c.cost,0),
      'foodCostPercent', case when c.cost is null or m.price_paise = 0 then null else round(100.0 * c.cost / m.price_paise, 1) end,
      'marginPaise', m.price_paise - coalesce(c.cost,0)) order by m.name)
    from public.menu_items m
    left join (select rl.menu_item_id, round(sum(private.convert_qty(rl.qty, rl.unit, si.unit) * si.avg_cost)) cost
                 from public.recipe_lines rl join public.stock_items si on si.id = rl.stock_item_id group by rl.menu_item_id) c
      on c.menu_item_id = m.id
   where not m.is_archived), '[]'::jsonb);
end $$;

/** Owner overview — operational estimates are labelled separately from accounting profit. */
create or replace function public.erp_overview() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_tz text; v_today date; v_start timestamptz; v_sales bigint; v_cogs bigint;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  select timezone into v_tz from public.restaurant_settings where id = 1;
  v_today := (now() at time zone v_tz)::date;
  v_start := v_today::timestamp at time zone v_tz;
  select coalesce(sum(subtotal_paise - discount_paise),0) into v_sales from public.orders
   where order_status = 'COMPLETED' and completed_at >= v_start;
  select coalesce(sum(l.debit_paise),0) into v_cogs from public.journal_lines l join public.journal_entries e on e.id = l.entry_id
   where e.source = 'COGS' and e.entry_date = v_today and l.account_id = private.acct('5000');
  return jsonb_build_object(
    'todaySalesPaise', v_sales,
    'todayOrders', (select count(*) from public.orders where created_at >= v_start and order_status not in ('REJECTED','CANCELLED')),
    'averageOrderValuePaise', (select coalesce(round(avg(total_paise)),0) from public.orders where order_status = 'COMPLETED' and completed_at >= v_start),
    'cashCollectedPaise', (select coalesce(sum(amount_paise),0) from public.order_payments where tender = 'CASH' and created_at >= v_start)
                          + (select coalesce(sum(amount_paise),0) from public.customer_receipts where tender = 'CASH' and created_at >= v_start),
    'upiCollectedPaise', (select coalesce(sum(amount_paise),0) from public.order_payments where tender = 'UPI' and created_at >= v_start)
                          + (select coalesce(sum(amount_paise),0) from public.customer_receipts where tender = 'UPI' and created_at >= v_start),
    'cardCollectedPaise', (select coalesce(sum(amount_paise),0) from public.order_payments where tender = 'CARD' and created_at >= v_start),
    'receivablesPaise', (select coalesce(sum(debit_paise - credit_paise),0) from public.journal_lines where account_id = private.acct('1100')),
    'payablesPaise', (select coalesce(sum(credit_paise - debit_paise),0) from public.journal_lines where account_id = private.acct('2000')),
    'todayExpensesPaise', (select coalesce(sum(amount_paise),0) from public.expenses where status = 'APPROVED' and expense_date = v_today),
    'estimatedGrossProfitPaise', v_sales - v_cogs,
    'foodCostPercent', case when v_sales > 0 then round(100.0 * v_cogs / v_sales, 1) end,
    'topItems', coalesce((select jsonb_agg(t) from (select i.name_snapshot name, sum(i.quantity) qty
                   from public.order_items i join public.orders o on o.id = i.order_id
                  where o.order_status = 'COMPLETED' and o.completed_at >= v_today - 6
                  group by 1 order by 2 desc limit 5) t), '[]'),
    'lowStock', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'name', name, 'qty', qty, 'unit', unit, 'minLevel', min_level))
                   from public.stock_items where is_active and qty <= min_level and min_level > 0), '[]'),
    'pendingKots', (select count(*) from public.orders where order_status in ('ACCEPTED','PREPARING')),
    'pendingOrders', (select count(*) from public.orders where order_status = 'PLACED'),
    'staffPresent', (select count(*) from public.attendance where day = v_today and status in ('PRESENT','HALF_DAY')),
    'staffAbsent', (select count(*) from public.attendance where day = v_today and status in ('ABSENT','LEAVE')),
    'pendingExpenses', (select count(*) from public.expenses where status = 'PENDING'),
    'recurringDue', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'name', name, 'amountPaise', amount_paise, 'day', day_of_month))
                   from public.recurring_expenses where is_active and extract(day from v_today) >= day_of_month
                    and (last_posted_month is null or last_posted_month < date_trunc('month', v_today)::date)), '[]'),
    'cashSession', (select to_jsonb(c) from public.cash_sessions c where closed_at is null),
    'note', 'Estimated gross profit = food sales today − recipe cost of dishes sold. See Accounting for actual profit.');
end $$;

/** Business intelligence: hourly sales, dish profitability, wastage, supplier rates, break-even. */
create or replace function public.business_insights(p_from date, p_to date) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_tz text; v_fixed bigint; v_sales bigint; v_cogs bigint;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  select timezone into v_tz from public.restaurant_settings where id = 1;
  select coalesce(sum(subtotal_paise - discount_paise),0) into v_sales from public.orders
   where order_status = 'COMPLETED' and (completed_at at time zone v_tz)::date between p_from and p_to;
  select coalesce(sum(l.debit_paise - l.credit_paise),0) into v_cogs from public.journal_lines l
    join public.journal_entries e on e.id = l.entry_id where l.account_id = private.acct('5000') and e.entry_date between p_from and p_to;
  select coalesce(sum(l.debit_paise - l.credit_paise),0) into v_fixed from public.journal_lines l
    join public.journal_entries e on e.id = l.entry_id join public.accounts a on a.id = l.account_id
   where a.type = 'EXPENSE' and a.code not in ('5000','5850','5860','5900','5910','5950') and e.entry_date between p_from and p_to;
  return jsonb_build_object(
    'hourly', coalesce((select jsonb_agg(jsonb_build_object('hour', h, 'orders', n, 'salesPaise', s) order by h) from (
        select extract(hour from completed_at at time zone v_tz)::int h, count(*) n, sum(subtotal_paise - discount_paise) s
          from public.orders where order_status = 'COMPLETED' and (completed_at at time zone v_tz)::date between p_from and p_to
         group by 1) x), '[]'),
    'dishes', coalesce((select jsonb_agg(jsonb_build_object('name', name, 'qty', qty, 'revenuePaise', rev,
                 'estimatedCostPaise', cost, 'estimatedProfitPaise', rev - cost) order by rev - cost desc) from (
        select i.name_snapshot name, sum(i.quantity) qty, sum(i.line_total_paise) rev,
               sum(i.quantity * coalesce((select (c->>'recipeCostPaise')::bigint from jsonb_array_elements(public.menu_costing()) c
                                          where c->>'itemId' = i.item_id), 0)) cost
          from public.order_items i join public.orders o on o.id = i.order_id
         where o.order_status = 'COMPLETED' and (o.completed_at at time zone v_tz)::date between p_from and p_to
         group by i.name_snapshot, i.item_id) d), '[]'),
    'wastage', coalesce((select jsonb_agg(jsonb_build_object('name', si.name, 'qty', -sum_q, 'unit', si.unit, 'valuePaise', -sum_v) order by sum_v) from (
        select item_id, sum(qty) sum_q, sum(value_paise) sum_v from public.stock_movements
         where type = 'WASTAGE' and (created_at at time zone v_tz)::date between p_from and p_to group by item_id) w
        join public.stock_items si on si.id = w.item_id), '[]'),
    'supplierRates', coalesce((select jsonb_agg(jsonb_build_object('item', si.name, 'unit', si.unit, 'supplier', s.name,
                 'avgRatePaise', round(r.avg_rate), 'lastPurchase', r.last_date) order by si.name, r.avg_rate) from (
        select pl.stock_item_id, pi.supplier_id, avg(pl.amount_paise / nullif(pl.qty,0)) avg_rate, max(pi.invoice_date) last_date
          from public.purchase_lines pl join public.purchase_invoices pi on pi.id = pl.invoice_id
         where pl.stock_item_id is not null group by 1, 2) r
        join public.stock_items si on si.id = r.stock_item_id join public.suppliers s on s.id = r.supplier_id), '[]'),
    'salesPaise', v_sales, 'foodCostPaise', v_cogs, 'operatingExpensesPaise', v_fixed,
    'foodCostPercent', case when v_sales > 0 then round(100.0 * v_cogs / v_sales, 1) end,
    'breakEvenSalesPaise', case when v_sales > v_cogs and v_sales > 0 then round(v_fixed::numeric / ((v_sales - v_cogs)::numeric / v_sales)) end,
    'note', 'Dish cost uses current recipe cost; break-even = operating expenses ÷ gross-margin ratio for the period. Estimates, not audited figures.');
end $$;

-- ---------------------------------------------------------------------
-- Row level security for ERP tables
-- ---------------------------------------------------------------------
do $$
declare t text;
begin
  foreach t in array array['accounts','journal_entries','journal_lines','stock_items','stock_movements','recipe_lines',
    'suppliers','purchase_invoices','purchase_lines','supplier_payments','customers','customer_receipts','expenses',
    'recurring_expenses','cash_sessions','order_payments','restaurant_tables','reservations','employees','attendance',
    'salary_advances','payroll_lines'] loop
    execute format('alter table public.%I enable row level security', t);
  end loop;
end $$;

-- Finance (read): owner, manager, accountant
create policy fin_read_accounts on public.accounts for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT','STAFF'));
create policy fin_write_accounts on public.accounts for all to authenticated
  using (private.has_role('OWNER','ACCOUNTANT')) with check (private.has_role('OWNER','ACCOUNTANT'));
create policy fin_read_je on public.journal_entries for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT'));
create policy fin_read_jl on public.journal_lines for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT'));
create policy fin_read_pi on public.purchase_invoices for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT'));
create policy fin_read_pl on public.purchase_lines for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT'));
create policy fin_read_sp on public.supplier_payments for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT'));
create policy fin_read_cr on public.customer_receipts for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT','STAFF'));
create policy fin_read_op on public.order_payments for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT','STAFF'));
create policy fin_read_cs on public.cash_sessions for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT','STAFF'));
create policy fin_read_exp on public.expenses for select to authenticated
  using (private.has_role('OWNER','MANAGER','ACCOUNTANT') or created_by = auth.uid());
create policy fin_all_rec on public.recurring_expenses for all to authenticated
  using (private.has_role('OWNER','MANAGER','ACCOUNTANT')) with check (private.has_role('OWNER','MANAGER','ACCOUNTANT'));

-- Masters editable by owner/manager (+accountant for parties)
create policy inv_read on public.stock_items for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT','STAFF','KITCHEN'));
create policy inv_write on public.stock_items for all to authenticated
  using (private.has_role('OWNER','MANAGER')) with check (private.has_role('OWNER','MANAGER'));
create policy inv_mv_read on public.stock_movements for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT','KITCHEN'));
create policy recipe_read on public.recipe_lines for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT','KITCHEN'));
create policy recipe_write on public.recipe_lines for all to authenticated
  using (private.has_role('OWNER','MANAGER')) with check (private.has_role('OWNER','MANAGER'));
create policy sup_read on public.suppliers for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT'));
create policy sup_write on public.suppliers for all to authenticated
  using (private.has_role('OWNER','MANAGER','ACCOUNTANT')) with check (private.has_role('OWNER','MANAGER','ACCOUNTANT'));
create policy cust_read on public.customers for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT','STAFF'));
create policy cust_write on public.customers for all to authenticated
  using (private.has_role('OWNER','MANAGER','ACCOUNTANT')) with check (private.has_role('OWNER','MANAGER','ACCOUNTANT'));
create policy tables_read on public.restaurant_tables for select to authenticated using (private.has_role('OWNER','MANAGER','STAFF','KITCHEN'));
create policy tables_write on public.restaurant_tables for all to authenticated
  using (private.has_role('OWNER','MANAGER','STAFF')) with check (private.has_role('OWNER','MANAGER','STAFF'));
create policy resv_all on public.reservations for all to authenticated
  using (private.has_role('OWNER','MANAGER','STAFF')) with check (private.has_role('OWNER','MANAGER','STAFF'));
create policy emp_read on public.employees for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT'));
create policy emp_write on public.employees for all to authenticated
  using (private.has_role('OWNER','ACCOUNTANT')) with check (private.has_role('OWNER','ACCOUNTANT'));
create policy att_read on public.attendance for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT'));
create policy adv_read on public.salary_advances for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT'));
create policy pay_read on public.payroll_lines for select to authenticated using (private.has_role('OWNER','ACCOUNTANT','MANAGER'));

-- Grants (RLS still filters rows). Ledger / money tables: read-only for API roles; writes via functions.
grant select on public.accounts, public.journal_entries, public.journal_lines, public.stock_items, public.stock_movements,
  public.recipe_lines, public.suppliers, public.purchase_invoices, public.purchase_lines, public.supplier_payments,
  public.customers, public.customer_receipts, public.expenses, public.recurring_expenses, public.cash_sessions,
  public.order_payments, public.restaurant_tables, public.reservations, public.employees, public.attendance,
  public.salary_advances, public.payroll_lines to authenticated;
grant insert, update, delete on public.accounts, public.stock_items, public.recipe_lines, public.suppliers, public.customers,
  public.recurring_expenses, public.restaurant_tables, public.reservations, public.employees to authenticated;
revoke insert, update, delete on public.journal_entries, public.journal_lines, public.stock_movements, public.purchase_invoices,
  public.purchase_lines, public.supplier_payments, public.customer_receipts, public.expenses, public.cash_sessions,
  public.order_payments, public.attendance, public.salary_advances, public.payroll_lines from anon, authenticated;
revoke all on public.accounts, public.journal_entries, public.journal_lines, public.stock_items, public.stock_movements,
  public.recipe_lines, public.suppliers, public.purchase_invoices, public.purchase_lines, public.supplier_payments,
  public.customers, public.customer_receipts, public.expenses, public.recurring_expenses, public.cash_sessions,
  public.order_payments, public.restaurant_tables, public.reservations, public.employees, public.attendance,
  public.salary_advances, public.payroll_lines from anon;
grant usage, select on all sequences in schema public to authenticated;

-- System accounts cannot be deactivated or renumbered
create or replace function private.protect_system_accounts() returns trigger
language plpgsql as $$
begin
  if tg_op = 'DELETE' then raise exception 'accounts cannot be deleted — deactivate instead'; end if;
  if old.is_system and (new.code <> old.code or new.type <> old.type or not new.is_active or not new.is_system) then
    raise exception 'system accounts cannot be renumbered, retyped or deactivated';
  end if;
  return new;
end $$;
create trigger trg_protect_accounts before update or delete on public.accounts for each row execute function private.protect_system_accounts();

-- Audit trail for ERP master data
create trigger audit_accounts  after insert or update on public.accounts for each row execute function private.audit_row();
create trigger audit_stock     after insert or update or delete on public.stock_items for each row execute function private.audit_row();
create trigger audit_recipes   after insert or update or delete on public.recipe_lines for each row execute function private.audit_row();
create trigger audit_suppliers after insert or update or delete on public.suppliers for each row execute function private.audit_row();
create trigger audit_customers after insert or update or delete on public.customers for each row execute function private.audit_row();
create trigger audit_employees after insert or update or delete on public.employees for each row execute function private.audit_row();
create trigger audit_expenses  after insert or update on public.expenses for each row execute function private.audit_row();
create trigger audit_cash      after insert or update on public.cash_sessions for each row execute function private.audit_row();
create trigger audit_payroll   after insert on public.payroll_lines for each row execute function private.audit_row();

-- Function privileges: everything new is for logged-in staff only (each checks its role)
revoke execute on all functions in schema private from public, anon, authenticated;
grant execute on function private.my_role(), private.has_role(public.staff_role[]),
  private.role_matches(public.staff_role, public.staff_role[]) to anon, authenticated;
do $$
declare f text;
begin
  foreach f in array array[
    'create_manual_journal(jsonb)','reverse_journal(bigint,text)','stock_adjust(jsonb)','record_supplier_payment(jsonb)',
    'record_purchase(jsonb)','record_customer_receipt(jsonb)','record_expense(jsonb)','decide_expense(bigint,boolean)',
    'record_order_payment(uuid,jsonb,uuid)','pos_create_order(jsonb)','pos_add_items(uuid,jsonb)',
    'pos_set_discount(uuid,bigint,text)','pos_settle(uuid)','get_receipt(uuid,boolean)','open_cash_session(bigint)',
    'cash_session_status()','close_cash_session(bigint,text)',
    'mark_attendance(bigint,date,text,timestamptz,timestamptz,numeric,text)',
    'give_salary_advance(bigint,bigint,public.tender,text)','payroll_preview(date,jsonb)','run_payroll(date,public.tender,jsonb)',
    'trial_balance(date)','profit_and_loss(date,date)','balance_sheet(date)',
    'account_ledger(text,date,date,public.party_type,bigint)','party_balances(public.party_type)','cash_flow(date,date)',
    'gst_report(date,date)','menu_costing()','erp_overview()','business_insights(date,date)'] loop
    execute format('revoke execute on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;

-- Private bucket for expense bills / receipts
do $$
begin
  if to_regclass('storage.buckets') is not null then
    insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
    values ('receipts', 'receipts', false, 5242880, array['image/webp','image/jpeg','image/png','application/pdf'])
    on conflict (id) do nothing;
    execute $p$create policy "receipts staff read" on storage.objects for select to authenticated
              using (bucket_id = 'receipts' and private.has_role('OWNER','MANAGER','ACCOUNTANT','STAFF'))$p$;
    execute $p$create policy "receipts staff upload" on storage.objects for insert to authenticated
              with check (bucket_id = 'receipts' and private.has_role('OWNER','MANAGER','ACCOUNTANT','STAFF'))$p$;
  end if;
end $$;
