-- Delivery location pin + distance-based delivery charge (free: OpenStreetMap map on the website,
-- distance computed here in the database, no paid maps API).
--
-- * Customers can drop a pin (or use their phone's location) for a delivery address.
-- * The server computes the distance from the restaurant (straight line x an owner-set road factor)
--   and the delivery fee = base fee + per-km fee beyond the included km. Owner can set a max radius
--   and make the pin mandatory. Everything is off by default: with per-km fee 0 and no radius the
--   fee stays exactly the old flat delivery fee.
-- * Orders keep the pin and distance so the rider gets exact directions.

alter table public.restaurant_settings
  add column if not exists delivery_per_km_paise int not null default 0 check (delivery_per_km_paise between 0 and 100000),
  add column if not exists delivery_base_km numeric(5,2) not null default 0 check (delivery_base_km between 0 and 100),
  add column if not exists delivery_road_factor numeric(3,2) not null default 1.00 check (delivery_road_factor between 1 and 3),
  add column if not exists delivery_require_pin boolean not null default false;

alter table public.orders
  add column if not exists delivery_lat double precision check (delivery_lat between -90 and 90),
  add column if not exists delivery_lng double precision check (delivery_lng between -180 and 180),
  add column if not exists delivery_distance_km numeric(6,2) check (delivery_distance_km >= 0);

alter table public.customer_addresses
  add column if not exists latitude double precision check (latitude between -90 and 90),
  add column if not exists longitude double precision check (longitude between -180 and 180);
do $$ begin
  alter table public.customer_addresses add constraint customer_addresses_pin_pair
    check ((latitude is null) = (longitude is null));
exception when duplicate_object then null; end $$;

/** Great-circle distance in km. */
create or replace function private.haversine_km(lat1 double precision, lng1 double precision,
                                                lat2 double precision, lng2 double precision) returns double precision
language sql immutable as $$
  select 2 * 6371.0088 * asin(sqrt(
    power(sin(radians(lat2 - lat1) / 2), 2) +
    cos(radians(lat1)) * cos(radians(lat2)) * power(sin(radians(lng2 - lng1) / 2), 2)))
$$;

/** Delivery pin, distance and fee for an order payload. Never trusts a fee or distance from the client.
    Returns {lat, lng, distanceKm, feePaise, error?, message?}. */
create or replace function private.delivery_calc(s public.restaurant_settings, p_order jsonb) returns jsonb
language plpgsql stable set search_path = '' as $$
declare
  v_lat double precision; v_lng double precision; v_km numeric; v_fee int := s.delivery_fee_paise;
begin
  begin
    v_lat := nullif(p_order->>'deliveryLat', '')::double precision;
    v_lng := nullif(p_order->>'deliveryLng', '')::double precision;
  exception when others then v_lat := null; v_lng := null; end;
  if v_lat is null or v_lng is null or v_lat not between -90 and 90 or v_lng not between -180 and 180 then
    v_lat := null; v_lng := null;
  end if;

  if v_lat is null then
    if s.delivery_require_pin and s.latitude is not null then
      return jsonb_build_object('feePaise', v_fee, 'error', 'DELIVERY_LOCATION_REQUIRED',
        'message', 'Please mark your delivery location on the map.');
    end if;
    return jsonb_build_object('feePaise', v_fee);
  end if;
  if s.latitude is null or s.longitude is null then   -- restaurant pin not set: keep the flat fee
    return jsonb_build_object('lat', v_lat, 'lng', v_lng, 'feePaise', v_fee);
  end if;

  v_km := round((private.haversine_km(s.latitude, s.longitude, v_lat, v_lng) * s.delivery_road_factor)::numeric, 1);
  if s.delivery_radius_km is not null and v_km > s.delivery_radius_km then
    return jsonb_build_object('lat', v_lat, 'lng', v_lng, 'distanceKm', v_km, 'feePaise', v_fee,
      'error', 'DELIVERY_OUT_OF_RANGE',
      'message', format('Sorry, we deliver up to %s km. This location is about %s km away.', s.delivery_radius_km, v_km),
      'radiusKm', s.delivery_radius_km);
  end if;
  v_fee := v_fee + s.delivery_per_km_paise * greatest(0, ceil(v_km - s.delivery_base_km))::int;
  return jsonb_build_object('lat', v_lat, 'lng', v_lng, 'distanceKm', v_km, 'feePaise', v_fee);
end $$;
revoke execute on function private.delivery_calc(public.restaurant_settings, jsonb) from public, anon, authenticated;

-- Use the computed fee inside the existing order and quote logic (one source of truth for the
-- money maths). The flat-fee line appears exactly once in each; fail loudly if that ever changes.
do $$
declare v_def text; v_old text := 'if v_type = ''DELIVERY'' then v_delivery := s.delivery_fee_paise; end if;';
        v_new text := 'if v_type = ''DELIVERY'' then v_delivery := (private.delivery_calc(s, p_order)->>''feePaise'')::int; end if;';
        f regprocedure;
begin
  foreach f in array array['private.place_order_core(jsonb)'::regprocedure, 'public.quote_order(jsonb)'::regprocedure] loop
    v_def := pg_get_functiondef(f);
    if position(v_new in v_def) > 0 then continue; end if;          -- already patched
    if (length(v_def) - length(replace(v_def, v_old, ''))) / length(v_old) <> 1 then
      raise exception 'delivery fee line not found exactly once in %', f;
    end if;
    execute replace(v_def, v_old, v_new);
  end loop;
end $$;

-- Quote: refuse out-of-range / missing pin and tell the customer the distance.
alter function public.quote_order(jsonb) set schema private;
alter function private.quote_order(jsonb) rename to quote_order_core;
revoke execute on function private.quote_order_core(jsonb) from public, anon, authenticated;

create or replace function public.quote_order(p_order jsonb) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare s public.restaurant_settings; d jsonb; r jsonb;
begin
  r := private.quote_order_core(p_order);
  if p_order->>'orderType' is distinct from 'DELIVERY' then return r; end if;
  select * into s from public.restaurant_settings where id = 1;
  d := private.delivery_calc(s, p_order);
  if d ? 'error' then
    return private.err(d->>'error', d->>'message', d - 'error' - 'message');
  end if;
  return r || jsonb_build_object('deliveryDistanceKm', d->'distanceKm');
end $$;
revoke execute on function public.quote_order(jsonb) from public;
grant execute on function public.quote_order(jsonb) to anon, authenticated;

-- Place order: same checks, then keep the pin and distance on the order.
create or replace function public.place_order(p_order jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare s public.restaurant_settings; d jsonb; r jsonb; v_id uuid;
begin
  if p_order->>'orderType' = 'DINE_IN' and not private.table_code_ok(coalesce(p_order->>'tableNumber',''), p_order->>'tableCode') then
    return private.err('TABLE_CODE_REQUIRED', 'Please enter the 4-digit table code shown by our staff.');
  end if;
  if p_order->>'orderType' = 'DELIVERY' then
    select * into s from public.restaurant_settings where id = 1;
    d := private.delivery_calc(s, p_order);
    if d ? 'error' then return private.err(d->>'error', d->>'message', d - 'error' - 'message'); end if;
  end if;
  r := private.place_order_core(p_order);
  if d is not null and (r->>'ok')::boolean and not coalesce((r->>'duplicate')::boolean, false) and d ? 'lat' then
    v_id := (r->'order'->>'id')::uuid;
    update public.orders set delivery_lat = (d->>'lat')::double precision, delivery_lng = (d->>'lng')::double precision,
           delivery_distance_km = (d->>'distanceKm')::numeric
     where id = v_id and order_type = 'DELIVERY';
    r := jsonb_set(r, '{order}', (r->'order') || jsonb_build_object(
           'deliveryLat', d->'lat', 'deliveryLng', d->'lng', 'deliveryDistanceKm', d->'distanceKm'));
  end if;
  return r;
end $$;
revoke execute on function public.place_order(jsonb) from public;
grant execute on function public.place_order(jsonb) to anon, authenticated;

-- Staff, rider and customer order views carry the pin so "Directions" opens the exact spot.
do $$
declare v_def text; f regprocedure;
begin
  foreach f in array array['private.order_json(uuid, boolean)'::regprocedure, 'public.delivery_my_orders()'::regprocedure] loop
    v_def := pg_get_functiondef(f);
    if position('''deliveryLat''' in v_def) > 0 then continue; end if;
    if position('''deliveryAddress'', o.delivery_address' in v_def) = 0 then
      raise exception 'deliveryAddress not found in %', f;
    end if;
    execute replace(v_def, '''deliveryAddress'', o.delivery_address',
      '''deliveryAddress'', o.delivery_address, ''deliveryLat'', o.delivery_lat, ''deliveryLng'', o.delivery_lng, ''deliveryDistanceKm'', o.delivery_distance_km');
  end loop;
end $$;
