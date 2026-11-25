-- =====================================================================
-- Swad Sangam — explicit state machines (audit of implicit state, 25 Nov 2026)
-- Every status column now has ONE transition rule, enforced by the database for every writer
-- (RPCs, triggers and direct table edits alike):
--   orders.order_status   private.valid_transition (READY → COMPLETED no longer for deliveries)
--                         + private.allowed_next() = the actions a given role may take now
--   orders.payment_status private.payment_transition_ok (trigger)
--   order_items.prep_status private.prep_transition_ok (kitchen display)
--   restaurant_tables.status private.table_transition_ok + "no FREE/CLEANING while a bill is open" (trigger)
--   reservations.status   private.reservation_transition_ok (trigger)
--   delivery code         unlock_delivery_code(); a rider cannot be removed while the order is on its way
-- Meaning of payment FAILED: "money never collected" (payment failed, order cancelled/rejected unpaid,
-- or bill merged into another one — the cancel reason says which).
-- =====================================================================

-- ---------- order status ----------
create or replace function private.valid_transition(p_from public.order_status, p_to public.order_status,
                                                    p_type public.order_type) returns boolean
language sql immutable as $$
  select (p_from, p_to) in (
      ('PLACED'::public.order_status,    'ACCEPTED'::public.order_status),
      ('PLACED',    'REJECTED'),
      ('PLACED',    'CANCELLED'),
      ('ACCEPTED',  'PREPARING'),
      ('ACCEPTED',  'CANCELLED'),
      ('PREPARING', 'READY'))
   or (p_type <> 'DELIVERY' and (p_from, p_to) = ('READY'::public.order_status, 'COMPLETED'::public.order_status))
   or (p_type = 'DELIVERY' and (p_from, p_to) in (
      ('READY'::public.order_status, 'OUT_FOR_DELIVERY'::public.order_status),
      ('OUT_FOR_DELIVERY', 'COMPLETED')))
$$;

/** Statuses this role may move the order to right now (mirrors update_order_status / handover rules). */
create or replace function private.allowed_next(o public.orders, p_role text) returns text[]
language sql stable as $$
  select coalesce(array_agg(s::text order by s), '{}')
    from unnest(enum_range(null::public.order_status)) s
   where private.valid_transition(o.order_status, s, o.order_type)
     and case
       when p_role is null then false
       when p_role = 'KITCHEN' then s in ('PREPARING','READY')
       when p_role = 'ACCOUNTANT' then false
       when p_role = 'DELIVERY' then s = 'OUT_FOR_DELIVERY' or (s = 'COMPLETED' and o.order_type = 'DELIVERY')
       -- completing means taking money: never for waiters
       when p_role = 'WAITER' then s <> 'COMPLETED'
       else true end
$$;

-- ---------- payment status ----------
create or replace function private.payment_transition_ok(p_from public.payment_status, p_to public.payment_status) returns boolean
language sql immutable as $$
  select p_from = p_to or (p_from, p_to) in (
    ('PENDING'::public.payment_status, 'PAID'::public.payment_status),
    ('PENDING', 'FAILED'),            -- not collected: cancelled / rejected / merged
    ('FAILED', 'PAID'),               -- paid later after all
    ('PAID', 'REFUND_PENDING'),       -- paid order cancelled
    ('PAID', 'REFUNDED'),             -- full refund recorded
    ('REFUND_PENDING', 'REFUNDED'))
$$;

create or replace function private.guard_payment_status() returns trigger
language plpgsql as $$
begin
  if not private.payment_transition_ok(old.payment_status, new.payment_status) then
    raise exception 'INVALID_PAYMENT_TRANSITION: % -> %', old.payment_status, new.payment_status using errcode = '22023';
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_payment_status on public.orders;
create trigger trg_guard_payment_status before update of payment_status on public.orders
  for each row execute function private.guard_payment_status();

-- ---------- kitchen item status ----------
-- VOID is reserved for removed lines (no screen sets it yet); it can never be left.
create or replace function private.prep_transition_ok(p_from text, p_to text) returns boolean
language sql immutable as $$
  select p_from = p_to or (p_from, p_to) in (
    ('PENDING','PREPARING'), ('PENDING','READY'),
    ('PREPARING','READY'), ('PREPARING','PENDING'),   -- undo "start cooking"
    ('READY','PREPARING'),                             -- undo "ready" (only while the order is still cooking)
    ('READY','SERVED'))
$$;

-- ---------- tables ----------
create or replace function private.table_transition_ok(p_from text, p_to text) returns boolean
language sql immutable as $$
  select p_from = p_to or (p_from, p_to) in (
    ('FREE','OCCUPIED'), ('FREE','RESERVED'),
    ('RESERVED','OCCUPIED'), ('RESERVED','FREE'),
    ('OCCUPIED','CLEANING'), ('OCCUPIED','FREE'),
    ('CLEANING','FREE'), ('CLEANING','OCCUPIED'), ('CLEANING','RESERVED'))
$$;

create or replace function private.table_has_open_bill(p_table text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.orders where table_number = p_table and order_status in ('PLACED','ACCEPTED','PREPARING','READY'))
$$;

create or replace function private.guard_table_status() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.status is distinct from old.status then
    if not private.table_transition_ok(old.status, new.status) then
      raise exception 'INVALID_TABLE_TRANSITION: % -> %', old.status, new.status using errcode = '22023';
    end if;
    if new.status <> 'OCCUPIED' and private.table_has_open_bill(new.name) then
      raise exception 'TABLE_HAS_OPEN_BILL: close or move the bill on % first', new.name using errcode = '22023';
    end if;
    -- a free table never keeps the previous guests' table code
    if new.status in ('FREE','CLEANING') then new.session_code := null; new.session_opened_at := null; end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_table_status on public.restaurant_tables;
create trigger trg_guard_table_status before update of status on public.restaurant_tables
  for each row execute function private.guard_table_status();

-- App dine-in orders now occupy / release their table too (POS bills already did)
create or replace function private.sync_table_from_order() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.table_number is not null and (tg_op = 'INSERT' or new.order_status is distinct from old.order_status) then
    perform private.refresh_table_status(new.table_number);
  end if;
  return new;
end $$;
drop trigger if exists trg_sync_table_from_order on public.orders;
create trigger trg_sync_table_from_order after insert or update of order_status on public.orders
  for each row execute function private.sync_table_from_order();

create or replace function public.close_table_session(p_table_id bigint) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare t public.restaurant_tables;
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  select * into t from public.restaurant_tables where id = p_table_id for update;
  if not found then raise exception 'TABLE_NOT_FOUND'; end if;
  update public.restaurant_tables set session_code = null, session_opened_at = null,
         status = case when status = 'OCCUPIED' and not private.table_has_open_bill(t.name) then 'CLEANING' else status end
   where id = p_table_id;
end $$;

-- ---------- reservations ----------
create or replace function private.reservation_transition_ok(p_from text, p_to text) returns boolean
language sql immutable as $$
  select p_from = p_to or (p_from, p_to) in (
    ('BOOKED','WAITING'), ('BOOKED','SEATED'), ('BOOKED','CANCELLED'), ('BOOKED','NO_SHOW'),
    ('WAITING','SEATED'), ('WAITING','CANCELLED'), ('WAITING','NO_SHOW'),
    ('SEATED','COMPLETED'))
$$;

create or replace function private.guard_reservation_status() returns trigger
language plpgsql as $$
begin
  if tg_op = 'INSERT' then
    if new.status not in ('BOOKED','WAITING') then raise exception 'INVALID_RESERVATION_STATUS' using errcode = '22023'; end if;
  elsif not private.reservation_transition_ok(old.status, new.status) then
    raise exception 'INVALID_RESERVATION_TRANSITION: % -> %', old.status, new.status using errcode = '22023';
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_reservation_status on public.reservations;
create trigger trg_guard_reservation_status before insert or update of status on public.reservations
  for each row execute function private.guard_reservation_status();

-- ---------- delivery code ----------
create or replace function public.unlock_delivery_code(p_order_id uuid) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders;
begin
  perform private.require_role('OWNER','MANAGER');
  select * into o from public.orders where id = p_order_id for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  update public.orders set delivery_otp_attempts = 0 where id = o.id;
  perform private.write_audit('UNLOCK_DELIVERY_CODE', 'orders', o.id::text,
    jsonb_build_object('attempts', o.delivery_otp_attempts), jsonb_build_object('attempts', 0));
  return private.order_json(o.id, true);
end $$;

-- ---------- "can a customer order now?" — one answer for every app ----------
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
    -- the same test place_order applies
    'canOrder', v_open and s.accepting_orders and (s.pickup_enabled or s.dine_in_enabled or s.delivery_enabled),
    'serverTime', now());
end $$;

create or replace function private.order_json(p_order_id uuid, p_staff_view boolean default false) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare o public.orders; s public.restaurant_settings; v jsonb; v_role text := private.my_role()::text;
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
      'paymentReference', o.payment_reference,
      'customerId', o.customer_id,
      'source', o.source,
      'paidPaise', private.order_paid(o.id),
      'assignedStaffId', o.assigned_staff_id,
      'assignedStaffName', (select coalesce(st.display_name, st.email) from public.staff st where st.user_id = o.assigned_staff_id),
      'assignedAt', o.assigned_at,
      'otpLocked', o.delivery_otp_attempts >= 5,
      -- what this staff member may do next (the screens show only these actions)
      'nextStatuses', to_jsonb(private.allowed_next(o, v_role)));
    -- Only the owner/manager may see the code (to help a customer whose phone is off); riders never see it
    if v_role in ('OWNER','MANAGER') then v := v || jsonb_build_object('deliveryOtp', o.delivery_otp); end if;
  else
    v := v || jsonb_build_object('deliveryAddress', o.delivery_address);
    -- The customer (no staff role) sees the code while the order is on its way to them
    if v_role is null and o.order_type = 'DELIVERY'
       and o.order_status in ('ACCEPTED','PREPARING','READY','OUT_FOR_DELIVERY') then
      v := v || jsonb_build_object('deliveryOtp', o.delivery_otp);
    end if;
  end if;
  return v;
end $$;

create or replace function public.pos_settle(p_order_id uuid) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders; v_actor text := coalesce(auth.jwt()->>'email','staff');
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  select * into o from public.orders where id = p_order_id;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if private.my_role()::text = 'WAITER' then raise exception 'NOT_AUTHORIZED' using errcode = '42501'; end if;
  -- App delivery orders are handed over with the customer's code (complete_order_with_payment)
  if o.source = 'APP' and o.order_type = 'DELIVERY' then raise exception 'USE_DELIVERY_COMPLETION' using errcode = '22023'; end if;
  if private.order_paid(o.id) < o.total_paise then raise exception 'PAYMENT_DUE: %', o.total_paise - private.order_paid(o.id) using errcode = '22023'; end if;
  if o.order_status = 'ACCEPTED' then perform private.apply_status(o.id, 'PREPARING', null, v_actor); end if;
  select * into o from public.orders where id = p_order_id;
  if o.order_status = 'PREPARING' then perform private.apply_status(o.id, 'READY', null, v_actor); end if;
  select * into o from public.orders where id = p_order_id;
  -- delivery bills always pass through "out for delivery" (same path as the transition table)
  if o.order_status = 'READY' and o.order_type = 'DELIVERY' then
    perform private.apply_status(o.id, 'OUT_FOR_DELIVERY', null, v_actor);
    select * into o from public.orders where id = p_order_id;
  end if;
  if o.order_status = 'OUT_FOR_DELIVERY' or o.order_status = 'READY' then perform private.apply_status(o.id, 'COMPLETED', null, v_actor); end if;
  if o.table_number is not null and not exists (select 1 from public.orders
       where table_number = o.table_number and order_status in ('ACCEPTED','PREPARING','READY') and id <> o.id) then
    update public.restaurant_tables set status = 'CLEANING' where name = o.table_number;
  end if;
  return private.order_json(o.id, true);
end $$;

create or replace function public.assign_delivery_staff(p_order_id uuid, p_staff_user_id uuid) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare r public.staff_role; o public.orders;
begin
  r := private.require_role('OWNER','MANAGER','STAFF');
  if r::text = 'WAITER' then raise exception 'NOT_AUTHORIZED' using errcode = '42501'; end if;
  select * into o from public.orders where id = p_order_id for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if o.order_type <> 'DELIVERY' or o.order_status not in ('ACCEPTED','PREPARING','READY','OUT_FOR_DELIVERY') then
    raise exception 'NOT_ASSIGNABLE' using errcode = '22023';
  end if;
  if p_staff_user_id is null and o.order_status = 'OUT_FOR_DELIVERY' then
    raise exception 'RIDER_REQUIRED: the order is on its way — choose another rider instead' using errcode = '22023';
  end if;
  if p_staff_user_id is not null and not exists (
       select 1 from public.staff where user_id = p_staff_user_id and role::text = 'DELIVERY' and is_active) then
    raise exception 'NOT_A_RIDER' using errcode = '22023';
  end if;
  update public.orders set assigned_staff_id = p_staff_user_id,
         assigned_at = case when p_staff_user_id is null then null else now() end where id = o.id;
  perform private.write_audit('ASSIGN_RIDER', 'orders', o.id::text, jsonb_build_object('rider', o.assigned_staff_id),
                              jsonb_build_object('rider', p_staff_user_id));
  return private.order_json(o.id, true);
end $$;

create or replace function public.kds_set_item_status(p_item_id bigint, p_status text) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare i public.order_items; o public.orders; v_actor text := coalesce(auth.jwt()->>'email','kitchen');
begin
  perform private.require_role('OWNER','MANAGER','STAFF','KITCHEN');
  if p_status not in ('PENDING','PREPARING','READY','SERVED') then raise exception 'INVALID_STATUS' using errcode = '22023'; end if;
  select * into i from public.order_items where id = p_item_id;
  if not found then raise exception 'ITEM_NOT_FOUND'; end if;
  select * into o from public.orders where id = i.order_id for update;
  if o.order_status not in ('ACCEPTED','PREPARING','READY') then raise exception 'ORDER_NOT_IN_KITCHEN' using errcode = '22023'; end if;
  if not private.prep_transition_ok(i.prep_status, p_status) then
    raise exception 'INVALID_PREP_TRANSITION: % -> %', i.prep_status, p_status using errcode = '22023';
  end if;
  -- Going back (undo) is only possible while the order is still cooking; a "Ready" order stays consistent
  if o.order_status = 'READY' and p_status in ('PENDING','PREPARING') then
    raise exception 'ORDER_ALREADY_READY: the whole order is already marked ready' using errcode = '22023';
  end if;
  update public.order_items set prep_status = p_status, prep_updated_at = now() where id = p_item_id;
  if o.order_status = 'ACCEPTED' and p_status in ('PREPARING','READY','SERVED') then
    perform private.apply_status(o.id, 'PREPARING', null, v_actor);
    o.order_status := 'PREPARING';
  end if;
  if o.order_status = 'PREPARING' and not exists (select 1 from public.order_items
       where order_id = o.id and prep_status in ('PENDING','PREPARING')) then
    perform private.apply_status(o.id, 'READY', null, v_actor);
  end if;
  return jsonb_build_object('ok', true);
end $$;
revoke execute on function public.unlock_delivery_code(uuid) from public, anon;
grant execute on function public.unlock_delivery_code(uuid) to authenticated;
