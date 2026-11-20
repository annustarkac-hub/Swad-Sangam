-- ERP tests: double-entry accounting, POS, payments, stock, purchases, khata, expenses, cash register, payroll.
-- Runs after backend_tests.sql (reuses its users: owner a, kitchen b, stranger c, staff d).
\set ON_ERROR_STOP 1
\pset tuples_only on
\pset format unaligned
set client_min_messages = warning;
create or replace function pg_temp.check(p_ok boolean, p_name text) returns void language plpgsql as $$
begin
  if not coalesce(p_ok, false) then raise exception 'TEST FAILED: %', p_name; end if;
  raise notice 'ok - %', p_name;
end $$;
set client_min_messages = notice;

-- every journal entry so far must balance
create or replace function pg_temp.books_balanced() returns boolean language sql as $$
  select not exists (select 1 from public.journal_lines group by entry_id having sum(debit_paise) <> sum(credit_paise))
     and (select coalesce(sum(debit_paise),0) = coalesce(sum(credit_paise),0) from public.journal_lines)
$$;

reset role;
select pg_temp.check((select count(*) from public.journal_entries where source = 'SALE') = 1, 'earlier completed app order posted a sale');
select pg_temp.check((select count(*) from public.journal_entries where source = 'PAYMENT') = 1, 'marking app order paid posted a payment');
select pg_temp.check((select count(*) from public.journal_entries where source = 'REFUND') = 1, 'refund adjustment posted');
select pg_temp.check(pg_temp.books_balanced(), 'books balanced after app order flow');

insert into auth.users(id, email) values ('00000000-0000-0000-0000-0000000000e1', 'cashier@test.in'),
                                          ('00000000-0000-0000-0000-0000000000e2', 'accounts@test.in'),
                                          ('00000000-0000-0000-0000-0000000000e3', 'waiter@test.in');
update public.staff set role = 'CASHIER', is_active = true where email = 'cashier@test.in';
update public.staff set role = 'ACCOUNTANT', is_active = true where email = 'accounts@test.in';
update public.staff set role = 'WAITER', is_active = true where email = 'waiter@test.in';
update public.restaurant_settings set prices_include_tax = true, tax_rate_bps = 500, packaging_fee_paise = 0, gst_registered = true;

-- ---------- owner sets up stock, recipe, supplier, customer, employee ----------
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
set request.jwt.claims = '{"email":"owner@test.in"}';
insert into public.stock_items(name, unit, min_level) values ('Atta', 'kg', 5), ('Chicken', 'kg', 2);
insert into public.recipe_lines(menu_item_id, stock_item_id, qty, unit)
  select 'dish_005', id, 200, 'g' from public.stock_items where name = 'Atta';
insert into public.suppliers(name, phone) values ('Negi Traders', '9000000001');
insert into public.customers(name, phone, credit_limit_paise) values ('NIT Hostel Mess', '9000000002', 100000);
insert into public.employees(name, designation, monthly_salary_paise) values ('Ramesh', 'Cook', 1500000);
insert into public.restaurant_tables(name, seats) values ('T1', 4);

select public.stock_adjust(jsonb_build_object('type','OPENING','itemId',(select id from public.stock_items where name='Atta'),'qty',10,'unitCostPaise',4000));
select pg_temp.check((select qty from public.stock_items where name='Atta') = 10, 'opening stock');
select public.record_purchase(jsonb_build_object('supplierId',(select id from public.suppliers limit 1),'invoiceNo','NT-1',
  'lines', jsonb_build_array(jsonb_build_object('stockItemId',(select id from public.stock_items where name='Atta'),'qty',10000,'unit','g','ratePaise',5))));
select pg_temp.check((select qty from public.stock_items where name='Atta') = 20, 'purchase in grams converted to kg');
select pg_temp.check((select round(avg_cost) from public.stock_items where name='Atta') = 4500, 'weighted average cost (40+50)/2 per kg');
select pg_temp.check((public.party_balances('SUPPLIER')->0->>'balancePaise')::bigint = 50000, 'supplier payable 500');
select public.record_supplier_payment(jsonb_build_object('supplierId',(select id from public.suppliers limit 1),'amountPaise',20000,'tender','CASH'));
select pg_temp.check((public.party_balances('SUPPLIER')->0->>'balancePaise')::bigint = 30000, 'supplier partial payment');

-- ---------- cash register + POS by cashier ----------
set request.jwt.claim.sub = '00000000-0000-0000-0000-0000000000e1';
set request.jwt.claims = '{"email":"cashier@test.in"}';
select public.open_cash_session(100000);
do $$ begin
  perform public.pos_create_order('{"idempotencyKey":"55555555-5555-5555-5555-555555555555","items":[{"itemId":"dish_005","quantity":1}],"discountPaise":500}');
  raise exception 'cashier gave discount';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'cashier cannot give discount without manager');
create temp table pos as select public.pos_create_order('{"idempotencyKey":"66666666-6666-6666-6666-666666666666","orderType":"DINE_IN","tableNumber":"T1","items":[{"itemId":"dish_005","quantity":2,"pricePaise":1}]}') as o;
grant select on pos to public;
select pg_temp.check((select (o->>'totalPaise')::int from pos) = 14000, 'POS bill priced on server (2 x 70)');
select pg_temp.check((select o->>'orderStatus' from pos) = 'ACCEPTED', 'POS bill goes straight to kitchen');
select pg_temp.check((select status from public.restaurant_tables where name='T1') = 'OCCUPIED', 'table occupied');
select public.pos_add_items((select (o->>'id')::uuid from pos), '[{"itemId":"dish_001","quantity":1}]');
select pg_temp.check((select total_paise from public.orders where id = (select (o->>'id')::uuid from pos)) = 19500, 'items added to open bill (dish_001 now 55)');
do $$ begin
  perform public.pos_settle((select (o->>'id')::uuid from pos));
  raise exception 'settled unpaid bill';
exception when sqlstate '22023' then null; end $$;
select pg_temp.check(true, 'cannot close an unpaid bill');
do $$ begin
  perform public.record_order_payment((select (o->>'id')::uuid from pos), '[{"tender":"CASH","amountPaise":20000}]', gen_random_uuid());
  raise exception 'overpayment accepted';
exception when sqlstate '22023' then null; end $$;
select pg_temp.check(true, 'overpayment rejected');
select public.record_order_payment((select (o->>'id')::uuid from pos),
  '[{"tender":"CASH","amountPaise":9500},{"tender":"UPI","amountPaise":5000,"reference":"UPI123"},{"tender":"CREDIT","amountPaise":5000,"customerId":1}]',
  '77777777-7777-7777-7777-777777777777');
select public.record_order_payment((select (o->>'id')::uuid from pos), '[{"tender":"CASH","amountPaise":9000}]', '77777777-7777-7777-7777-777777777777');
select pg_temp.check((select count(*) from public.order_payments where order_id = (select (o->>'id')::uuid from pos)) = 3, 'split payment recorded once (idempotent)');
select pg_temp.check((select payment_status from public.orders where id = (select (o->>'id')::uuid from pos)) = 'PAID', 'bill fully paid');
do $$ begin
  perform public.pos_add_items((select (o->>'id')::uuid from pos), '[{"itemId":"dish_001","quantity":1}]');
  raise exception 'edited paid bill';
exception when sqlstate '22023' then null; end $$;
select pg_temp.check(true, 'paid bill cannot be edited');
select public.pos_settle((select (o->>'id')::uuid from pos));
select pg_temp.check((select order_status from public.orders where id = (select (o->>'id')::uuid from pos)) = 'COMPLETED', 'POS bill settled');
select pg_temp.check((select qty from public.stock_items where name='Atta') = 19.6, 'recipe stock deducted (2 x 200 g)');
select public.get_receipt((select (o->>'id')::uuid from pos));
select pg_temp.check((public.get_receipt((select (o->>'id')::uuid from pos))->>'printNumber')::int = 2, 'reprint counted as duplicate');

-- waiter cannot take payments, staff cannot read ledgers
set request.jwt.claim.sub = '00000000-0000-0000-0000-0000000000e3';
do $$ begin
  perform public.record_order_payment((select (o->>'id')::uuid from pos), '[{"tender":"CASH","amountPaise":1}]', gen_random_uuid());
  raise exception 'waiter took payment';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'waiter cannot take payments');
select pg_temp.check((select count(*) from public.journal_lines) = 0, 'waiter cannot read the ledger');

-- ---------- accountant: books ----------
set request.jwt.claim.sub = '00000000-0000-0000-0000-0000000000e2';
set request.jwt.claims = '{"email":"accounts@test.in"}';
reset role;
select pg_temp.check(pg_temp.books_balanced(), 'every entry balanced after POS');
select pg_temp.check((select count(*) from public.stock_movements where type='CONSUMPTION') = 1, 'consumption posted once');
select pg_temp.check((select credit_paise from public.journal_lines l join public.journal_entries e on e.id=l.entry_id
   where e.source='SALE' and e.source_ref = (select o->>'id' from pos) and l.account_id = private.acct('4000')) = 18571,
   'GST separated from inclusive price (195/1.05)');
set role authenticated;
select pg_temp.check((public.trial_balance(current_date)->>'totalDebitPaise') = (public.trial_balance(current_date)->>'totalCreditPaise'), 'trial balance totals equal');
select pg_temp.check((public.balance_sheet(current_date)->>'balanced')::boolean, 'balance sheet balances');
select pg_temp.check((public.party_balances('CUSTOMER')->0->>'balancePaise')::bigint = 5000, 'customer khata outstanding 50');
select public.record_customer_receipt('{"customerId":1,"amountPaise":3000,"tender":"UPI"}');
select pg_temp.check((public.party_balances('CUSTOMER')->0->>'balancePaise')::bigint = 2000, 'khata payment collected');
select pg_temp.check((public.account_ledger('1100', current_date - 1, current_date, 'CUSTOMER', 1)->>'closingPaise')::bigint = 2000, 'customer statement closing balance');
select public.record_expense('{"accountCode":"5400","amountPaise":250000,"tender":"BANK","vendorName":"UPCL"}');
select pg_temp.check((select status from public.expenses order by id desc limit 1) = 'APPROVED', 'accountant expense auto-approved');
do $$ begin
  perform public.create_manual_journal('{"narration":"bad entry","lines":[{"accountCode":"1000","debitPaise":100},{"accountCode":"3000","creditPaise":90}]}');
  raise exception 'unbalanced accepted';
exception when sqlstate '22023' then null; end $$;
select pg_temp.check(true, 'unbalanced manual journal rejected');
select public.create_manual_journal('{"narration":"Owner capital introduced","lines":[{"accountCode":"1010","debitPaise":1000000},{"accountCode":"3000","creditPaise":1000000}]}');
select public.reverse_journal((select id from public.journal_entries where source='MANUAL' limit 1), 'Entered twice');
select pg_temp.check((select status from public.journal_entries where source='MANUAL' limit 1) = 'REVERSED', 'reversal marks original');
reset role;
do $$ begin
  update public.journal_lines set debit_paise = 1 where id = (select min(id) from public.journal_lines);
  raise exception 'ledger edited';
exception when others then if sqlerrm = 'ledger edited' then raise; end if; end $$;
select pg_temp.check(true, 'posted journal lines are immutable');
set role authenticated;
select pg_temp.check((public.profit_and_loss(current_date - 1, current_date)->>'netProfitPaise') is not null, 'P&L runs');
select pg_temp.check(jsonb_array_length(public.gst_report(current_date - 1, current_date)->'outward') >= 1, 'GST report has outward supplies');
select pg_temp.check((public.cash_flow(current_date - 1, current_date)->'closing'->>'1000') is not null, 'cash flow runs');
select pg_temp.check(jsonb_array_length(public.menu_costing()) > 0, 'menu costing');
select pg_temp.check((public.business_insights(current_date - 1, current_date)->>'salesPaise')::bigint > 0, 'business insights');

-- ---------- cash register close ----------
set request.jwt.claim.sub = '00000000-0000-0000-0000-0000000000e1';
select pg_temp.check((public.close_cash_session(100000 + 9500 - 1000)->>'differencePaise')::bigint = -1000, 'cash shortage of 10 detected');
reset role;
select pg_temp.check((select count(*) from public.journal_entries where source = 'CASH_SESSION') = 1, 'shortage posted');

-- ---------- stock wastage + count ----------
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select public.stock_adjust(jsonb_build_object('type','WASTAGE','itemId',(select id from public.stock_items where name='Atta'),'qty',0.6));
select public.stock_adjust(jsonb_build_object('type','COUNT','itemId',(select id from public.stock_items where name='Atta'),'countedQty',18.5));
select pg_temp.check((select qty from public.stock_items where name='Atta') = 18.5, 'wastage and physical count applied');

-- ---------- payroll ----------
select public.mark_attendance((select id from public.employees limit 1), date_trunc('month', current_date)::date, 'ABSENT');
select public.give_salary_advance((select id from public.employees limit 1), 200000, 'CASH', 'Festival');
create temp table pv as select public.payroll_preview(current_date) as p;
grant select on pv to public;
select pg_temp.check((select (p->0->>'advanceRecoveredPaise')::bigint from pv) = 200000, 'advance recovered from salary');
select public.run_payroll(current_date, 'BANK');
select pg_temp.check((select count(*) from public.payroll_lines) = 1, 'payroll posted');
select pg_temp.check((public.run_payroll(current_date, 'BANK')->>'employeesPaid')::int = 0, 'payroll not paid twice');
select pg_temp.check((public.party_balances('EMPLOYEE') = '[]'::jsonb), 'advance cleared');
select pg_temp.check((select (public.erp_overview()->>'todayOrders')::int) >= 1, 'owner overview');
reset role;
select pg_temp.check(pg_temp.books_balanced(), 'all books balanced at end');
\echo ALL ERP TESTS PASSED
