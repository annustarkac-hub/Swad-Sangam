-- Backend business-rule and security tests (PRD §15).
-- Run after supabase_stub.sql + all migrations. Any failure aborts with an error.
\set ON_ERROR_STOP 1
set client_min_messages = warning;
\pset tuples_only on
\pset format unaligned

create or replace function pg_temp.check(p_ok boolean, p_name text) returns void language plpgsql as $$
begin
  if not coalesce(p_ok, false) then raise exception 'TEST FAILED: %', p_name; end if;
  raise notice 'ok - %', p_name;
end $$;
-- true when the query returns no rows OR the role has no privilege at all (both are secure)
create or replace function pg_temp.sees_nothing(p_sql text) returns boolean language plpgsql as $$
declare n bigint;
begin
  execute 'select count(*) from (' || p_sql || ') x' into n;
  return n = 0;
exception when insufficient_privilege then
  return true;
end $$;
set client_min_messages = notice;

-- ---------- fixtures: users ----------
insert into auth.users(id, email) values
  ('00000000-0000-0000-0000-00000000000a', 'owner@test.in'),
  ('00000000-0000-0000-0000-00000000000b', 'kitchen@test.in'),
  ('00000000-0000-0000-0000-00000000000c', 'stranger@test.in'),
  ('00000000-0000-0000-0000-00000000000d', 'staff@test.in');
select public.make_owner('owner@test.in');
update public.staff set role = 'KITCHEN', is_active = true where email = 'kitchen@test.in';
update public.staff set role = 'STAFF',   is_active = true where email = 'staff@test.in';
-- open 24h for deterministic tests
update public.restaurant_settings set opening_hours =
  '{"1":[{"open":"00:00","close":"00:00"}],"2":[{"open":"00:00","close":"00:00"}],"3":[{"open":"00:00","close":"00:00"}],
    "4":[{"open":"00:00","close":"00:00"}],"5":[{"open":"00:00","close":"00:00"}],"6":[{"open":"00:00","close":"00:00"}],
    "7":[{"open":"00:00","close":"00:00"}]}';

-- ---------- opening hours ----------
select pg_temp.check(private.is_open_at('{"1":[{"open":"06:00","close":"23:00"}]}', 'Asia/Kolkata', '2026-11-16 10:00+05:30'), 'open Monday 10:00');
select pg_temp.check(not private.is_open_at('{"1":[{"open":"06:00","close":"23:00"}]}', 'Asia/Kolkata', '2026-11-16 23:30+05:30'), 'closed Monday 23:30');
select pg_temp.check(private.is_open_at('{"1":[{"open":"18:00","close":"02:00"}]}', 'Asia/Kolkata', '2026-11-17 01:00+05:30'), 'overnight interval');

-- ---------- customer (anon) ----------
set role anon;
select pg_temp.check((select count(*) from public.menu_items) = 12, 'anon can read menu');
select pg_temp.check((select count(*) from public.categories) = 7, 'inactive categories hidden');
select pg_temp.check(pg_temp.sees_nothing('select * from public.orders'), 'anon cannot read orders');
do $$ begin
  perform count(*) from public.coupons;
  raise exception 'anon read coupons';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'anon cannot read coupons');

-- ordering is OFF by default (release gate)
select pg_temp.check((public.place_order('{"idempotencyKey":"11111111-1111-1111-1111-111111111111","orderType":"PICKUP","paymentMethod":"CASH","customerName":"Ravi","customerPhone":"9876543210","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":2}]}')->>'error') = 'ORDERS_CLOSED', 'orders closed by default');
reset role;
update public.restaurant_settings set accepting_orders = true, pickup_enabled = true;
insert into public.coupons(code, discount_type, value, min_order_paise) values ('STUDENT10', 'PERCENT', 10, 10000);
set role anon;

select pg_temp.check((public.place_order('{"idempotencyKey":"11111111-1111-1111-1111-111111111111","orderType":"DELIVERY","paymentMethod":"CASH","customerName":"Ravi","customerPhone":"9876543210","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":1}]}')->>'error') = 'ORDER_TYPE_UNAVAILABLE', 'delivery disabled');
select pg_temp.check((public.place_order('{"idempotencyKey":"11111111-1111-1111-1111-111111111111","orderType":"PICKUP","paymentMethod":"CASH","customerName":"Ravi","customerPhone":"12345","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":1}]}')->>'error') = 'INVALID_PHONE', 'invalid phone rejected');
select pg_temp.check((public.place_order('{"idempotencyKey":"11111111-1111-1111-1111-111111111111","orderType":"PICKUP","paymentMethod":"CASH","customerName":"Ravi","customerPhone":"9876543210","termsAccepted":true,"items":[]}')->>'error') = 'EMPTY_CART', 'empty cart rejected');
select pg_temp.check((public.place_order('{"idempotencyKey":"11111111-1111-1111-1111-111111111111","orderType":"PICKUP","paymentMethod":"ONLINE","customerName":"Ravi","customerPhone":"9876543210","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":1}]}')->>'error') = 'PAYMENT_METHOD_UNAVAILABLE', 'online payment disabled in MVP');
select pg_temp.check((public.place_order('{"idempotencyKey":"11111111-1111-1111-1111-111111111111","orderType":"PICKUP","paymentMethod":"CASH","customerName":"Ravi","customerPhone":"9876543210","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":1}],"expectedTotalPaise":100}')->>'error') = 'PRICE_CHANGED', 'stale client price detected');
select pg_temp.check((public.place_order('{"idempotencyKey":"11111111-1111-1111-1111-111111111111","orderType":"PICKUP","paymentMethod":"CASH","customerName":"Ravi","customerPhone":"9876543210","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":0}]}')->>'error') = 'INVALID_QUANTITY', 'zero quantity rejected');

-- client tries to send its own price: ignored, server price used
create temp table r as select public.place_order('{"idempotencyKey":"22222222-2222-2222-2222-222222222222","orderType":"PICKUP","paymentMethod":"CASH","customerName":"Ravi Kumar","customerPhone":"+91 98765 43210","termsAccepted":true,"sessionId":"s1","items":[{"itemId":"dish_001","quantity":2,"pricePaise":1},{"itemId":"dish_012","quantity":1,"optionIds":["with_noodles"]}]}') as res;
select pg_temp.check((select res->>'ok' from r) = 'true', 'order placed');
select pg_temp.check((select (res->'order'->>'subtotalPaise')::int from r) = 40000, 'server-side subtotal 2x50 + 300');
select pg_temp.check((select (res->'order'->>'totalPaise')::int from r) = 40000, 'total = subtotal (tax inclusive, no fees)');
select pg_temp.check((select res->'order'->>'orderNumber' from r) ~ '^SS-[0-9]{4}-0001$', 'order number format');
select pg_temp.check((select res->'order'->>'orderStatus' from r) = 'PLACED', 'status PLACED, not accepted');
select pg_temp.check((select res->'order'->'items'->1->'options'->0->>'name' from r) = 'With Noodles', 'option snapshot stored');

-- duplicate submission returns same order
select pg_temp.check((public.place_order('{"idempotencyKey":"22222222-2222-2222-2222-222222222222","orderType":"PICKUP","paymentMethod":"CASH","customerName":"Ravi Kumar","customerPhone":"9876543210","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":2}]}')->'order'->>'orderNumber') = (select res->'order'->>'orderNumber' from r), 'idempotent duplicate');

-- coupon + tax exclusive
reset role;
update public.restaurant_settings set prices_include_tax = false, tax_rate_bps = 500, packaging_fee_paise = 1000;
set role anon;
create temp table q as select public.quote_order('{"orderType":"PICKUP","couponCode":"student10","items":[{"itemId":"dish_006","quantity":1}]}') as res;
select pg_temp.check((select (res->>'discountPaise')::int from q) = 1900, 'coupon 10% of 190');
select pg_temp.check((select (res->>'taxPaise')::int from q) = 855, 'tax 5% of (190-19)');
select pg_temp.check((select (res->>'totalPaise')::int from q) = 19000 - 1900 + 855 + 1000, 'Total = Subtotal + Taxes + Fees - Discounts');
create temp table r2 as select public.place_order(('{"idempotencyKey":"33333333-3333-3333-3333-333333333333","orderType":"PICKUP","paymentMethod":"CASH","customerName":"Asha","customerPhone":"9123456789","termsAccepted":true,"couponCode":"STUDENT10","expectedTotalPaise":' || (select res->>'totalPaise' from q) || ',"items":[{"itemId":"dish_006","quantity":1}]}')::jsonb) as res;
select pg_temp.check((select res->>'ok' from r2) = 'true', 'order with coupon + expected total');
grant select on r, r2, q to public;

-- unavailable item
reset role;
update public.menu_items set is_available = false where id = 'dish_011';
set role anon;
select pg_temp.check((public.place_order('{"idempotencyKey":"44444444-4444-4444-4444-444444444444","orderType":"PICKUP","paymentMethod":"CASH","customerName":"Asha","customerPhone":"9123456789","termsAccepted":true,"items":[{"itemId":"dish_011","quantity":1}]}')->>'error') = 'ITEM_UNAVAILABLE', 'unavailable item rejected');

-- customer cannot modify data directly
do $$ begin
  begin
    update public.menu_items set price_paise = 1 where id = 'dish_001';
    if exists (select 1 from public.menu_items where price_paise = 1) then raise exception 'price changed'; end if;
  exception when insufficient_privilege then null; end;
  begin
    insert into public.orders(order_number) values ('x');
    raise exception 'anon inserted order';
  exception when insufficient_privilege then null; end;
end $$;
select pg_temp.check((select price_paise from public.menu_items where id = 'dish_001') = 5000, 'customer cannot alter prices');

-- token-based status lookup; wrong token reveals nothing
select pg_temp.check(public.get_order_by_token((select (res->'order'->>'accessToken')::uuid from r))->>'orderStatus' = 'PLACED', 'customer reads own order');
select pg_temp.check(public.get_order_by_token(gen_random_uuid()) is null, 'cannot read other orders');
select pg_temp.check(public.get_order_by_token((select (res->'order'->>'accessToken')::uuid from r)) ? 'customerPhone' = false, 'customer view hides phone');

-- anon cannot call staff RPCs
do $$ begin
  perform public.update_order_status((select (res->'order'->>'id')::uuid from r), 'ACCEPTED');
  raise exception 'anon changed status';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'anon blocked from admin RPC');

-- ---------- staff ----------
reset role;
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000c';
set request.jwt.claims = '{"email":"stranger@test.in"}';
select pg_temp.check(pg_temp.sees_nothing('select * from public.orders'), 'inactive signup sees no orders');
do $$ begin
  perform public.dashboard_summary();
  raise exception 'stranger read dashboard';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'inactive signup blocked from dashboard');

set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
set request.jwt.claims = '{"email":"kitchen@test.in"}';
do $$ begin
  perform public.update_order_status((select (res->'order'->>'id')::uuid from r), 'ACCEPTED');
  raise exception 'kitchen accepted order';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'kitchen cannot accept orders');
select pg_temp.check(pg_temp.sees_nothing('select * from public.orders'), 'kitchen cannot read customer orders table');

set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000d';
set request.jwt.claims = '{"email":"staff@test.in"}';
do $$ begin
  update public.restaurant_settings set accepting_orders = false;
  if (select accepting_orders from public.restaurant_settings) = false then raise exception 'staff changed settings'; end if;
end $$;
select pg_temp.check(true, 'staff cannot change owner-only settings');

do $$ begin
  perform public.update_order_status((select (res->'order'->>'id')::uuid from r), 'READY');
  raise exception 'invalid transition allowed';
exception when sqlstate '22023' then null; end $$;
select pg_temp.check(true, 'invalid transition PLACED->READY rejected');
do $$ begin
  perform public.update_order_status((select (res->'order'->>'id')::uuid from r), 'REJECTED');
  raise exception 'reject without reason allowed';
exception when sqlstate '22023' then null; end $$;
select pg_temp.check(true, 'reject requires reason');

select public.update_order_status((select (res->'order'->>'id')::uuid from r), 'ACCEPTED', null, 15);
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
select pg_temp.check(jsonb_array_length(public.kitchen_queue()) = 1, 'kitchen sees accepted order');
select pg_temp.check(not (public.kitchen_queue()->0 ? 'customerPhone'), 'kitchen queue hides phone');
select public.update_order_status((select (res->'order'->>'id')::uuid from r), 'PREPARING');
select public.update_order_status((select (res->'order'->>'id')::uuid from r), 'READY');
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000d';
select public.update_order_status((select (res->'order'->>'id')::uuid from r), 'COMPLETED');
select public.update_payment_status((select (res->'order'->>'id')::uuid from r), 'PAID');
select pg_temp.check((select order_status from public.orders where id = (select (res->'order'->>'id')::uuid from r)) = 'COMPLETED', 'full lifecycle completed');
select pg_temp.check((select count(*) from public.order_status_history where order_id = (select (res->'order'->>'id')::uuid from r)) = 5, 'status history recorded');

do $$ begin
  update public.orders set total_paise = 1;
  if exists (select 1 from public.orders where total_paise = 1) then raise exception 'staff altered price'; end if;
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'historical order prices cannot be edited');

-- customer cancel after accept is blocked by default
reset role;
set role anon;
select pg_temp.check((public.cancel_order_by_customer((select (res->'order'->>'accessToken')::uuid from r))->>'error') = 'CANNOT_CANCEL', 'cannot cancel completed order');
select pg_temp.check((public.cancel_order_by_customer((select (res->'order'->>'accessToken')::uuid from r2))->>'ok') = 'true', 'customer cancels PLACED order');

-- ---------- owner ----------
reset role;
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
set request.jwt.claims = '{"email":"owner@test.in"}';
update public.menu_items set price_paise = 5500 where id = 'dish_001';
select pg_temp.check((select price_paise from public.menu_items where id = 'dish_001') = 5500, 'owner edits price');
select pg_temp.check((select count(*) from public.audit_logs where entity = 'menu_items' and actor_email = 'owner@test.in') >= 1, 'price change audited');
select pg_temp.check(public.get_order_by_token((select (res->'order'->>'accessToken')::uuid from r))->'items'->0->>'unitPricePaise' = '5000', 'order keeps price snapshot');
select pg_temp.check((public.sales_report(current_date - 1, current_date + 1)->>'grossOrderValuePaise')::int = 40000, 'sales report gross');
select pg_temp.check((public.sales_report(current_date - 1, current_date + 1)->>'cancelledCount')::int = 1, 'sales report cancellations');
select public.record_order_adjustment((select (res->'order'->>'id')::uuid from r), 'REFUND', 5000, 'Item missing');
select pg_temp.check((public.sales_report(current_date - 1, current_date + 1)->>'netReceiptsPaise')::int = 35000, 'net receipts subtract refunds');
select pg_temp.check((public.dashboard_summary()->>'completed')::int = 1, 'dashboard summary');
do $$ begin
  perform public.set_staff_member('00000000-0000-0000-0000-00000000000a', 'STAFF', true);
  raise exception 'last owner demoted';
exception when others then
  if sqlerrm not like 'LAST_OWNER%' then raise; end if;
end $$;
select pg_temp.check(true, 'last owner cannot demote themself');
select public.submit_feedback('{"overall":5,"foodQuality":4,"comments":"Great thali"}');
select pg_temp.check((select count(*) from public.feedback) = 1, 'owner sees private feedback');
select public.log_events('[{"name":"app_opened","sessionId":"s1"},{"name":"bogus_event"}]');
select pg_temp.check((select count(*) from public.analytics_events where event_name = 'app_opened') = 1, 'analytics accepts whitelisted events only');
reset role;
select pg_temp.check(private.purge_old_personal_data() = 0, 'retention job runs');
\echo ALL BACKEND TESTS PASSED
