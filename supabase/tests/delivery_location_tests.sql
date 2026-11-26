-- Delivery location pin and distance-based fee. Runs after state_machine_tests.sql.

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
create or replace function pg_temp.rejected_any(p_sql text) returns boolean language plpgsql as $$
begin
  execute p_sql;
  return false;
exception when others then return true;
end $$;
set client_min_messages = notice;
reset role;

-- Restaurant pin near NIT Srinagar; ₹20 covers the first 2 km, then ₹10 per started km, max 8 km.
update public.restaurant_settings set accepting_orders = true, delivery_enabled = true, temporarily_closed = false,
  latitude = 30.2219, longitude = 78.7836, delivery_fee_paise = 2000, delivery_per_km_paise = 1000,
  delivery_base_km = 2, delivery_radius_km = 8, delivery_road_factor = 1.00, delivery_require_pin = false,
  delivery_min_order_paise = 0, min_order_paise = 0;
create temp table q (k text, r jsonb);
grant select, insert on q to public;

select pg_temp.check(abs(private.haversine_km(30.2219, 78.7836, 30.2534, 78.7836) - 3.5) < 0.05, 'haversine ~3.5 km');

set role anon;
select pg_temp.as_user('', '');
-- 0.0315 deg north = about 3.5 km
insert into q select 'near', public.quote_order('{"orderType":"DELIVERY","deliveryLat":30.2534,"deliveryLng":78.7836,"items":[{"itemId":"dish_001","quantity":1}]}');
insert into q select 'nopin', public.quote_order('{"orderType":"DELIVERY","items":[{"itemId":"dish_001","quantity":1}]}');
insert into q select 'far', public.quote_order('{"orderType":"DELIVERY","deliveryLat":30.3219,"deliveryLng":78.7836,"items":[{"itemId":"dish_001","quantity":1}]}');
insert into q select 'junk', public.quote_order('{"orderType":"DELIVERY","deliveryLat":"abc","deliveryLng":999,"items":[{"itemId":"dish_001","quantity":1}]}');
insert into q select 'pickup', public.quote_order('{"orderType":"PICKUP","deliveryLat":30.3219,"deliveryLng":78.7836,"items":[{"itemId":"dish_001","quantity":1}]}');
reset role;
select pg_temp.check((select (r->>'deliveryFeePaise')::int from q where k = 'near') = 4000, 'fee = base + 2 started km beyond 2 km');
select pg_temp.check((select (r->>'deliveryDistanceKm')::numeric from q where k = 'near') = 3.5, 'quote tells the distance');
select pg_temp.check((select (r->>'deliveryFeePaise')::int from q where k = 'nopin') = 2000, 'no pin keeps the flat fee');
select pg_temp.check((select r->>'error' from q where k = 'far') = 'DELIVERY_OUT_OF_RANGE', 'beyond the radius is refused');
select pg_temp.check((select (r->>'deliveryFeePaise')::int from q where k = 'junk') = 2000, 'invalid coordinates are ignored');
select pg_temp.check((select r->>'error' from q where k = 'pickup') is null and (select (r->>'deliveryFeePaise')::int from q where k = 'pickup') = 0, 'pickup ignores the pin');

update public.restaurant_settings set delivery_road_factor = 1.40;
set role anon;
insert into q select 'hills', public.quote_order('{"orderType":"DELIVERY","deliveryLat":30.2534,"deliveryLng":78.7836,"items":[{"itemId":"dish_001","quantity":1}]}');
reset role;
select pg_temp.check((select (r->>'deliveryDistanceKm')::numeric from q where k = 'hills') = 4.9
  and (select (r->>'deliveryFeePaise')::int from q where k = 'hills') = 5000, 'road factor stretches the distance');
update public.restaurant_settings set delivery_road_factor = 1.00, delivery_require_pin = true;

set role anon;
insert into q select 'req', public.place_order('{"idempotencyKey":"10c10c10-0000-0000-0000-000000000001","orderType":"DELIVERY","paymentMethod":"CASH","customerName":"Asha","customerPhone":"9876500031","deliveryAddress":"Near Srikot bridge, Srinagar","termsAccepted":true,"items":[{"itemId":"dish_001","quantity":1}]}');
insert into q select 'farorder', public.place_order('{"idempotencyKey":"10c10c10-0000-0000-0000-000000000002","orderType":"DELIVERY","paymentMethod":"CASH","customerName":"Asha","customerPhone":"9876500031","deliveryAddress":"Far away village road","deliveryLat":30.3219,"deliveryLng":78.7836,"termsAccepted":true,"items":[{"itemId":"dish_001","quantity":1}]}');
-- the client tries to send its own fee and distance: both ignored
insert into q select 'ok', public.place_order('{"idempotencyKey":"10c10c10-0000-0000-0000-000000000003","orderType":"DELIVERY","paymentMethod":"CASH","customerName":"Asha","customerPhone":"9876500031","deliveryAddress":"Near Srikot bridge, Srinagar","deliveryLat":30.2534,"deliveryLng":78.7836,"deliveryFeePaise":0,"deliveryDistanceKm":0.1,"termsAccepted":true,"items":[{"itemId":"dish_001","quantity":1}]}');
reset role;
select pg_temp.check((select r->>'error' from q where k = 'req') = 'DELIVERY_LOCATION_REQUIRED', 'pin can be made mandatory');
select pg_temp.check((select r->>'error' from q where k = 'farorder') = 'DELIVERY_OUT_OF_RANGE', 'out-of-range order is not placed');
select pg_temp.check(not exists (select 1 from public.orders where idempotency_key in
  ('10c10c10-0000-0000-0000-000000000001', '10c10c10-0000-0000-0000-000000000002')), 'refused orders leave nothing behind');
create temp table lo as select o.* from public.orders o where idempotency_key = '10c10c10-0000-0000-0000-000000000003';
grant select on lo to public;
select pg_temp.check((select delivery_fee_paise from lo) = 4000 and (select delivery_distance_km from lo) = 3.5
  and (select delivery_lat from lo) = 30.2534 and (select delivery_lng from lo) = 78.7836, 'order keeps server fee, pin and distance');
select pg_temp.check((select total_paise = subtotal_paise + tax_paise + packaging_fee_paise + delivery_fee_paise - discount_paise from lo), 'order total includes the distance fee');
select pg_temp.check((select (r->'order'->>'deliveryLat')::float8 from q where k = 'ok') = 30.2534
  and (select (r->'order'->>'deliveryDistanceKm')::numeric from q where k = 'ok') = 3.5, 'customer response carries the pin');

select pg_temp.check((private.order_json((select id from lo), true)->>'deliveryLng')::float8 = 78.7836, 'staff order view has the pin');

-- saved address keeps a pin; half a pin is refused
select pg_temp.check(pg_temp.rejected_any('insert into public.customer_addresses(user_id, address, latitude) values (''00000000-0000-0000-0000-00000000000a'', ''Some long address here'', 30.2)'), 'address pin needs both coordinates');
update public.restaurant_settings set delivery_require_pin = false, delivery_per_km_paise = 0, delivery_radius_km = null;
select 'delivery location tests PASSED';
