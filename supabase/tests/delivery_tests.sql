-- Delivery code (OTP), COD handover, rider role, waiter payment blocks, tender, reviews, loyalty, merge.
-- Runs after customer_tests.sql.
\set ON_ERROR_STOP 1
\pset tuples_only on
\pset format unaligned
set client_min_messages = warning;
create or replace function pg_temp.check(p_ok boolean, p_name text) returns void language plpgsql as $$
begin
  if not coalesce(p_ok, false) then raise exception 'TEST FAILED: %', p_name; end if;
  raise notice 'ok - %', p_name;
end $$;
create or replace function pg_temp.books_balanced() returns boolean language sql as $$
  select coalesce(sum(debit_paise), 0) = coalesce(sum(credit_paise), 0) from public.journal_lines
$$;
create or replace function pg_temp.as_user(p_id text, p_email text) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', p_id, false);
  perform set_config('request.jwt.claims', json_build_object('email', p_email)::text, false);
end $$;
set client_min_messages = notice;
reset role;
insert into auth.users(id, email) values ('00000000-0000-0000-0000-0000000000d1', 'rider1@test.in'),
                                         ('00000000-0000-0000-0000-0000000000d2', 'rider2@test.in') on conflict do nothing;
update public.staff set role = 'DELIVERY', is_active = true, display_name = 'Rider One' where email = 'rider1@test.in';
update public.staff set role = 'DELIVERY', is_active = true, display_name = 'Rider Two' where email = 'rider2@test.in';
update public.restaurant_settings set accepting_orders = true, delivery_enabled = true, temporarily_closed = false;
grant select on all tables in schema pg_temp to public;

-- ---------- customer places a COD delivery order ----------
select pg_temp.as_user('', ''); set role anon;
create temp table d as select public.place_order('{"idempotencyKey":"d1d1d1d1-0000-0000-0000-000000000001","orderType":"DELIVERY","paymentMethod":"CASH","customerName":"Meena","customerPhone":"9876500011","deliveryAddress":"Hostel 3, NIT Srinagar","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":2}]}') as r;
reset role;
grant select on d to public;
create temp table ids as select (r->'order'->>'id')::uuid id, (r->'order'->>'accessToken')::uuid tok from d;
grant select on ids to public;
select pg_temp.check((select r->>'error' from d) is null, 'delivery order placed');
select pg_temp.check((select delivery_otp from public.orders where id = (select id from ids)) is null, 'no code before acceptance');

set role authenticated;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a', 'owner@test.in');
select public.update_order_status((select id from ids), 'ACCEPTED', null, 20);
reset role;
select pg_temp.check((select delivery_otp ~ '^[0-9]{4}$' from public.orders where id = (select id from ids)), 'code generated on accept');

-- customer sees the code; kitchen / cashier / rider do not
select pg_temp.as_user('', ''); set role anon;
select pg_temp.check((public.get_order_by_token((select tok from ids))->>'deliveryOtp') ~ '^[0-9]{4}$', 'customer sees delivery code');
reset role;
set role authenticated;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b', 'kitchen@test.in');
select pg_temp.check(not (public.kitchen_queue()->0 ? 'deliveryOtp'), 'kitchen does not see the code');
select pg_temp.as_user('00000000-0000-0000-0000-00000000000d', 'staff@test.in');
select pg_temp.check(not (public.update_order_status((select id from ids), 'PREPARING') ? 'deliveryOtp'), 'staff does not see the code');
select public.update_order_status((select id from ids), 'READY');

-- staff cannot complete a delivery directly, nor skip to completed
do $$ begin
  perform public.update_order_status((select id from ids), 'COMPLETED');
  raise exception 'delivery completed without code';
exception when sqlstate '22023' then null; end $$;
select pg_temp.check(true, 'delivery cannot be completed with update_order_status');
select pg_temp.check((public.complete_order_with_payment((select id from ids), 'CASH', null, '0000')->>'error') = 'NOT_OUT_FOR_DELIVERY', 'handover needs out-for-delivery');

-- assign rider
select pg_temp.check(jsonb_array_length(public.list_delivery_staff()) = 2, 'riders listed');
do $$ begin
  perform public.assign_delivery_staff((select id from ids), '00000000-0000-0000-0000-00000000000b');
  raise exception 'assigned a non-rider';
exception when sqlstate '22023' then null; end $$;
select pg_temp.check(true, 'only riders can be assigned');
select pg_temp.check((public.assign_delivery_staff((select id from ids), '00000000-0000-0000-0000-0000000000d1')->>'assignedStaffName') = 'Rider One', 'rider assigned');

-- rider 2 sees nothing and cannot touch it; rider 1 sees it without the code
select pg_temp.as_user('00000000-0000-0000-0000-0000000000d2', 'rider2@test.in');
select pg_temp.check(jsonb_array_length(public.delivery_my_orders()) = 0, 'other rider sees no orders');
do $$ begin
  perform public.delivery_start((select id from ids));
  raise exception 'other rider started delivery';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'other rider cannot start delivery');
do $$ begin
  perform public.admin_list_orders();
  raise exception 'rider listed all orders';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'rider cannot list all orders');
do $$ begin
  perform public.record_order_payment((select id from ids), '[{"tender":"CASH","amountPaise":100}]', gen_random_uuid());
  raise exception 'rider took a POS payment';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'rider cannot use POS payments');

select pg_temp.as_user('00000000-0000-0000-0000-0000000000d1', 'rider1@test.in');
select pg_temp.check(jsonb_array_length(public.delivery_my_orders()) = 1, 'rider sees assigned order');
select pg_temp.check(not (public.delivery_my_orders()->0 ? 'deliveryOtp'), 'rider list hides the code');
select pg_temp.check((public.delivery_my_orders()->0->>'duePaise')::int > 0, 'rider sees COD amount due');
select public.delivery_start((select id from ids));

-- wrong code is counted (not rolled back); right code collects UPI and completes
select pg_temp.check((public.complete_order_with_payment((select id from ids), 'UPI', 'UTR1', '9999x')->>'error') = 'WRONG_OTP', 'wrong code rejected');
reset role;
select pg_temp.check((select delivery_otp_attempts from public.orders where id = (select id from ids)) = 1, 'wrong attempt counted');
create temp table otp as select delivery_otp v from public.orders where id = (select id from ids);
grant select on otp to public;
set role authenticated;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000d1', 'rider1@test.in');
select pg_temp.check((public.complete_order_with_payment((select id from ids), 'UPI', 'UTR123', (select v from otp))->>'ok')::boolean, 'right code completes delivery');
reset role;
select pg_temp.check((select order_status = 'COMPLETED' and payment_status = 'PAID' from public.orders where id = (select id from ids)), 'order completed and paid');
select pg_temp.check((select tender = 'UPI' from public.order_payments where order_id = (select id from ids)), 'payment recorded as UPI');
select pg_temp.check(pg_temp.books_balanced(), 'books balanced after delivery handover');
select pg_temp.as_user('', ''); set role anon;
select pg_temp.check(not (public.get_order_by_token((select tok from ids)) ? 'deliveryOtp'), 'code hidden after completion');
reset role;

-- ---------- 5 wrong codes lock; owner override with reason ----------
select pg_temp.as_user('', ''); set role anon;
create temp table d2 as select public.place_order('{"idempotencyKey":"d2d2d2d2-0000-0000-0000-000000000002","orderType":"DELIVERY","paymentMethod":"CASH","customerName":"Kiran","customerPhone":"9876500012","deliveryAddress":"Chauras campus road","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":1}]}') as r;
reset role;
create temp table id2 as select (r->'order'->>'id')::uuid id from d2;
grant select on d2, id2 to public;
set role authenticated;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a', 'owner@test.in');
select public.update_order_status((select id from id2), 'ACCEPTED');
select public.update_order_status((select id from id2), 'PREPARING');
select public.update_order_status((select id from id2), 'READY');
select public.assign_delivery_staff((select id from id2), '00000000-0000-0000-0000-0000000000d1');
select public.delivery_start((select id from id2));
select pg_temp.as_user('00000000-0000-0000-0000-0000000000d1', 'rider1@test.in');
select public.complete_order_with_payment((select id from id2), 'CASH', null, 'bad') from generate_series(1, 5);
select pg_temp.check((public.complete_order_with_payment((select id from id2), 'CASH', null, 'bad')->>'error') = 'OTP_LOCKED', 'code locked after 5 wrong tries');
select pg_temp.check((public.complete_order_with_payment((select id from id2), 'CASH', null, null, 'customer phone off')->>'error') = 'OTP_LOCKED', 'rider cannot override');
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a', 'owner@test.in');
select pg_temp.check((public.complete_order_with_payment((select id from id2), 'CASH', null, null, 'customer phone off, verified by call')->>'ok')::boolean, 'owner override completes with reason');
reset role;
select pg_temp.check((select count(*) from public.audit_logs where action = 'ORDER_HANDOVER') = 2, 'handovers audited');

-- ---------- pickup: unpaid cannot complete; owner can with reason ----------
select pg_temp.as_user('', ''); set role anon;
create temp table p as select public.place_order('{"idempotencyKey":"d3d3d3d3-0000-0000-0000-000000000003","orderType":"PICKUP","paymentMethod":"CASH","customerName":"Sonu","customerPhone":"9876500013","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":1}]}') as r;
reset role;
create temp table idp as select (r->'order'->>'id')::uuid id from p;
grant select on p, idp to public;
set role authenticated;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a', 'owner@test.in');
select public.update_order_status((select id from idp), 'ACCEPTED');
select public.update_order_status((select id from idp), 'PREPARING');
select public.update_order_status((select id from idp), 'READY');
select pg_temp.check(not ((public.update_order_status((select id from idp), 'COMPLETED', 'Regular customer, pays monthly')->>'paymentStatus') = 'PAID'), 'owner completed unpaid with reason (audited)');
reset role;
select pg_temp.check((select count(*) from public.audit_logs where action = 'COMPLETED_UNPAID') = 1, 'unpaid completion audited');

-- ---------- waiter: no payments, no cash register ----------
set role authenticated;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000e3', 'waiter@test.in');
do $$ begin
  perform public.update_payment_status((select id from idp), 'PAID');
  raise exception 'waiter marked paid';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'waiter cannot mark orders paid');
do $$ begin perform public.cash_session_status(); raise exception 'waiter saw register';
exception when insufficient_privilege then null; end $$;
do $$ begin perform public.open_cash_session(0); raise exception 'waiter opened register';
exception when insufficient_privilege then null; end $$;
do $$ begin perform public.close_cash_session(0); raise exception 'waiter closed register';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'waiter cannot open, view or close the cash register');
do $$ begin perform public.complete_order_with_payment((select id from idp), 'CASH'); raise exception 'waiter handed over';
exception when insufficient_privilege then null; end $$;
select pg_temp.check(true, 'waiter cannot take handover payments');

-- ---------- refunds: real tender, never more than paid ----------
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a', 'owner@test.in');
do $$ begin
  perform public.record_order_adjustment((select id from ids), 'REFUND', 999999, 'too much refund');
  raise exception 'refund above paid';
exception when sqlstate '22023' then null; end $$;
select pg_temp.check(true, 'refund cannot exceed amount paid');
select public.record_order_adjustment((select id from ids), 'REFUND', 1000, 'One item was cold', 'UPI');
reset role;
select pg_temp.check((select l.credit_paise from public.journal_lines l join public.journal_entries e on e.id = l.entry_id
                       join public.accounts a on a.id = l.account_id
                      where e.source = 'REFUND' and a.code = private.tender_account('UPI') order by e.id desc limit 1) = 1000,
                     'UPI refund credited to the UPI account, not cash');
select pg_temp.check(pg_temp.books_balanced(), 'books balanced after UPI refund');

-- ---------- reviews: order-less feedback is never published or counted ----------
select pg_temp.as_user('', ''); set role anon;
select public.submit_feedback('{"overall":1,"comments":"spam spam","consentToPublish":true}');
select pg_temp.check(not exists (select 1 from jsonb_array_elements(public.public_reviews()->'recent') x where x->>'comments' = 'spam spam'), 'order-less review not published');
reset role;
select pg_temp.check((public.public_reviews()->>'count')::int = (select count(*) from public.feedback where order_id is not null), 'review count only order-linked');

-- ---------- loyalty points come back when a bill is cancelled ----------
reset role;
update public.restaurant_settings set loyalty_enabled = true, loyalty_point_value_paise = 100;
insert into public.customers(name, phone) values ('Loyal Lata', '9000000777');
update public.customers set loyalty_points = 50 where phone = '9000000777';
set role authenticated;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a', 'owner@test.in');
create temp table lb as select public.pos_create_order(jsonb_build_object('idempotencyKey', gen_random_uuid(), 'orderType', 'PICKUP',
  'customerId', (select id from public.customers where phone = '9000000777'), 'items', '[{"itemId":"dish_001","quantity":3}]'::jsonb)) as o;
select public.pos_redeem_points((select (o->>'id')::uuid from lb), 20);
reset role;
select pg_temp.check((select loyalty_points from public.customers where phone = '9000000777') = 30, 'points redeemed on bill');
set role authenticated;
select public.update_order_status((select (o->>'id')::uuid from lb), 'CANCELLED', 'Customer left');
reset role;
select pg_temp.check((select loyalty_points from public.customers where phone = '9000000777') = 50, 'points restored when bill cancelled');

-- ---------- merge bills where the source discount is larger than the target total ----------
set role authenticated;
create temp table m1 as select public.pos_create_order(jsonb_build_object('idempotencyKey', gen_random_uuid(), 'orderType', 'PICKUP', 'items', '[{"itemId":"dish_001","quantity":1}]'::jsonb)) as o;
create temp table m2 as select public.pos_create_order(jsonb_build_object('idempotencyKey', gen_random_uuid(), 'orderType', 'PICKUP', 'items', '[{"itemId":"dish_001","quantity":6}]'::jsonb)) as o;
select public.pos_set_discount((select (o->>'id')::uuid from m2), (select (o->>'subtotalPaise')::bigint - 100 from m2), 'Big party discount');
select pg_temp.check((public.pos_merge_bills((select (o->>'id')::uuid from m1), (select (o->>'id')::uuid from m2))->>'totalPaise')::bigint >= 0, 'merge with large discount works');
reset role;
select pg_temp.check(pg_temp.books_balanced(), 'books balanced at end');

-- ---------- payroll preview clamps negative adjustments ----------
set role authenticated;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a', 'owner@test.in');
select pg_temp.check(coalesce((select bool_and((x->>'bonusPaise')::bigint >= 0 and (x->>'otherDeductionPaise')::bigint >= 0)
   from jsonb_array_elements(public.payroll_preview(date_trunc('month', now())::date,
        (select jsonb_object_agg(id::text, '{"bonusPaise":-500,"otherDeductionPaise":-700}'::jsonb) from public.employees))) x), true),
   'payroll bonus/deduction never negative');
reset role;
select 'DELIVERY TESTS PASSED';
