-- =====================================================================
-- Swad Sangam — role helpers, audit logging, row level security and
-- server-authoritative business logic (PRD §6, §9.3, §11, §12)
-- =====================================================================

-- ---------------------------------------------------------------------
-- Role helpers
-- ---------------------------------------------------------------------
create or replace function private.my_role() returns public.staff_role
language sql stable security definer set search_path = '' as $$
  select s.role from public.staff s
   where s.user_id = auth.uid() and s.is_active and s.role is not null
$$;

create or replace function private.has_role(variadic p_roles public.staff_role[]) returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce(private.my_role() = any(p_roles), false)
$$;

create or replace function private.require_role(variadic p_roles public.staff_role[]) returns public.staff_role
language plpgsql stable security definer set search_path = '' as $$
declare r public.staff_role;
begin
  r := private.my_role();
  if r is null or not (r = any(p_roles)) then
    raise exception 'NOT_AUTHORIZED' using errcode = '42501';
  end if;
  return r;
end $$;

grant usage on schema private to anon, authenticated;
grant execute on function private.my_role(), private.has_role(public.staff_role[]) to anon, authenticated;

-- ---------------------------------------------------------------------
-- New auth users get an inactive staff row (owner activates them)
-- ---------------------------------------------------------------------
create or replace function private.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.staff(user_id, email, display_name)
  values (new.id, coalesce(new.email, ''), coalesce(new.raw_user_meta_data->>'display_name', split_part(coalesce(new.email,''), '@', 1)))
  on conflict (user_id) do nothing;
  return new;
end $$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function private.handle_new_user();

alter table public.staff
  add constraint staff_user_fk foreign key (user_id) references auth.users(id) on delete cascade;

-- Bootstrap the first owner. Run ONCE in the Supabase SQL editor:
--   select public.make_owner('owner@example.com');
create or replace function public.make_owner(p_email text) returns text
language plpgsql security definer set search_path = '' as $$
declare v_uid uuid;
begin
  select id into v_uid from auth.users where lower(email) = lower(trim(p_email));
  if v_uid is null then
    raise exception 'No account with email %. Sign up on the admin dashboard first.', p_email;
  end if;
  insert into public.staff(user_id, email, role, is_active)
  values (v_uid, lower(trim(p_email)), 'OWNER', true)
  on conflict (user_id) do update set role = 'OWNER', is_active = true;
  return 'OK: ' || p_email || ' is now OWNER';
end $$;
revoke execute on function public.make_owner(text) from public, anon, authenticated;

-- ---------------------------------------------------------------------
-- Audit logging (PRD §10.6)
-- ---------------------------------------------------------------------
create or replace function private.write_audit(p_action text, p_entity text, p_entity_id text,
                                               p_old jsonb, p_new jsonb) returns void
language sql security definer set search_path = '' as $$
  insert into public.audit_logs(actor_id, actor_email, action, entity, entity_id, old_value, new_value)
  values (auth.uid(), auth.jwt()->>'email', p_action, p_entity, p_entity_id, p_old, p_new);
$$;

create or replace function private.audit_row() returns trigger
language plpgsql security definer set search_path = '' as $$
declare v_old jsonb; v_new jsonb; v_id text;
begin
  if tg_op in ('UPDATE','DELETE') then v_old := to_jsonb(old); end if;
  if tg_op in ('INSERT','UPDATE') then v_new := to_jsonb(new); end if;
  if tg_op = 'UPDATE' and v_old - 'updated_at' = v_new - 'updated_at' then
    return new;  -- nothing meaningful changed
  end if;
  v_id := coalesce(v_new->>'id', v_old->>'id', v_new->>'user_id', v_old->>'user_id', v_new->>'code', v_old->>'code');
  perform private.write_audit(tg_op, tg_table_name, v_id, v_old, v_new);
  return coalesce(new, old);
end $$;

create trigger audit_settings    after insert or update or delete on public.restaurant_settings for each row execute function private.audit_row();
create trigger audit_categories  after insert or update or delete on public.categories          for each row execute function private.audit_row();
create trigger audit_menu_items  after insert or update or delete on public.menu_items          for each row execute function private.audit_row();
create trigger audit_offers      after insert or update or delete on public.offers              for each row execute function private.audit_row();
create trigger audit_coupons     after insert or update or delete on public.coupons             for each row execute function private.audit_row();
create trigger audit_staff       after insert or update or delete on public.staff               for each row execute function private.audit_row();
create trigger audit_adjustments after insert or update or delete on public.order_adjustments   for each row execute function private.audit_row();

-- Historical order money fields and item snapshots are immutable.
create or replace function private.protect_order_money() returns trigger
language plpgsql as $$
begin
  if (new.subtotal_paise, new.tax_paise, new.packaging_fee_paise, new.delivery_fee_paise,
      new.discount_paise, new.total_paise, new.order_number, new.idempotency_key)
     is distinct from
     (old.subtotal_paise, old.tax_paise, old.packaging_fee_paise, old.delivery_fee_paise,
      old.discount_paise, old.total_paise, old.order_number, old.idempotency_key) then
    raise exception 'ORDER_AMOUNTS_IMMUTABLE: use an order adjustment (refund/correction) instead';
  end if;
  return new;
end $$;
create trigger trg_protect_order_money before update on public.orders
  for each row execute function private.protect_order_money();

create or replace function private.block_change() returns trigger
language plpgsql as $$
begin
  raise exception '% rows are immutable', tg_table_name;
end $$;
create trigger trg_order_items_immutable before update or delete on public.order_items
  for each row when (pg_trigger_depth() = 0) execute function private.block_change();
create trigger trg_audit_immutable before update or delete on public.audit_logs
  for each row execute function private.block_change();
create trigger trg_adjustments_immutable before update or delete on public.order_adjustments
  for each row execute function private.block_change();

-- ---------------------------------------------------------------------
-- Row level security
-- ---------------------------------------------------------------------
alter table public.restaurant_settings  enable row level security;
alter table public.categories           enable row level security;
alter table public.menu_items           enable row level security;
alter table public.offers               enable row level security;
alter table public.coupons              enable row level security;
alter table public.staff                enable row level security;
alter table public.order_counters       enable row level security;
alter table public.orders               enable row level security;
alter table public.order_items          enable row level security;
alter table public.order_status_history enable row level security;
alter table public.order_adjustments    enable row level security;
alter table public.feedback             enable row level security;
alter table public.audit_logs           enable row level security;
alter table public.analytics_events     enable row level security;

-- Settings: public read, owner write
create policy settings_read   on public.restaurant_settings for select using (true);
create policy settings_update on public.restaurant_settings for update to authenticated
  using (private.has_role('OWNER')) with check (private.has_role('OWNER'));

-- Categories / menu / offers: public read of published rows, owner+manager write
create policy categories_read on public.categories for select
  using (is_active or private.has_role('OWNER','MANAGER','STAFF','KITCHEN'));
create policy categories_write on public.categories for all to authenticated
  using (private.has_role('OWNER','MANAGER')) with check (private.has_role('OWNER','MANAGER'));

create policy menu_read on public.menu_items for select
  using ((not is_archived and exists (select 1 from public.categories c where c.id = category_id and c.is_active))
         or private.has_role('OWNER','MANAGER','STAFF','KITCHEN'));
create policy menu_write on public.menu_items for all to authenticated
  using (private.has_role('OWNER','MANAGER')) with check (private.has_role('OWNER','MANAGER'));

-- Staff may toggle availability (stock) without full menu rights
create or replace function public.set_item_availability(p_item_id text, p_available boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','STAFF','KITCHEN');
  update public.menu_items set is_available = p_available where id = p_item_id;
  if not found then raise exception 'ITEM_NOT_FOUND'; end if;
end $$;

create policy offers_read on public.offers for select
  using ((is_active and (starts_at is null or starts_at <= now()) and (ends_at is null or ends_at > now()))
         or private.has_role('OWNER','MANAGER'));
create policy offers_write on public.offers for all to authenticated
  using (private.has_role('OWNER','MANAGER')) with check (private.has_role('OWNER','MANAGER'));

create policy coupons_admin on public.coupons for all to authenticated
  using (private.has_role('OWNER','MANAGER')) with check (private.has_role('OWNER','MANAGER'));

-- Staff table: everyone sees their own row; owner/manager see all. Writes via RPC only.
create policy staff_read on public.staff for select to authenticated
  using (user_id = auth.uid() or private.has_role('OWNER','MANAGER'));

-- Orders: front-of-house roles read; all writes through RPCs.
create policy orders_read on public.orders for select to authenticated
  using (private.has_role('OWNER','MANAGER','STAFF'));
create policy order_items_read on public.order_items for select to authenticated
  using (private.has_role('OWNER','MANAGER','STAFF'));
create policy order_history_read on public.order_status_history for select to authenticated
  using (private.has_role('OWNER','MANAGER','STAFF'));
create policy adjustments_read on public.order_adjustments for select to authenticated
  using (private.has_role('OWNER','MANAGER'));
create policy feedback_read on public.feedback for select to authenticated
  using (private.has_role('OWNER','MANAGER'));
create policy audit_read on public.audit_logs for select to authenticated
  using (private.has_role('OWNER'));
create policy analytics_read on public.analytics_events for select to authenticated
  using (private.has_role('OWNER','MANAGER'));

-- Defence in depth: the public roles never write these tables directly.
revoke insert, update, delete on public.orders, public.order_items, public.order_status_history,
  public.order_adjustments, public.feedback, public.audit_logs, public.analytics_events,
  public.order_counters, public.staff from anon, authenticated;
revoke all on public.order_counters from anon, authenticated;
revoke all on public.coupons from anon;

-- ---------------------------------------------------------------------
-- Opening hours
-- ---------------------------------------------------------------------
create or replace function private.is_open_at(p_hours jsonb, p_tz text, p_at timestamptz) returns boolean
language plpgsql stable set search_path = '' as $$
declare
  v_local timestamp := p_at at time zone p_tz;
  v_t time := v_local::time;
  v_dow int := extract(isodow from v_local)::int;
  v_prev int := case when v_dow = 1 then 7 else v_dow - 1 end;
  i jsonb; o time; c time;
begin
  for i in select * from jsonb_array_elements(coalesce(p_hours->(v_dow::text), '[]'::jsonb)) loop
    o := (i->>'open')::time; c := (i->>'close')::time;
    if c > o then
      if v_t >= o and v_t < c then return true; end if;
    else  -- interval crosses midnight (or 24h when open = close)
      if v_t >= o then return true; end if;
    end if;
  end loop;
  for i in select * from jsonb_array_elements(coalesce(p_hours->(v_prev::text), '[]'::jsonb)) loop
    o := (i->>'open')::time; c := (i->>'close')::time;
    if c <= o and v_t < c then return true; end if;
  end loop;
  return false;
end $$;

create or replace function public.restaurant_status() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare s public.restaurant_settings; v_open boolean;
begin
  select * into s from public.restaurant_settings where id = 1;
  v_open := not s.temporarily_closed and private.is_open_at(s.opening_hours, s.timezone, now());
  return jsonb_build_object(
    'isOpenNow', v_open,
    'acceptingOrders', v_open and s.accepting_orders,
    'pickupEnabled', s.pickup_enabled,
    'dineInEnabled', s.dine_in_enabled,
    'deliveryEnabled', s.delivery_enabled,
    'temporarilyClosed', s.temporarily_closed,
    'closureMessage', s.closure_message,
    'serverTime', now());
end $$;

-- ---------------------------------------------------------------------
-- Order JSON (customer view never exposes other customers or staff data)
-- ---------------------------------------------------------------------
create or replace function private.order_json(p_order_id uuid, p_staff_view boolean default false) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare o public.orders; s public.restaurant_settings; v jsonb;
begin
  select * into o from public.orders where id = p_order_id;
  if not found then return null; end if;
  select * into s from public.restaurant_settings where id = 1;
  v := jsonb_build_object(
    'id', o.id,
    'orderNumber', o.order_number,
    'orderStatus', o.order_status,
    'paymentStatus', o.payment_status,
    'paymentMethod', o.payment_method,
    'orderType', o.order_type,
    'tableNumber', o.table_number,
    'customerName', o.customer_name,
    'subtotalPaise', o.subtotal_paise,
    'taxPaise', o.tax_paise,
    'taxRateBps', o.tax_rate_bps,
    'pricesIncludeTax', o.prices_include_tax,
    'packagingFeePaise', o.packaging_fee_paise,
    'deliveryFeePaise', o.delivery_fee_paise,
    'discountPaise', o.discount_paise,
    'totalPaise', o.total_paise,
    'couponCode', o.coupon_code,
    'rejectionReason', o.rejection_reason,
    'cancelReason', o.cancel_reason,
    'cancelledBy', o.cancelled_by,
    'estimatedReadyAt', o.estimated_ready_at,
    'createdAt', o.created_at,
    'acceptedAt', o.accepted_at,
    'readyAt', o.ready_at,
    'completedAt', o.completed_at,
    'restaurantPhone', s.phone,
    'canCancel', (o.order_status = 'PLACED' or (o.order_status = 'ACCEPTED' and s.allow_cancel_after_accept)),
    'items', coalesce((select jsonb_agg(jsonb_build_object(
                 'itemId', i.item_id, 'nameSnapshot', i.name_snapshot, 'foodType', i.food_type,
                 'unitPricePaise', i.unit_price_paise, 'quantity', i.quantity,
                 'options', i.options_snapshot, 'lineTotalPaise', i.line_total_paise) order by i.id)
               from public.order_items i where i.order_id = o.id), '[]'::jsonb),
    'history', coalesce((select jsonb_agg(jsonb_build_object('status', h.to_status, 'at', h.created_at) order by h.id)
               from public.order_status_history h where h.order_id = o.id), '[]'::jsonb)
  );
  if p_staff_view then
    v := v || jsonb_build_object(
      'customerPhone', o.customer_phone,
      'deliveryAddress', o.delivery_address,
      'deliveryLandmark', o.delivery_landmark,
      'pickupInstructions', o.pickup_instructions,
      'notes', o.notes,
      'paymentReference', o.payment_reference);
  else
    v := v || jsonb_build_object('deliveryAddress', o.delivery_address);
  end if;
  return v;
end $$;

-- ---------------------------------------------------------------------
-- Error helper for structured RPC responses
-- ---------------------------------------------------------------------
create or replace function private.err(p_code text, p_message text, p_details jsonb default null) returns jsonb
language sql immutable as $$
  select jsonb_build_object('ok', false, 'error', p_code, 'message', p_message, 'details', p_details)
$$;

-- ---------------------------------------------------------------------
-- POST /v1/orders  →  rpc/place_order
-- Server recalculates every price. The client is never trusted.
-- ---------------------------------------------------------------------
create or replace function public.place_order(p_order jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare
  s            public.restaurant_settings;
  v_key        uuid;
  v_existing   uuid;
  v_name       text;
  v_phone      text;
  v_type       public.order_type;
  v_pay        public.payment_method;
  v_line       jsonb;
  m            public.menu_items;
  v_qty        int;
  v_opt_id     text;
  v_opt        jsonb;
  v_opts_snap  jsonb;
  v_unit       int;
  v_lines      jsonb := '[]'::jsonb;
  v_subtotal   int := 0;
  v_discount   int := 0;
  v_tax        int := 0;
  v_pack       int := 0;
  v_delivery   int := 0;
  v_total      int;
  v_coupon     public.coupons;
  v_coupon_code text;
  v_order_id   uuid;
  v_year       int;
  v_seq        int;
  v_number     text;
  v_open_count int;
  v_unavailable jsonb := '[]'::jsonb;
  v_prices     jsonb := '{}'::jsonb;
  v_prep       int;
begin
  if p_order is null or jsonb_typeof(p_order) <> 'object' then
    return private.err('INVALID_REQUEST', 'Order payload missing');
  end if;

  -- Idempotency (duplicate submission returns the original order)
  begin
    v_key := (p_order->>'idempotencyKey')::uuid;
  exception when others then
    return private.err('INVALID_REQUEST', 'idempotencyKey must be a UUID');
  end;
  if v_key is null then return private.err('INVALID_REQUEST', 'idempotencyKey is required'); end if;
  select id into v_existing from public.orders where idempotency_key = v_key;
  if v_existing is not null then
    return jsonb_build_object('ok', true, 'duplicate', true,
      'order', private.order_json(v_existing) || jsonb_build_object(
        'accessToken', (select access_token from public.orders where id = v_existing)));
  end if;

  select * into s from public.restaurant_settings where id = 1;

  -- Restaurant availability
  if s.temporarily_closed then
    return private.err('RESTAURANT_CLOSED', coalesce(s.closure_message, 'The restaurant is temporarily closed.'));
  end if;
  if not s.accepting_orders then
    return private.err('ORDERS_CLOSED', 'The restaurant is not accepting app orders right now. Please call to order.');
  end if;
  if not private.is_open_at(s.opening_hours, s.timezone, now()) then
    return private.err('RESTAURANT_CLOSED', 'The restaurant is closed right now.');
  end if;

  -- Order type
  begin
    v_type := (p_order->>'orderType')::public.order_type;
  exception when others then
    return private.err('INVALID_ORDER_TYPE', 'Unknown order type');
  end;
  if v_type is null
     or (v_type = 'PICKUP'   and not s.pickup_enabled)
     or (v_type = 'DINE_IN'  and not s.dine_in_enabled)
     or (v_type = 'DELIVERY' and not s.delivery_enabled) then
    return private.err('ORDER_TYPE_UNAVAILABLE', 'This order type is not available right now.');
  end if;
  if v_type = 'DELIVERY' and length(trim(coalesce(p_order->>'deliveryAddress', ''))) < 10 then
    return private.err('INVALID_ADDRESS', 'Please enter a complete delivery address.');
  end if;

  -- Payment method (online payment is Phase 2 and needs a gateway + webhook)
  begin
    v_pay := (p_order->>'paymentMethod')::public.payment_method;
  exception when others then
    return private.err('PAYMENT_METHOD_UNAVAILABLE', 'Unknown payment method');
  end;
  if v_pay is null
     or (v_pay = 'CASH' and not s.cash_enabled)
     or (v_pay = 'UPI_AT_RESTAURANT' and not s.upi_at_restaurant_enabled)
     or (v_pay = 'ONLINE') then
    return private.err('PAYMENT_METHOD_UNAVAILABLE', 'This payment method is not available.');
  end if;

  -- Customer details
  v_name := regexp_replace(trim(coalesce(p_order->>'customerName', '')), '\s+', ' ', 'g');
  if length(v_name) < 2 or length(v_name) > 60 then
    return private.err('INVALID_NAME', 'Please enter your name (2–60 characters).');
  end if;
  v_phone := regexp_replace(coalesce(p_order->>'customerPhone', ''), '[^0-9]', '', 'g');
  if length(v_phone) = 12 and left(v_phone, 2) = '91' then v_phone := substr(v_phone, 3); end if;
  if length(v_phone) = 11 and left(v_phone, 1) = '0'  then v_phone := substr(v_phone, 2); end if;
  if v_phone !~ '^[6-9][0-9]{9}$' then
    return private.err('INVALID_PHONE', 'Please enter a valid 10-digit Indian mobile number.');
  end if;
  v_phone := '+91' || v_phone;

  if coalesce((p_order->>'termsAccepted')::boolean, false) is not true then
    return private.err('TERMS_NOT_ACCEPTED', 'Please accept the order terms.');
  end if;

  -- Items
  if jsonb_typeof(p_order->'items') <> 'array' or jsonb_array_length(p_order->'items') = 0 then
    return private.err('EMPTY_CART', 'Your cart is empty.');
  end if;
  if jsonb_array_length(p_order->'items') > 50 then
    return private.err('TOO_MANY_ITEMS', 'Too many lines in one order.');
  end if;

  for v_line in select * from jsonb_array_elements(p_order->'items') loop
    begin
      v_qty := (v_line->>'quantity')::int;
    exception when others then v_qty := null;
    end;
    if v_qty is null or v_qty < 1 or v_qty > s.max_item_quantity then
      return private.err('INVALID_QUANTITY', format('Quantity must be between 1 and %s.', s.max_item_quantity));
    end if;

    select mi.* into m from public.menu_items mi
      join public.categories c on c.id = mi.category_id
     where mi.id = v_line->>'itemId' and not mi.is_archived and c.is_active;
    if not found or not m.is_available then
      v_unavailable := v_unavailable || jsonb_build_array(jsonb_build_object(
        'itemId', v_line->>'itemId', 'name', coalesce(m.name, v_line->>'itemId')));
      continue;
    end if;

    v_unit := m.price_paise;
    v_opts_snap := '[]'::jsonb;
    if jsonb_typeof(v_line->'optionIds') = 'array' then
      for v_opt_id in select distinct x from jsonb_array_elements_text(v_line->'optionIds') x loop
        select o into v_opt from jsonb_array_elements(m.options) o where o->>'id' = v_opt_id;
        if v_opt is null or coalesce((v_opt->>'isAvailable')::boolean, true) = false then
          return private.err('INVALID_OPTION', format('An option for %s is no longer available.', m.name),
                             jsonb_build_object('itemId', m.id));
        end if;
        v_unit := v_unit + coalesce((v_opt->>'pricePaise')::int, 0);
        v_opts_snap := v_opts_snap || jsonb_build_array(jsonb_build_object(
          'id', v_opt->>'id', 'name', v_opt->>'name', 'pricePaise', coalesce((v_opt->>'pricePaise')::int, 0)));
        v_opt := null;
      end loop;
    end if;

    v_prices := v_prices || jsonb_build_object(m.id, m.price_paise);
    v_subtotal := v_subtotal + v_unit * v_qty;
    v_prep := greatest(coalesce(v_prep, 0), coalesce(m.preparation_minutes, s.default_prep_minutes));
    v_lines := v_lines || jsonb_build_array(jsonb_build_object(
      'itemId', m.id, 'name', m.name, 'foodType', m.food_type, 'unit', v_unit,
      'qty', v_qty, 'options', v_opts_snap));
  end loop;

  if jsonb_array_length(v_unavailable) > 0 then
    return private.err('ITEM_UNAVAILABLE', 'Some items are no longer available.', jsonb_build_object('items', v_unavailable));
  end if;

  -- Coupon
  v_coupon_code := nullif(upper(trim(coalesce(p_order->>'couponCode', ''))), '');
  if v_coupon_code is not null then
    select * into v_coupon from public.coupons where code = v_coupon_code for update;
    if not found or not v_coupon.is_active
       or (v_coupon.starts_at is not null and v_coupon.starts_at > now())
       or (v_coupon.ends_at   is not null and v_coupon.ends_at  <= now())
       or (v_coupon.max_uses  is not null and v_coupon.used_count >= v_coupon.max_uses) then
      return private.err('INVALID_COUPON', 'This coupon code is not valid.');
    end if;
    if v_subtotal < v_coupon.min_order_paise then
      return private.err('COUPON_MIN_ORDER', 'Order total is below the coupon minimum.',
                         jsonb_build_object('minOrderPaise', v_coupon.min_order_paise));
    end if;
    if v_coupon.discount_type = 'PERCENT' then
      v_discount := (v_subtotal * v_coupon.value) / 100;
    else
      v_discount := v_coupon.value;
    end if;
    if v_coupon.max_discount_paise is not null then v_discount := least(v_discount, v_coupon.max_discount_paise); end if;
    v_discount := least(v_discount, v_subtotal);
  end if;

  -- Minimum order
  if v_subtotal < s.min_order_paise
     or (v_type = 'DELIVERY' and v_subtotal < s.delivery_min_order_paise) then
    return private.err('BELOW_MINIMUM_ORDER', 'Order is below the minimum order amount.',
      jsonb_build_object('minOrderPaise', greatest(s.min_order_paise,
        case when v_type = 'DELIVERY' then s.delivery_min_order_paise else 0 end)));
  end if;

  -- Fees and tax (explicitly disclosed)
  if v_type in ('PICKUP', 'DELIVERY') then v_pack := s.packaging_fee_paise; end if;
  if v_type = 'DELIVERY' then v_delivery := s.delivery_fee_paise; end if;
  if not s.prices_include_tax and s.tax_rate_bps > 0 then
    v_tax := round(((v_subtotal - v_discount)::numeric * s.tax_rate_bps) / 10000)::int;
  end if;
  v_total := v_subtotal + v_tax + v_pack + v_delivery - v_discount;

  -- Price changed since the customer last saw the cart?
  if p_order ? 'expectedTotalPaise' and (p_order->>'expectedTotalPaise')::int <> v_total then
    return private.err('PRICE_CHANGED', 'Prices have changed. Please review your cart.',
      jsonb_build_object('totalPaise', v_total, 'prices', v_prices));
  end if;

  -- Abuse protection
  select count(*) into v_open_count from public.orders
   where customer_phone = v_phone and order_status in ('PLACED','ACCEPTED','PREPARING','READY','OUT_FOR_DELIVERY')
     and created_at > now() - interval '12 hours';
  if v_open_count >= s.max_open_orders_per_phone then
    return private.err('TOO_MANY_OPEN_ORDERS', 'You already have open orders. Please call the restaurant.');
  end if;

  -- Unique, human-friendly order number: SS-2026-0001
  v_year := extract(year from (now() at time zone s.timezone))::int;
  insert into public.order_counters(year, last) values (v_year, 1)
    on conflict (year) do update set last = public.order_counters.last + 1
    returning last into v_seq;
  v_number := format('SS-%s-%s', v_year, lpad(v_seq::text, 4, '0'));

  begin
    insert into public.orders(order_number, idempotency_key, customer_name, customer_phone, order_type,
        table_number, delivery_address, delivery_landmark, pickup_instructions, notes,
        payment_method, subtotal_paise, tax_paise, packaging_fee_paise, delivery_fee_paise,
        discount_paise, total_paise, tax_rate_bps, prices_include_tax, coupon_code,
        estimated_ready_at, fcm_token, terms_accepted_at)
    values (v_number, v_key, v_name, v_phone, v_type,
        nullif(left(trim(coalesce(p_order->>'tableNumber', '')), 10), ''),
        case when v_type = 'DELIVERY' then left(trim(p_order->>'deliveryAddress'), 300) end,
        case when v_type = 'DELIVERY' then nullif(left(trim(coalesce(p_order->>'deliveryLandmark', '')), 120), '') end,
        nullif(left(trim(coalesce(p_order->>'pickupInstructions', '')), 300), ''),
        nullif(left(trim(coalesce(p_order->>'notes', '')), 300), ''),
        v_pay, v_subtotal, v_tax, v_pack, v_delivery, v_discount, v_total, s.tax_rate_bps,
        s.prices_include_tax, case when v_discount > 0 then v_coupon_code end,
        now() + make_interval(mins => coalesce(v_prep, s.default_prep_minutes)),
        nullif(left(coalesce(p_order->>'fcmToken', ''), 4096), ''), now())
    returning id into v_order_id;
  exception when unique_violation then
    -- concurrent duplicate submission with the same idempotency key
    select id into v_existing from public.orders where idempotency_key = v_key;
    return jsonb_build_object('ok', true, 'duplicate', true,
      'order', private.order_json(v_existing) || jsonb_build_object(
        'accessToken', (select access_token from public.orders where id = v_existing)));
  end;

  insert into public.order_items(order_id, item_id, name_snapshot, food_type, unit_price_paise,
                                 options_snapshot, quantity, line_total_paise)
  select v_order_id, l->>'itemId', l->>'name', (l->>'foodType')::public.food_type, (l->>'unit')::int,
         l->'options', (l->>'qty')::int, (l->>'unit')::int * (l->>'qty')::int
    from jsonb_array_elements(v_lines) l;

  insert into public.order_status_history(order_id, from_status, to_status, actor_label)
  values (v_order_id, null, 'PLACED', 'CUSTOMER');

  if v_discount > 0 then
    update public.coupons set used_count = used_count + 1 where code = v_coupon_code;
  end if;

  insert into public.analytics_events(event_name, session_id, properties)
  values ('order_submitted', left(p_order->>'sessionId', 64),
          jsonb_build_object('orderType', v_type, 'totalPaise', v_total, 'lines', jsonb_array_length(v_lines)));

  return jsonb_build_object('ok', true, 'duplicate', false,
    'order', private.order_json(v_order_id) || jsonb_build_object(
      'accessToken', (select access_token from public.orders where id = v_order_id)));
end $$;

-- Preview totals without placing an order (cart screen, coupon check)
create or replace function public.quote_order(p_order jsonb) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
  s public.restaurant_settings; v_line jsonb; m public.menu_items; v_opt jsonb; v_opt_id text;
  v_qty int; v_unit int; v_subtotal int := 0; v_discount int := 0; v_tax int := 0; v_pack int := 0;
  v_delivery int := 0; v_type public.order_type; v_coupon public.coupons; v_code text; v_msg text;
  v_unavailable jsonb := '[]'::jsonb; v_prices jsonb := '{}'::jsonb;
begin
  select * into s from public.restaurant_settings where id = 1;
  begin v_type := coalesce((p_order->>'orderType')::public.order_type, 'PICKUP');
  exception when others then v_type := 'PICKUP'; end;
  for v_line in select * from jsonb_array_elements(coalesce(p_order->'items', '[]'::jsonb)) loop
    v_qty := greatest(1, least(coalesce((v_line->>'quantity')::int, 1), s.max_item_quantity));
    select mi.* into m from public.menu_items mi join public.categories c on c.id = mi.category_id
     where mi.id = v_line->>'itemId' and not mi.is_archived and c.is_active;
    if not found or not m.is_available then
      v_unavailable := v_unavailable || to_jsonb(v_line->>'itemId');
      continue;
    end if;
    v_unit := m.price_paise;
    if jsonb_typeof(v_line->'optionIds') = 'array' then
      for v_opt_id in select distinct x from jsonb_array_elements_text(v_line->'optionIds') x loop
        select o into v_opt from jsonb_array_elements(m.options) o where o->>'id' = v_opt_id;
        if v_opt is not null then v_unit := v_unit + coalesce((v_opt->>'pricePaise')::int, 0); end if;
        v_opt := null;
      end loop;
    end if;
    v_prices := v_prices || jsonb_build_object(m.id, m.price_paise);
    v_subtotal := v_subtotal + v_unit * v_qty;
  end loop;
  v_code := nullif(upper(trim(coalesce(p_order->>'couponCode', ''))), '');
  if v_code is not null then
    select * into v_coupon from public.coupons where code = v_code;
    if not found or not v_coupon.is_active
       or (v_coupon.starts_at is not null and v_coupon.starts_at > now())
       or (v_coupon.ends_at is not null and v_coupon.ends_at <= now())
       or (v_coupon.max_uses is not null and v_coupon.used_count >= v_coupon.max_uses) then
      v_msg := 'INVALID_COUPON';
    elsif v_subtotal < v_coupon.min_order_paise then
      v_msg := 'COUPON_MIN_ORDER';
    else
      v_discount := case when v_coupon.discount_type = 'PERCENT' then (v_subtotal * v_coupon.value) / 100 else v_coupon.value end;
      if v_coupon.max_discount_paise is not null then v_discount := least(v_discount, v_coupon.max_discount_paise); end if;
      v_discount := least(v_discount, v_subtotal);
    end if;
  end if;
  if v_type in ('PICKUP','DELIVERY') then v_pack := s.packaging_fee_paise; end if;
  if v_type = 'DELIVERY' then v_delivery := s.delivery_fee_paise; end if;
  if not s.prices_include_tax and s.tax_rate_bps > 0 then
    v_tax := round(((v_subtotal - v_discount)::numeric * s.tax_rate_bps) / 10000)::int;
  end if;
  return jsonb_build_object('subtotalPaise', v_subtotal, 'discountPaise', v_discount, 'taxPaise', v_tax,
    'taxLabel', s.tax_label, 'taxRateBps', s.tax_rate_bps, 'pricesIncludeTax', s.prices_include_tax,
    'packagingFeePaise', v_pack, 'deliveryFeePaise', v_delivery,
    'totalPaise', v_subtotal + v_tax + v_pack + v_delivery - v_discount,
    'couponError', v_msg, 'unavailableItemIds', v_unavailable, 'prices', v_prices,
    'minOrderPaise', case when v_type = 'DELIVERY' then greatest(s.min_order_paise, s.delivery_min_order_paise) else s.min_order_paise end);
end $$;

-- GET /v1/orders/{id}  (customer holds the secret access token)
create or replace function public.get_order_by_token(p_token uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select private.order_json(o.id) from public.orders o where o.access_token = p_token
$$;

-- Order history on this device
create or replace function public.get_orders_by_tokens(p_tokens uuid[]) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(private.order_json(o.id) || jsonb_build_object('accessToken', o.access_token)
                            order by o.created_at desc), '[]'::jsonb)
    from public.orders o
   where o.access_token = any(p_tokens[1:50])
$$;

create or replace function public.set_order_push_token(p_token uuid, p_fcm_token text) returns void
language sql volatile security definer set search_path = '' as $$
  update public.orders set fcm_token = nullif(left(p_fcm_token, 4096), '')
   where access_token = p_token and order_status not in ('COMPLETED','REJECTED','CANCELLED');
$$;

-- ---------------------------------------------------------------------
-- Status transitions (PRD §11) — validated server-side
-- ---------------------------------------------------------------------
create or replace function private.valid_transition(p_from public.order_status, p_to public.order_status,
                                                    p_type public.order_type) returns boolean
language sql immutable as $$
  select (p_from, p_to) in (
      ('PLACED'::public.order_status,    'ACCEPTED'::public.order_status),
      ('PLACED',    'REJECTED'),
      ('PLACED',    'CANCELLED'),
      ('ACCEPTED',  'PREPARING'),
      ('ACCEPTED',  'CANCELLED'),
      ('PREPARING', 'READY'),
      ('READY',     'COMPLETED'))
   or (p_type = 'DELIVERY' and (p_from, p_to) in (
      ('READY'::public.order_status, 'OUT_FOR_DELIVERY'::public.order_status),
      ('OUT_FOR_DELIVERY', 'COMPLETED')))
$$;

create or replace function private.apply_status(p_order_id uuid, p_to public.order_status, p_reason text,
                                                p_actor_label text, p_eta_minutes int default null) returns void
language plpgsql security definer set search_path = '' as $$
declare o public.orders;
begin
  select * into o from public.orders where id = p_order_id for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if not private.valid_transition(o.order_status, p_to, o.order_type) then
    raise exception 'INVALID_TRANSITION: % -> %', o.order_status, p_to using errcode = '22023';
  end if;
  if p_to in ('REJECTED','CANCELLED') and length(trim(coalesce(p_reason, ''))) < 3 then
    raise exception 'REASON_REQUIRED' using errcode = '22023';
  end if;

  update public.orders set
    order_status       = p_to,
    accepted_at        = case when p_to = 'ACCEPTED' then now() else accepted_at end,
    ready_at           = case when p_to = 'READY' then now() else ready_at end,
    completed_at       = case when p_to = 'COMPLETED' then now() else completed_at end,
    closed_at          = case when p_to in ('COMPLETED','REJECTED','CANCELLED') then now() else closed_at end,
    rejection_reason   = case when p_to = 'REJECTED' then left(trim(p_reason), 300) else rejection_reason end,
    cancel_reason      = case when p_to = 'CANCELLED' then left(trim(p_reason), 300) else cancel_reason end,
    cancelled_by       = case when p_to = 'CANCELLED' then (case when p_actor_label = 'CUSTOMER' then 'CUSTOMER' else 'RESTAURANT' end) else cancelled_by end,
    estimated_ready_at = case when p_eta_minutes is not null and p_eta_minutes between 1 and 240
                              then now() + make_interval(mins => p_eta_minutes) else estimated_ready_at end,
    payment_status     = case when p_to in ('REJECTED','CANCELLED') and payment_status = 'PAID' then 'REFUND_PENDING'
                              when p_to in ('REJECTED','CANCELLED') and payment_status = 'PENDING' then 'FAILED'
                              else payment_status end
  where id = p_order_id;

  insert into public.order_status_history(order_id, from_status, to_status, actor_id, actor_label, reason)
  values (p_order_id, o.order_status, p_to, auth.uid(), p_actor_label, nullif(trim(coalesce(p_reason, '')), ''));

  if p_to in ('ACCEPTED','REJECTED','COMPLETED','CANCELLED') then
    insert into public.analytics_events(event_name, properties)
    values ('order_' || lower(p_to::text), jsonb_build_object('orderType', o.order_type));
  end if;
end $$;

create or replace function public.cancel_order_by_customer(p_token uuid, p_reason text default 'Cancelled by customer')
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders; s public.restaurant_settings;
begin
  select * into o from public.orders where access_token = p_token;
  if not found then return private.err('ORDER_NOT_FOUND', 'Order not found'); end if;
  select * into s from public.restaurant_settings where id = 1;
  if not (o.order_status = 'PLACED' or (o.order_status = 'ACCEPTED' and s.allow_cancel_after_accept)) then
    return private.err('CANNOT_CANCEL', 'This order can no longer be cancelled in the app. Please call the restaurant.');
  end if;
  perform private.apply_status(o.id, 'CANCELLED',
    coalesce(nullif(trim(p_reason), ''), 'Cancelled by customer'), 'CUSTOMER');
  return jsonb_build_object('ok', true, 'order', private.order_json(o.id));
end $$;

-- PATCH /v1/admin/orders/{id}
create or replace function public.update_order_status(p_order_id uuid, p_status public.order_status,
                                                      p_reason text default null, p_eta_minutes int default null)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare r public.staff_role;
begin
  r := private.require_role('OWNER','MANAGER','STAFF','KITCHEN');
  -- Kitchen may only move food through preparation
  if r = 'KITCHEN' and p_status not in ('PREPARING','READY') then
    raise exception 'NOT_AUTHORIZED' using errcode = '42501';
  end if;
  perform private.apply_status(p_order_id, p_status, p_reason, coalesce(auth.jwt()->>'email', 'staff'), p_eta_minutes);
  return private.order_json(p_order_id, r <> 'KITCHEN');
end $$;

create or replace function public.update_payment_status(p_order_id uuid, p_status public.payment_status,
                                                        p_reference text default null)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders;
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  select * into o from public.orders where id = p_order_id for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if not ((o.payment_status, p_status) in (
      ('PENDING'::public.payment_status, 'PAID'::public.payment_status), ('PENDING','FAILED'),
      ('FAILED','PAID'), ('PAID','REFUND_PENDING'), ('REFUND_PENDING','REFUNDED'))) then
    raise exception 'INVALID_PAYMENT_TRANSITION: % -> %', o.payment_status, p_status using errcode = '22023';
  end if;
  update public.orders set payment_status = p_status,
         payment_reference = coalesce(nullif(left(trim(coalesce(p_reference, '')), 100), ''), payment_reference)
   where id = p_order_id;
  perform private.write_audit('PAYMENT_STATUS', 'orders', p_order_id::text,
    jsonb_build_object('paymentStatus', o.payment_status), jsonb_build_object('paymentStatus', p_status, 'reference', p_reference));
  return private.order_json(p_order_id, true);
end $$;

-- Auditable correction / refund process
create or replace function public.record_order_adjustment(p_order_id uuid, p_type public.adjustment_type,
                                                          p_amount_paise int, p_reason text)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER');
  if not exists (select 1 from public.orders where id = p_order_id) then raise exception 'ORDER_NOT_FOUND'; end if;
  insert into public.order_adjustments(order_id, type, amount_paise, reason, actor_id)
  values (p_order_id, p_type, p_amount_paise, p_reason, auth.uid());
  if p_type = 'REFUND' then
    -- mark fully refunded only when refunds cover the order total
    update public.orders o set payment_status = 'REFUNDED'
     where o.id = p_order_id and o.payment_status in ('PAID', 'REFUND_PENDING')
       and (select coalesce(sum(a.amount_paise), 0) from public.order_adjustments a
             where a.order_id = o.id and a.type = 'REFUND') >= o.total_paise;
  end if;
end $$;

-- Admin order listing (paginated — avoid unbounded reads)
create or replace function public.admin_list_orders(p_statuses public.order_status[] default null,
    p_from timestamptz default null, p_to timestamptz default null, p_search text default null,
    p_limit int default 50, p_offset int default 0)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare r public.staff_role;
begin
  r := private.require_role('OWNER','MANAGER','STAFF','KITCHEN');
  if r = 'KITCHEN' then
    p_statuses := array['ACCEPTED','PREPARING']::public.order_status[];
  end if;
  return coalesce((
    select jsonb_agg(private.order_json(x.id, r <> 'KITCHEN') order by x.created_at desc)
      from (select o.id, o.created_at from public.orders o
             where (p_statuses is null or o.order_status = any(p_statuses))
               and (p_from is null or o.created_at >= p_from)
               and (p_to   is null or o.created_at <  p_to)
               and (p_search is null or o.order_number ilike '%' || p_search || '%'
                    or (r <> 'KITCHEN' and (o.customer_phone like '%' || regexp_replace(p_search, '[^0-9]', '', 'g') || '%'
                                            and length(regexp_replace(p_search, '[^0-9]', '', 'g')) >= 4))
                    or o.customer_name ilike '%' || p_search || '%')
             order by o.created_at desc
             limit least(greatest(p_limit, 1), 200) offset greatest(p_offset, 0)) x), '[]'::jsonb);
end $$;

-- Kitchen preparation queue (no customer phone numbers)
create or replace function public.kitchen_queue() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','STAFF','KITCHEN');
  return coalesce((select jsonb_agg(private.order_json(o.id, false) order by o.accepted_at nulls last, o.created_at)
                     from public.orders o where o.order_status in ('ACCEPTED','PREPARING')), '[]'::jsonb);
end $$;

-- Quick switch so a manager can pause ordering during a rush
create or replace function public.set_accepting_orders(p_accepting boolean) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER');
  update public.restaurant_settings set accepting_orders = p_accepting where id = 1;
end $$;

-- ---------------------------------------------------------------------
-- Staff management (owner only)
-- ---------------------------------------------------------------------
create or replace function public.my_staff_profile() returns jsonb
language sql stable security definer set search_path = '' as $$
  select to_jsonb(s) from public.staff s where s.user_id = auth.uid()
$$;

create or replace function public.set_staff_member(p_user_id uuid, p_role public.staff_role, p_active boolean,
                                                   p_display_name text default null)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER');
  if p_user_id = auth.uid() and (p_role is distinct from 'OWNER' or not p_active)
     and (select count(*) from public.staff where role = 'OWNER' and is_active) <= 1 then
    raise exception 'LAST_OWNER: add another active owner before changing your own access';
  end if;
  update public.staff set role = p_role, is_active = p_active,
         display_name = coalesce(nullif(trim(coalesce(p_display_name, '')), ''), display_name)
   where user_id = p_user_id;
  if not found then raise exception 'STAFF_NOT_FOUND'; end if;
end $$;

-- ---------------------------------------------------------------------
-- Feedback (FR-12) and analytics events (PRD §14)
-- ---------------------------------------------------------------------
create or replace function public.submit_feedback(p jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v_order uuid;
begin
  if p ? 'orderToken' then
    select id into v_order from public.orders where access_token = (p->>'orderToken')::uuid;
    if v_order is not null and exists (select 1 from public.feedback where order_id = v_order) then
      return private.err('ALREADY_SUBMITTED', 'Feedback for this order was already submitted.');
    end if;
  end if;
  insert into public.feedback(order_id, food_quality, order_accuracy, packaging, staff_behaviour, overall,
                              comments, consent_to_publish)
  values (v_order, (p->>'foodQuality')::smallint, (p->>'orderAccuracy')::smallint, (p->>'packaging')::smallint,
          (p->>'staffBehaviour')::smallint, (p->>'overall')::smallint,
          nullif(left(trim(coalesce(p->>'comments', '')), 1000), ''),
          coalesce((p->>'consentToPublish')::boolean, false));
  insert into public.analytics_events(event_name, properties) values ('feedback_submitted', '{}'::jsonb);
  return jsonb_build_object('ok', true);
exception when check_violation or invalid_text_representation or not_null_violation then
  return private.err('INVALID_FEEDBACK', 'Please rate your overall experience (1–5).');
end $$;

create or replace function public.log_events(p_events jsonb) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare e jsonb;
begin
  if jsonb_typeof(p_events) <> 'array' then return; end if;
  for e in select * from jsonb_array_elements(p_events) limit 50 loop
    begin
      insert into public.analytics_events(event_name, session_id, properties, created_at)
      values (e->>'name', left(e->>'sessionId', 64), coalesce(e->'properties', '{}'::jsonb),
              least(now(), greatest(now() - interval '7 days', coalesce((e->>'at')::timestamptz, now()))));
    exception when others then
      null;  -- ignore malformed / unknown events; analytics must never break the app
    end;
  end loop;
end $$;

-- ---------------------------------------------------------------------
-- Dashboard + reports (PRD §6.2 A/E, §14)
-- Gross order value, discounts, taxes, fees, refunds and net receipts are
-- reported separately and never conflated.
-- ---------------------------------------------------------------------
create or replace function public.dashboard_summary() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_tz text; v_start timestamptz;
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  select timezone into v_tz from public.restaurant_settings where id = 1;
  v_start := date_trunc('day', now() at time zone v_tz) at time zone v_tz;
  return (
    select jsonb_build_object(
      'todayOrders',      count(*),
      'pending',          count(*) filter (where order_status = 'PLACED'),
      'accepted',         count(*) filter (where order_status = 'ACCEPTED'),
      'preparing',        count(*) filter (where order_status = 'PREPARING'),
      'ready',            count(*) filter (where order_status in ('READY','OUT_FOR_DELIVERY')),
      'completed',        count(*) filter (where order_status = 'COMPLETED'),
      'cancelled',        count(*) filter (where order_status in ('CANCELLED','REJECTED')),
      'grossSalesPaise',  coalesce(sum(subtotal_paise) filter (where order_status = 'COMPLETED'), 0),
      'collectedPaise',   coalesce(sum(total_paise) filter (where payment_status = 'PAID'), 0),
      'oldestPendingAt',  min(created_at) filter (where order_status = 'PLACED'),
      'bestSellers',      (select coalesce(jsonb_agg(b), '[]'::jsonb) from (
                             select i.name_snapshot as name, sum(i.quantity) as qty
                               from public.order_items i join public.orders o2 on o2.id = i.order_id
                              where o2.order_status = 'COMPLETED' and o2.created_at >= now() - interval '30 days'
                              group by i.name_snapshot
                             having (select count(*) from public.orders o3
                                      where o3.order_status = 'COMPLETED' and o3.created_at >= now() - interval '30 days') >= 20
                              order by 2 desc limit 5) b))
    from public.orders where created_at >= v_start);
end $$;

create or replace function public.sales_report(p_from date, p_to date) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_tz text; v_start timestamptz; v_end timestamptz; v_result jsonb;
begin
  perform private.require_role('OWNER','MANAGER');
  if p_to < p_from or p_to - p_from > 366 then raise exception 'INVALID_RANGE'; end if;
  select timezone into v_tz from public.restaurant_settings where id = 1;
  v_start := p_from::timestamp at time zone v_tz;
  v_end   := (p_to + 1)::timestamp at time zone v_tz;

  with o as (select * from public.orders where created_at >= v_start and created_at < v_end),
       done as (select * from o where order_status = 'COMPLETED'),
       adj as (select a.* from public.order_adjustments a join o on o.id = a.order_id)
  select jsonb_build_object(
    'from', p_from, 'to', p_to,
    'orderCount',          (select count(*) from o),
    'completedCount',      (select count(*) from done),
    'cancelledCount',      (select count(*) from o where order_status = 'CANCELLED'),
    'rejectedCount',       (select count(*) from o where order_status = 'REJECTED'),
    'cancellationRate',    (select case when count(*) = 0 then 0
                              else round(100.0 * count(*) filter (where order_status in ('CANCELLED','REJECTED')) / count(*), 1) end from o),
    'grossOrderValuePaise',(select coalesce(sum(subtotal_paise), 0) from done),
    'discountsPaise',      (select coalesce(sum(discount_paise), 0) from done),
    'taxesPaise',          (select coalesce(sum(tax_paise), 0) from done),
    'feesPaise',           (select coalesce(sum(packaging_fee_paise + delivery_fee_paise), 0) from done),
    'refundsPaise',        (select coalesce(sum(amount_paise), 0) from adj where type = 'REFUND'),
    'correctionsPaise',    (select coalesce(sum(amount_paise), 0) from adj where type = 'CORRECTION'),
    'paidReceiptsPaise',   (select coalesce(sum(total_paise), 0) from o where payment_status in ('PAID','REFUND_PENDING','REFUNDED')),
    'netReceiptsPaise',    (select coalesce(sum(total_paise), 0) from o where payment_status in ('PAID','REFUND_PENDING','REFUNDED'))
                           - (select coalesce(sum(amount_paise), 0) from adj where type = 'REFUND')
                           + (select coalesce(sum(amount_paise), 0) from adj where type = 'CORRECTION'),
    'averageOrderValuePaise', (select coalesce(round(avg(total_paise)), 0) from done),
    'byDay', (select coalesce(jsonb_agg(d order by d->>'date'), '[]'::jsonb) from (
               select jsonb_build_object('date', (created_at at time zone v_tz)::date,
                        'orders', count(*), 'completed', count(*) filter (where order_status = 'COMPLETED'),
                        'grossPaise', coalesce(sum(subtotal_paise) filter (where order_status = 'COMPLETED'), 0)) d
                 from o group by (created_at at time zone v_tz)::date) x),
    'byItem', (select coalesce(jsonb_agg(i order by (i->>'revenuePaise')::int desc), '[]'::jsonb) from (
               select jsonb_build_object('itemId', it.item_id, 'name', max(it.name_snapshot),
                        'quantity', sum(it.quantity), 'revenuePaise', sum(it.line_total_paise)) i
                 from public.order_items it join done on done.id = it.order_id group by it.item_id) y),
    'byCategory', (select coalesce(jsonb_agg(c order by (c->>'revenuePaise')::int desc), '[]'::jsonb) from (
               select jsonb_build_object('category', coalesce(cat.name, 'Archived'),
                        'revenuePaise', sum(it.line_total_paise), 'quantity', sum(it.quantity)) c
                 from public.order_items it join done on done.id = it.order_id
                 left join public.menu_items mi on mi.id = it.item_id
                 left join public.categories cat on cat.id = mi.category_id
                group by cat.name) z),
    'byPaymentMethod', (select coalesce(jsonb_agg(p), '[]'::jsonb) from (
               select jsonb_build_object('method', payment_method, 'orders', count(*), 'totalPaise', sum(total_paise)) p
                 from done group by payment_method) w),
    'byOrderType', (select coalesce(jsonb_agg(t), '[]'::jsonb) from (
               select jsonb_build_object('type', order_type, 'orders', count(*)) t from o group by order_type) v),
    'customers', (select jsonb_build_object(
               'unique', count(distinct customer_phone),
               'returning', count(distinct customer_phone) filter (where exists (
                   select 1 from public.orders p where p.customer_phone = o.customer_phone and p.created_at < v_start)),
               'new', count(distinct customer_phone) filter (where not exists (
                   select 1 from public.orders p where p.customer_phone = o.customer_phone and p.created_at < v_start)))
               from o where not pii_purged),
    'funnel', (select jsonb_object_agg(event_name, n) from (
               select event_name, count(*) n from public.analytics_events
                where created_at >= v_start and created_at < v_end group by event_name) f)
  ) into v_result;
  return v_result;
end $$;

-- ---------------------------------------------------------------------
-- Data retention: anonymise customer personal data after N days
-- (scheduled daily with pg_cron in the next migration)
-- ---------------------------------------------------------------------
create or replace function private.purge_old_personal_data() returns int
language plpgsql security definer set search_path = '' as $$
declare v_days int; v_count int;
begin
  select pii_retention_days into v_days from public.restaurant_settings where id = 1;
  update public.orders set customer_name = 'Customer', customer_phone = 'REDACTED',
         delivery_address = case when order_type = 'DELIVERY' then 'REDACTED' end,
         delivery_landmark = null, pickup_instructions = null, notes = null, fcm_token = null, pii_purged = true
   where not pii_purged and closed_at is not null and closed_at < now() - make_interval(days => v_days);
  get diagnostics v_count = row_count;
  delete from public.analytics_events where created_at < now() - interval '400 days';
  return v_count;
end $$;

-- ---------------------------------------------------------------------
-- Function privileges
-- ---------------------------------------------------------------------
revoke execute on all functions in schema private from public, anon, authenticated;
grant  execute on function private.my_role(), private.has_role(public.staff_role[]) to anon, authenticated;

revoke execute on function
  public.place_order(jsonb), public.quote_order(jsonb), public.get_order_by_token(uuid),
  public.get_orders_by_tokens(uuid[]), public.set_order_push_token(uuid, text),
  public.cancel_order_by_customer(uuid, text), public.submit_feedback(jsonb), public.log_events(jsonb),
  public.restaurant_status(), public.update_order_status(uuid, public.order_status, text, int),
  public.update_payment_status(uuid, public.payment_status, text),
  public.record_order_adjustment(uuid, public.adjustment_type, int, text),
  public.admin_list_orders(public.order_status[], timestamptz, timestamptz, text, int, int),
  public.kitchen_queue(), public.set_accepting_orders(boolean), public.my_staff_profile(),
  public.set_staff_member(uuid, public.staff_role, boolean, text), public.dashboard_summary(),
  public.sales_report(date, date), public.set_item_availability(text, boolean)
from public;

-- Customer (anonymous) API
grant execute on function
  public.place_order(jsonb), public.quote_order(jsonb), public.get_order_by_token(uuid),
  public.get_orders_by_tokens(uuid[]), public.set_order_push_token(uuid, text),
  public.cancel_order_by_customer(uuid, text), public.submit_feedback(jsonb), public.log_events(jsonb),
  public.restaurant_status()
to anon, authenticated;

-- Staff API (each function also checks the caller's role)
grant execute on function
  public.update_order_status(uuid, public.order_status, text, int),
  public.update_payment_status(uuid, public.payment_status, text),
  public.record_order_adjustment(uuid, public.adjustment_type, int, text),
  public.admin_list_orders(public.order_status[], timestamptz, timestamptz, text, int, int),
  public.kitchen_queue(), public.set_accepting_orders(boolean), public.my_staff_profile(),
  public.set_staff_member(uuid, public.staff_role, boolean, text), public.dashboard_summary(),
  public.sales_report(date, date), public.set_item_availability(text, boolean)
to authenticated;
