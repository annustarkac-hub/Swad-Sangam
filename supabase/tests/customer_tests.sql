-- Customer account tests: profile/address isolation, order linking, my_orders, claim, reviews. Runs after pos_upgrade_tests.sql.
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
insert into auth.users(id, email) values ('00000000-0000-0000-0000-0000000000c1', 'cust1@test.in'),
                                         ('00000000-0000-0000-0000-0000000000c2', 'cust2@test.in') on conflict do nothing;
update public.restaurant_settings set accepting_orders = true, pickup_enabled = true, delivery_enabled = true,
  temporarily_closed = false, delivery_fee_paise = 3000, opening_hours =
  '{"1":[{"open":"00:00","close":"23:59"}],"2":[{"open":"00:00","close":"23:59"}],"3":[{"open":"00:00","close":"23:59"}],"4":[{"open":"00:00","close":"23:59"}],"5":[{"open":"00:00","close":"23:59"}],"6":[{"open":"00:00","close":"23:59"}],"7":[{"open":"00:00","close":"23:59"}]}';

-- customer 1: profile + addresses
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-0000000000c1';
set request.jwt.claims = '{"email":"cust1@test.in"}';
insert into public.customer_profiles(user_id, full_name, phone) values ('00000000-0000-0000-0000-0000000000c1', '  Asha   Rawat ', '9876500001');
select pg_temp.check((select full_name from public.customer_profiles) = 'Asha Rawat', 'profile saved and name tidied');
insert into public.customer_addresses(label, address, is_default) values ('Home', 'Near NIT Ground, Srinagar', true);
insert into public.customer_addresses(label, address, is_default) values ('Office', 'Main market road, Srinagar', true);
select pg_temp.check((select count(*) from public.customer_addresses where is_default) = 1, 'only one default address');
do $$ begin
  insert into public.customer_profiles(user_id, full_name, phone) values ('00000000-0000-0000-0000-0000000000c2', 'Fake', '9876500009');
  raise exception 'wrote other profile';
exception when insufficient_privilege or check_violation then null; end $$;
select pg_temp.check(true, 'cannot create a profile for someone else');

-- customer 1 places a delivery order -> linked to the account
create temp table o1 as select public.place_order('{"idempotencyKey":"c1c1c1c1-0000-0000-0000-000000000001","orderType":"DELIVERY","paymentMethod":"CASH","customerName":"Asha Rawat","customerPhone":"9876500001","deliveryAddress":"Near NIT Ground, Srinagar","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":2}]}') as r;
grant select on o1 to public;
select pg_temp.check((select r->>'error' from o1) is null, 'customer order placed');
select pg_temp.check(jsonb_array_length(public.my_orders()) = 1, 'my_orders shows the order');
select pg_temp.check((public.my_orders()->0->>'deliveryFeePaise')::int = 3000, 'delivery charge from settings');

-- customer 2 sees nothing of customer 1
set request.jwt.claim.sub = '00000000-0000-0000-0000-0000000000c2';
select pg_temp.check((select count(*) from public.customer_addresses) = 0, 'addresses are private');
select pg_temp.check((select count(*) from public.customer_profiles) = 0, 'profiles are private');
select pg_temp.check(jsonb_array_length(public.my_orders()) = 0, 'orders are private');
select pg_temp.check((select count(*) from public.orders) = 0, 'customer cannot read orders table');

-- guest order, then claim it after login
reset role; set role anon;
set request.jwt.claim.sub = '';
create temp table g as select public.place_order('{"idempotencyKey":"c2c2c2c2-0000-0000-0000-000000000002","orderType":"PICKUP","paymentMethod":"CASH","customerName":"Guest","customerPhone":"9876500002","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":1}]}') as r;
reset role; grant select on g to public;
select pg_temp.check((select customer_user_id from public.orders where id = (select (r->'order'->>'id')::uuid from g)) is null, 'guest order not linked');
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-0000000000c2';
select pg_temp.check(public.claim_my_orders(array[(select (r->'order'->>'accessToken')::uuid from g)]) = 1, 'guest order claimed after login');
select pg_temp.check(jsonb_array_length(public.my_orders()) = 1, 'claimed order in my_orders');
select pg_temp.check(public.claim_my_orders(array[(select (r->'order'->>'accessToken')::uuid from g)]) = 0, 'claim is one-time');

-- staff POS order is never linked to the staff user
set request.jwt.claim.sub = '00000000-0000-0000-0000-0000000000e1';
create temp table p as select public.pos_create_order('{"idempotencyKey":"c3c3c3c3-0000-0000-0000-000000000003","orderType":"PICKUP","items":[{"itemId":"dish_001","quantity":1}]}') as o;
reset role;
select pg_temp.check((select customer_user_id from public.orders where id = (select (o->>'id')::uuid from p)) is null, 'POS order not linked to staff');

-- feedback only for completed orders; public reviews show only consented comments
set role anon;
select pg_temp.check((public.submit_feedback(jsonb_build_object('orderToken', (select r->'order'->>'accessToken' from g), 'overall', 5))->>'error') is not null, 'cannot rate an unfinished order');
reset role;
-- reviews must belong to a real completed order (order-less feedback is never published)
insert into public.feedback(order_id, overall, comments, consent_to_publish)
select o.id, v.overall, v.comments, v.consent
  from (select id, row_number() over (order by created_at) n from public.orders
         where order_status = 'COMPLETED' and not exists (select 1 from public.feedback f where f.order_id = orders.id)) o
  join (values (1, 4, 'Tasty momos', true), (2, 2, 'private note', false)) v(n, overall, comments, consent) on v.n = o.n;
set role anon;
select pg_temp.check((public.public_reviews()->>'count')::int >= 2, 'review count');
select pg_temp.check(not (public.public_reviews()::text like '%private note%'), 'unpublished comment hidden');
select pg_temp.check(public.public_reviews()::text like '%Tasty momos%', 'published comment shown');
reset role;
insert into auth.users(id, email, raw_user_meta_data) values ('00000000-0000-0000-0000-0000000000c3', 'cust3@test.in', '{"account_type":"customer"}');
select pg_temp.check(not exists (select 1 from public.staff where user_id = '00000000-0000-0000-0000-0000000000c3'), 'customer sign-up is not a pending staff member');
insert into auth.users(id, email) values ('00000000-0000-0000-0000-0000000000c4', 'newstaff@test.in');
select pg_temp.check(exists (select 1 from public.staff where user_id = '00000000-0000-0000-0000-0000000000c4' and not is_active), 'staff sign-up still creates inactive staff row');
select 'customer tests PASSED';
