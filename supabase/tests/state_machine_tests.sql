-- Explicit state machines: payment, kitchen items, tables, reservations, delivery code, allowed actions.
-- Runs after delivery_tests.sql.
\set ON_ERROR_STOP 1
\pset tuples_only on
\pset format unaligned
set client_min_messages = warning;
create or replace function pg_temp.check(p_ok boolean, p_name text) returns void language plpgsql as $$
begin
  if not coalesce(p_ok, false) then raise exception 'TEST FAILED: %', p_name; end if;
  raise notice 'ok - %', p_name;
end $$;
create or replace function pg_temp.as_user(p_id text, p_email text) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', p_id, false);
  perform set_config('request.jwt.claims', json_build_object('email', p_email)::text, false);
end $$;
-- true when the statement fails with SQLSTATE 22023 (business rule)
create or replace function pg_temp.rejected(p_sql text) returns boolean language plpgsql as $$
begin
  execute p_sql;
  return false;
exception when sqlstate '22023' then return true;
end $$;
set client_min_messages = notice;
reset role;

-- ---------- pure transition tables ----------
select pg_temp.check(private.payment_transition_ok('PENDING','PAID') and private.payment_transition_ok('PAID','REFUNDED')
  and not private.payment_transition_ok('PAID','PENDING') and not private.payment_transition_ok('REFUNDED','PAID')
  and not private.payment_transition_ok('FAILED','REFUNDED'), 'payment transition table');
select pg_temp.check(not private.valid_transition('READY','COMPLETED','DELIVERY') and private.valid_transition('READY','COMPLETED','PICKUP')
  and private.valid_transition('OUT_FOR_DELIVERY','COMPLETED','DELIVERY'), 'deliveries must go out before completing');
select pg_temp.check(private.prep_transition_ok('READY','PREPARING') and not private.prep_transition_ok('SERVED','PENDING')
  and not private.prep_transition_ok('VOID','PENDING'), 'kitchen item transition table');
select pg_temp.check(not private.table_transition_ok('OCCUPIED','RESERVED') and private.table_transition_ok('CLEANING','FREE'), 'table transition table');
select pg_temp.check(not private.reservation_transition_ok('COMPLETED','WAITING') and private.reservation_transition_ok('BOOKED','SEATED'), 'reservation transition table');

-- ---------- payment guard catches every writer, even a direct update ----------
create temp table po as select id from public.orders where payment_status = 'PAID' limit 1;
grant select on po to public;
select pg_temp.check(pg_temp.rejected('update public.orders set payment_status = ''PENDING'' where id = (select id from po)'), 'PAID -> PENDING blocked for any writer');

-- ---------- allowed actions per role ----------
update public.restaurant_settings set require_table_code = false, dine_in_enabled = true;
insert into public.restaurant_tables(name, seats, sort_order) values ('T9', 4, 99);
set role anon;
select pg_temp.as_user('', '');
create temp table ao as select public.place_order('{"idempotencyKey":"5a5a5a5a-0000-0000-0000-000000000001","orderType":"DINE_IN","paymentMethod":"CASH","customerName":"Tara","customerPhone":"9876500021","tableNumber":"T9","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":1}]}') as r;
reset role;
create temp table aid as select (r->'order'->>'id')::uuid id from ao;
grant select on ao, aid to public;
select pg_temp.check((select r->>'error' from ao) is null, 'dine-in app order placed');
select pg_temp.check((select status from public.restaurant_tables where name = 'T9') = 'OCCUPIED', 'app dine-in order occupies its table');
select pg_temp.check(pg_temp.rejected('update public.restaurant_tables set status = ''FREE'' where name = ''T9'''), 'cannot free a table with an open bill');

set role authenticated;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a', 'owner@test.in');
select pg_temp.check((public.update_order_status((select id from aid), 'ACCEPTED')->'nextStatuses') @> '["CANCELLED","PREPARING"]', 'owner sees next actions');
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b', 'kitchen@test.in');
select public.update_order_status((select id from aid), 'PREPARING');
select public.update_order_status((select id from aid), 'READY');
select pg_temp.as_user('00000000-0000-0000-0000-0000000000e3', 'waiter@test.in');
select pg_temp.check(not ((public.admin_list_orders(array['READY']::public.order_status[])->0->'nextStatuses') ? 'COMPLETED'), 'waiter is never offered "completed"');
select pg_temp.as_user('00000000-0000-0000-0000-0000000000e1', 'cashier@test.in');
select pg_temp.check((public.admin_list_orders(array['READY']::public.order_status[])->0->'nextStatuses') ? 'COMPLETED', 'cashier is offered "completed"');

-- ---------- kitchen undo cannot desync a ready order ----------
reset role;
create temp table it as select i.id from public.order_items i where i.order_id = (select id from aid) limit 1;
grant select on it to public;
set role authenticated;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b', 'kitchen@test.in');
select pg_temp.check(pg_temp.rejected('select public.kds_set_item_status((select id from it), ''PREPARING'')'), 'undo blocked once the order is ready');
select pg_temp.check(pg_temp.rejected('select public.kds_set_item_status((select id from it), ''VOID'')'), 'VOID cannot be set from the kitchen screen');

-- ---------- table freed after the bill closes; code cleared ----------
select pg_temp.as_user('00000000-0000-0000-0000-0000000000e1', 'cashier@test.in');
select public.complete_order_with_payment((select id from aid), 'CASH');
reset role;
select pg_temp.check((select status from public.restaurant_tables where name = 'T9') = 'CLEANING', 'table goes to cleaning when the last bill closes');
update public.restaurant_tables set status = 'FREE' where name = 'T9';
select pg_temp.check((select session_code is null from public.restaurant_tables where name = 'T9'), 'free table has no old table code');
select pg_temp.check(pg_temp.rejected('update public.restaurant_tables set status = ''CLEANING'' where name = ''T9'' and status = ''FREE'''), 'FREE -> CLEANING is not a valid move');

-- ---------- reservations ----------
insert into public.reservations(customer_name, party_size, reserved_for) values ('Kumar', 4, now() + interval '1 hour');
update public.reservations set status = 'SEATED' where customer_name = 'Kumar';
update public.reservations set status = 'COMPLETED' where customer_name = 'Kumar';
select pg_temp.check(pg_temp.rejected('update public.reservations set status = ''WAITING'' where customer_name = ''Kumar'''), 'completed booking cannot go back to waiting');
select pg_temp.check(pg_temp.rejected('insert into public.reservations(customer_name, party_size, reserved_for, status) values (''X'', 2, now(), ''COMPLETED'')'), 'new booking cannot start completed');

-- ---------- delivery: rider required while out, unlock locked code ----------
create temp table dl as select id from public.orders where order_type = 'DELIVERY' and delivery_otp_attempts >= 5 limit 1;
grant select on dl to public;
set role anon;
select pg_temp.as_user('', '');
create temp table d3 as select public.place_order('{"idempotencyKey":"5a5a5a5a-0000-0000-0000-000000000002","orderType":"DELIVERY","paymentMethod":"CASH","customerName":"Ravi","customerPhone":"9876500022","deliveryAddress":"Near the main bus stand","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":1}]}') as r;
reset role;
create temp table d3i as select (r->'order'->>'id')::uuid id from d3;
grant select on d3, d3i to public;
set role authenticated;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a', 'owner@test.in');
select public.update_order_status((select id from d3i), 'ACCEPTED');
select public.update_order_status((select id from d3i), 'PREPARING');
select public.update_order_status((select id from d3i), 'READY');
select pg_temp.check(pg_temp.rejected('select public.update_order_status((select id from d3i), ''COMPLETED'')'), 'ready delivery cannot be completed directly');
select public.assign_delivery_staff((select id from d3i), '00000000-0000-0000-0000-0000000000d1');
select public.update_order_status((select id from d3i), 'OUT_FOR_DELIVERY');
select pg_temp.check(pg_temp.rejected('select public.assign_delivery_staff((select id from d3i), null)'), 'rider cannot be removed while out for delivery');
select pg_temp.check((public.assign_delivery_staff((select id from d3i), '00000000-0000-0000-0000-0000000000d2')->>'assignedStaffName') = 'Rider Two', 'rider can be swapped while out');
reset role;
update public.orders set delivery_otp_attempts = 5 where id = (select id from d3i);
set role authenticated;
select pg_temp.check((public.unlock_delivery_code((select id from d3i))->>'otpLocked')::boolean = false, 'owner unlocks a locked delivery code');
select pg_temp.as_user('00000000-0000-0000-0000-0000000000e1', 'cashier@test.in');
do $$ begin perform public.unlock_delivery_code((select id from d3i)); raise exception 'cashier unlocked';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'only owner/manager can unlock');

-- ---------- one "can order" answer ----------
set role anon;
select pg_temp.check((public.restaurant_status() ? 'canOrder'), 'restaurant_status says whether ordering is possible');
reset role;
select 'STATE MACHINE TESTS PASSED';
