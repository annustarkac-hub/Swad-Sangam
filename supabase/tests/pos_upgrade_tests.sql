-- POS upgrade tests: KDS, split/merge/transfer, loyalty, table code, service requests. Runs after erp_tests.sql.
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
reset role;
update public.restaurant_settings set loyalty_enabled = true, loyalty_points_per_100 = 10, loyalty_point_value_paise = 100,
  prices_include_tax = true, tax_rate_bps = 0, packaging_fee_paise = 0;
insert into public.restaurant_tables(name, seats) values ('T2', 4) on conflict do nothing;

set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-0000000000e1';
set request.jwt.claims = '{"email":"cashier@test.in"}';
create temp table b as select public.pos_create_order('{"idempotencyKey":"88888888-8888-8888-8888-888888888888","orderType":"DINE_IN","tableNumber":"T1","items":[{"itemId":"dish_004","quantity":2},{"itemId":"dish_002","quantity":1},{"itemId":"dish_009","quantity":1}]}') as o;
create temp table b2 as select public.pos_create_order('{"idempotencyKey":"99999999-9999-9999-9999-999999999999","orderType":"DINE_IN","tableNumber":"T2","items":[{"itemId":"dish_001","quantity":1}]}') as o;
grant select on b, b2 to public;

-- stations snapshot on items
reset role;
select pg_temp.check((select count(distinct station_id) from public.order_items where order_id = (select (o->>'id')::uuid from b)) = 2, 'items routed to 2 kitchen stations');
set role authenticated;

-- split: move momo line to a new bill
create temp table sp as select public.pos_split_bill((select (o->>'id')::uuid from b),
  array(select id from public.order_items where order_id = (select (o->>'id')::uuid from b) and item_id = 'dish_002')) as r;
grant select on sp to public;
select pg_temp.check((select (r->'newBill'->>'totalPaise')::int from sp) = 5000, 'split bill total 50');
select pg_temp.check((select (r->'original'->>'totalPaise')::int from sp) = 12000 + 16000, 'original bill reduced');
do $$ begin
  perform public.pos_split_bill((select (o->>'id')::uuid from b), array(select id from public.order_items where order_id = (select (o->>'id')::uuid from b)));
  raise exception 'moved all';
exception when sqlstate '22023' then null; end $$;
select pg_temp.check(true, 'cannot split away every item');

-- merge bill from T2 into the original, then transfer table
select public.pos_merge_bills((select (o->>'id')::uuid from b), (select (o->>'id')::uuid from b2));
select pg_temp.check((select total_paise from public.orders where id = (select (o->>'id')::uuid from b)) = 28000 + 5500, 'merged total');
select pg_temp.check((select order_status from public.orders where id = (select (o->>'id')::uuid from b2)) = 'CANCELLED', 'source bill closed');
select pg_temp.check((select total_paise from public.orders where id = (select (o->>'id')::uuid from b2)) = 0, 'source bill zeroed');
select public.pos_transfer_table((select (o->>'id')::uuid from b), 'T2');
select pg_temp.check((select table_number from public.orders where id = (select (o->>'id')::uuid from b)) = 'T2', 'table transferred');

-- KDS: kitchen marks items, order follows
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
select pg_temp.check(jsonb_array_length(public.kds_board(null)) >= 2, 'KDS board lists bills');
select pg_temp.check(not (public.kds_board(null)->0 ? 'customerPhone'), 'KDS hides phone');
reset role;
create temp table it as select id from public.order_items where order_id = (select (o->>'id')::uuid from b);
grant select on it to public;
set role authenticated;
select public.kds_set_item_status((select min(id) from it), 'PREPARING');
reset role;
select pg_temp.check((select order_status from public.orders where id = (select (o->>'id')::uuid from b)) = 'PREPARING', 'first item started → order PREPARING');
set role authenticated;
select public.kds_set_item_status(id, 'READY') from it;
reset role;
select pg_temp.check((select order_status from public.orders where id = (select (o->>'id')::uuid from b)) = 'READY', 'all items ready → order READY');

-- loyalty: attach customer, earn on completion, redeem on next bill
set request.jwt.claim.sub = '00000000-0000-0000-0000-0000000000e1';
update public.customers set credit_limit_paise = 1000000 where id = 1;
set role authenticated;
select public.pos_set_customer((select (o->>'id')::uuid from b), 1);
select public.record_order_payment((select (o->>'id')::uuid from b), '[{"tender":"CASH","amountPaise":33500}]', gen_random_uuid());
select public.pos_settle((select (o->>'id')::uuid from b));
select pg_temp.check((select loyalty_points from public.customers where id = 1) = 30, 'earned 10 pts per ₹100 (₹335 → 30)');
create temp table b3 as select public.pos_create_order('{"idempotencyKey":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","items":[{"itemId":"dish_005","quantity":1}],"customerId":1}') as o;
grant select on b3 to public;
select public.pos_set_customer((select (o->>'id')::uuid from b3), 1);
select public.pos_redeem_points((select (o->>'id')::uuid from b3), 20);
select pg_temp.check((select total_paise from public.orders where id = (select (o->>'id')::uuid from b3)) = 5000, 'redeemed ₹20 on ₹70 bill');
select pg_temp.check((select loyalty_points from public.customers where id = 1) = 10, 'points deducted');
do $$ begin
  perform public.pos_redeem_points((select (o->>'id')::uuid from b3), 5);
  raise exception 'double redeem';
exception when sqlstate '22023' then null; end $$;
select pg_temp.check(true, 'one redemption per bill');

-- manager discount on an open bill (regression: total must stay consistent)
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
create temp table b4 as select public.pos_create_order('{"idempotencyKey":"dddddddd-dddd-dddd-dddd-dddddddddddd","items":[{"itemId":"dish_006","quantity":1}]}') as o;
grant select on b4 to public;
select public.pos_set_discount((select (o->>'id')::uuid from b4), 2000, 'Regular customer');
select pg_temp.check((select total_paise from public.orders where id = (select (o->>'id')::uuid from b4)) = 17000, 'manager discount applied');
select public.pos_set_discount((select (o->>'id')::uuid from b4), 0, 'Removed');
select pg_temp.check((select total_paise from public.orders where id = (select (o->>'id')::uuid from b4)) = 19000, 'discount removed');

-- QR table code + service request (customer, anon)
reset role;
update public.restaurant_settings set require_table_code = true, dine_in_enabled = true, accepting_orders = true;
set role anon;
select pg_temp.check((public.place_order('{"idempotencyKey":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","orderType":"DINE_IN","tableNumber":"T1","paymentMethod":"CASH","customerName":"Neha","customerPhone":"9812345678","termsAccepted":true,"items":[{"itemId":"dish_004","quantity":1}]}')->>'error') = 'TABLE_CODE_REQUIRED', 'QR order needs table code');
select pg_temp.check((public.request_service('T1','CALL_WAITER','0000')->>'error') = 'TABLE_CODE_REQUIRED', 'call waiter needs code');
reset role;
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-0000000000e1';
create temp table code as select public.open_table_session((select id from public.restaurant_tables where name = 'T1')) as c;
grant select on code to public;
set role anon;
select pg_temp.check((public.place_order(('{"idempotencyKey":"cccccccc-cccc-cccc-cccc-cccccccccccc","orderType":"DINE_IN","tableNumber":"T1","tableCode":"' || (select c from code) || '","paymentMethod":"CASH","customerName":"Neha","customerPhone":"9812345678","termsAccepted":true,"items":[{"itemId":"dish_004","quantity":1}]}')::jsonb)->>'ok') = 'true', 'QR order with correct code');
select public.request_service('T1','REQUEST_BILL',(select c from code));
select public.request_service('T1','REQUEST_BILL',(select c from code));
reset role;
select pg_temp.check((select count(*) from public.service_requests where status = 'OPEN') = 1, 'duplicate request ignored');
select pg_temp.check(not exists (select 1 from public.journal_lines group by entry_id having sum(debit_paise) <> sum(credit_paise)), 'books still balanced');
\echo ALL POS UPGRADE TESTS PASSED
