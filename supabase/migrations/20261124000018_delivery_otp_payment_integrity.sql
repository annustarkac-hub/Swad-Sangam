-- =====================================================================
-- Swad Sangam — delivery handover + payment integrity (live test feedback, 23 Nov 2026)
--  * Free in-app delivery code (4 digits) shown only to the customer; rider must enter it
--  * Orders cannot be COMPLETED while unpaid (owner/manager may override with an audited reason)
--  * Delivery riders (DELIVERY role) see only orders assigned to them, never the code
--  * Waiters cannot take payments or open/close the cash register
--  * Real payment tender (cash / UPI / bank) for app orders, refunds and corrections
--  * Fixes: review spam, loyalty points lost on cancelled bills, bill merge with large
--    discounts, negative payroll adjustments, slow dish-profit report
-- =====================================================================

alter table public.orders
  add column if not exists delivery_otp text check (delivery_otp ~ '^[0-9]{4}$'),
  add column if not exists delivery_otp_attempts int not null default 0,
  add column if not exists assigned_staff_id uuid references public.staff(user_id),
  add column if not exists assigned_at timestamptz;
create index if not exists orders_assigned_staff_idx on public.orders(assigned_staff_id) where assigned_staff_id is not null;

alter table public.order_adjustments add column if not exists tender public.tender;

-- ---------------------------------------------------------------------
-- Delivery code: generated once for app delivery orders when they are accepted
-- ---------------------------------------------------------------------
create or replace function private.set_delivery_otp() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.order_type = 'DELIVERY' and coalesce(new.source, 'APP') = 'APP' and new.delivery_otp is null
     and new.order_status in ('ACCEPTED','PREPARING','READY','OUT_FOR_DELIVERY') then
    new.delivery_otp := lpad((floor(random() * 10000))::int::text, 4, '0');
  end if;
  return new;
end $$;
drop trigger if exists trg_set_delivery_otp on public.orders;
create trigger trg_set_delivery_otp before insert or update of order_status on public.orders
  for each row execute function private.set_delivery_otp();

-- ---------------------------------------------------------------------
-- Order JSON: + delivery code (customer only), rider, customer id, amount paid
-- ---------------------------------------------------------------------
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
      'assignedAt', o.assigned_at);
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

-- ---------------------------------------------------------------------
-- Status changes: completion needs payment; delivery needs the handover code
-- ---------------------------------------------------------------------
create or replace function public.update_order_status(p_order_id uuid, p_status public.order_status,
                                                      p_reason text default null, p_eta_minutes int default null)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare r public.staff_role; o public.orders;
begin
  r := private.require_role('OWNER','MANAGER','STAFF','KITCHEN');
  -- Kitchen may only move food through preparation
  if r = 'KITCHEN' and p_status not in ('PREPARING','READY') then
    raise exception 'NOT_AUTHORIZED' using errcode = '42501';
  end if;
  if p_status = 'COMPLETED' then
    select * into o from public.orders where id = p_order_id;
    if not found then raise exception 'ORDER_NOT_FOUND'; end if;
    if o.order_type = 'DELIVERY' and o.source = 'APP' then
      raise exception 'USE_DELIVERY_COMPLETION: enter the customer''s delivery code and payment' using errcode = '22023';
    end if;
    if o.payment_status <> 'PAID' then
      if r::text in ('OWNER','MANAGER') and length(trim(coalesce(p_reason, ''))) >= 3 then
        perform private.write_audit('COMPLETED_UNPAID', 'orders', o.id::text,
          jsonb_build_object('paymentStatus', o.payment_status), jsonb_build_object('reason', trim(p_reason)));
      else
        raise exception 'PAYMENT_REQUIRED: collect % first', o.total_paise - private.order_paid(o.id) using errcode = '22023';
      end if;
    end if;
    perform private.apply_status(p_order_id, p_status, null, coalesce(auth.jwt()->>'email', 'staff'), p_eta_minutes);
  else
    perform private.apply_status(p_order_id, p_status, p_reason, coalesce(auth.jwt()->>'email', 'staff'), p_eta_minutes);
  end if;
  return private.order_json(p_order_id, r <> 'KITCHEN');
end $$;

/**
 * Hand over + collect payment in one step. For app delivery orders the rider must type the
 * customer's 4-digit code (5 wrong tries lock it; owner/manager can override with a reason).
 * Records the payment with the real tender (CASH/UPI/CARD/BANK), marks PAID, then COMPLETED.
 * Business errors are returned as {error, message} so failed code attempts are counted.
 */
create or replace function public.complete_order_with_payment(p_order_id uuid, p_tender public.tender default 'CASH',
    p_reference text default null, p_delivery_otp text default null, p_override_reason text default null)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare r public.staff_role; o public.orders; v_due bigint; v_actor text := coalesce(auth.jwt()->>'email', 'staff');
        v_override boolean := false;
begin
  r := private.require_role('OWNER','MANAGER','STAFF','DELIVERY');
  if r::text = 'WAITER' then raise exception 'NOT_AUTHORIZED' using errcode = '42501'; end if;
  select * into o from public.orders where id = p_order_id for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if r::text = 'DELIVERY' and o.assigned_staff_id is distinct from auth.uid() then
    raise exception 'NOT_AUTHORIZED' using errcode = '42501';
  end if;
  if p_tender = 'CREDIT' then return private.err('INVALID_TENDER', 'Use Cash, UPI, Card or Bank. Credit (khata) bills are closed by the owner.'); end if;

  if o.order_type = 'DELIVERY' and o.source = 'APP' then
    if o.order_status <> 'OUT_FOR_DELIVERY' then
      return private.err('NOT_OUT_FOR_DELIVERY', 'Start the delivery first (status: Out for delivery).');
    end if;
    v_override := r::text in ('OWNER','MANAGER') and length(trim(coalesce(p_override_reason, ''))) >= 3;
    if not v_override then
      if o.delivery_otp_attempts >= 5 then
        return private.err('OTP_LOCKED', 'Too many wrong codes. Ask the owner or manager to complete this order.');
      end if;
      if o.delivery_otp is null or trim(coalesce(p_delivery_otp, '')) <> o.delivery_otp then
        update public.orders set delivery_otp_attempts = delivery_otp_attempts + 1 where id = o.id;
        return private.err('WRONG_OTP', 'Wrong delivery code. Ask the customer for the 4-digit code in their app.',
                           jsonb_build_object('attemptsLeft', greatest(0, 4 - o.delivery_otp_attempts)));
      end if;
    end if;
  elsif o.order_status <> 'READY' then
    return private.err('NOT_READY', 'Only ready orders can be handed over.');
  end if;

  v_due := o.total_paise - private.order_paid(o.id);
  if v_due > 0 then
    perform private.insert_payment(o.id, p_tender, v_due, nullif(left(trim(coalesce(p_reference, '')), 100), ''),
                                   null, gen_random_uuid(), 1);
  end if;
  update public.orders set payment_status = 'PAID',
         payment_reference = coalesce(nullif(left(trim(coalesce(p_reference, '')), 100), ''), payment_reference)
   where id = o.id and payment_status in ('PENDING','FAILED');
  perform private.apply_status(o.id, 'COMPLETED', null, v_actor);
  perform private.write_audit('ORDER_HANDOVER', 'orders', o.id::text, jsonb_build_object('paymentStatus', o.payment_status),
    jsonb_build_object('tender', p_tender, 'collectedPaise', greatest(v_due, 0), 'otpVerified', not v_override and o.order_type = 'DELIVERY',
                       'overrideReason', case when v_override then trim(p_override_reason) end));
  return jsonb_build_object('ok', true, 'collectedPaise', greatest(v_due, 0), 'order', private.order_json(o.id, r::text <> 'DELIVERY'));
end $$;

-- ---------------------------------------------------------------------
-- Riders
-- ---------------------------------------------------------------------
create or replace function public.list_delivery_staff() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  return coalesce((select jsonb_agg(jsonb_build_object('userId', s.user_id, 'name', coalesce(nullif(s.display_name, ''), s.email),
                     'activeOrders', (select count(*) from public.orders o where o.assigned_staff_id = s.user_id
                                        and o.order_status in ('ACCEPTED','PREPARING','READY','OUT_FOR_DELIVERY')))
                     order by s.display_name)
                     from public.staff s where s.role::text = 'DELIVERY' and s.is_active), '[]'::jsonb);
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

/** The rider's own list: assigned orders on the way, plus today's delivered ones. Never includes the code. */
create or replace function public.delivery_my_orders() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_role('DELIVERY');
  return coalesce((select jsonb_agg(jsonb_build_object(
      'id', o.id, 'orderNumber', o.order_number, 'orderStatus', o.order_status, 'customerName', o.customer_name,
      'customerPhone', o.customer_phone, 'deliveryAddress', o.delivery_address, 'deliveryLandmark', o.delivery_landmark,
      'notes', o.notes, 'totalPaise', o.total_paise, 'paidPaise', private.order_paid(o.id),
      'duePaise', greatest(0, o.total_paise - private.order_paid(o.id)), 'paymentStatus', o.payment_status,
      'paymentMethod', o.payment_method, 'assignedAt', o.assigned_at, 'readyAt', o.ready_at, 'completedAt', o.completed_at,
      'otpLocked', o.delivery_otp_attempts >= 5,
      'items', coalesce((select jsonb_agg(jsonb_build_object('name', i.name_snapshot, 'quantity', i.quantity) order by i.id)
                           from public.order_items i where i.order_id = o.id), '[]'::jsonb))
      order by (o.order_status = 'COMPLETED'), o.assigned_at)
    from public.orders o
   where o.assigned_staff_id = auth.uid()
     and (o.order_status in ('ACCEPTED','PREPARING','READY','OUT_FOR_DELIVERY')
          or (o.order_status = 'COMPLETED' and o.completed_at > now() - interval '18 hours'))), '[]'::jsonb);
end $$;

create or replace function public.delivery_start(p_order_id uuid) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare r public.staff_role; o public.orders;
begin
  r := private.require_role('OWNER','MANAGER','STAFF','DELIVERY');
  if r::text = 'WAITER' then raise exception 'NOT_AUTHORIZED' using errcode = '42501'; end if;
  select * into o from public.orders where id = p_order_id;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if r::text = 'DELIVERY' and o.assigned_staff_id is distinct from auth.uid() then
    raise exception 'NOT_AUTHORIZED' using errcode = '42501';
  end if;
  perform private.apply_status(o.id, 'OUT_FOR_DELIVERY', null, coalesce(auth.jwt()->>'email', 'rider'));
  return jsonb_build_object('ok', true);
end $$;

-- ---------------------------------------------------------------------
-- Payments: waiters blocked; real tender recorded
-- ---------------------------------------------------------------------
drop function if exists public.update_payment_status(uuid, public.payment_status, text);
create or replace function public.update_payment_status(p_order_id uuid, p_status public.payment_status,
                                                        p_reference text default null, p_tender public.tender default null)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders; v_due bigint; v_tender public.tender;
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  if private.my_role()::text = 'WAITER' then raise exception 'NOT_AUTHORIZED' using errcode = '42501'; end if;
  select * into o from public.orders where id = p_order_id for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if not ((o.payment_status, p_status) in (
      ('PENDING'::public.payment_status, 'PAID'::public.payment_status), ('PENDING','FAILED'),
      ('FAILED','PAID'), ('PAID','REFUND_PENDING'), ('REFUND_PENDING','REFUNDED'))) then
    raise exception 'INVALID_PAYMENT_TRANSITION: % -> %', o.payment_status, p_status using errcode = '22023';
  end if;
  if p_tender = 'CREDIT' then raise exception 'INVALID_TENDER' using errcode = '22023'; end if;
  if p_status = 'PAID' then
    v_tender := coalesce(p_tender, case when o.payment_method = 'UPI_AT_RESTAURANT' then 'UPI'::public.tender else 'CASH'::public.tender end);
    v_due := o.total_paise - private.order_paid(o.id);
    if v_due > 0 then
      perform private.insert_payment(o.id, v_tender, v_due, p_reference, null, gen_random_uuid(), 1);
    end if;
  end if;
  update public.orders set payment_status = p_status,
         payment_reference = coalesce(nullif(left(trim(coalesce(p_reference, '')), 100), ''), payment_reference)
   where id = p_order_id;
  perform private.write_audit('PAYMENT_STATUS', 'orders', p_order_id::text,
    jsonb_build_object('paymentStatus', o.payment_status),
    jsonb_build_object('paymentStatus', p_status, 'reference', p_reference, 'tender', coalesce(v_tender, p_tender)));
  return private.order_json(p_order_id, true);
end $$;

drop function if exists public.record_order_adjustment(uuid, public.adjustment_type, int, text);
create or replace function public.record_order_adjustment(p_order_id uuid, p_type public.adjustment_type,
                                                          p_amount_paise int, p_reason text, p_tender public.tender default 'CASH')
returns void language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders; v_refunded bigint;
begin
  perform private.require_role('OWNER','MANAGER');
  select * into o from public.orders where id = p_order_id for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if p_tender = 'CREDIT' then raise exception 'INVALID_TENDER' using errcode = '22023'; end if;
  if p_type = 'REFUND' then
    if p_amount_paise <= 0 then raise exception 'INVALID_AMOUNT' using errcode = '22023'; end if;
    select coalesce(sum(amount_paise), 0) into v_refunded from public.order_adjustments where order_id = o.id and type = 'REFUND';
    if v_refunded + p_amount_paise > private.order_paid(o.id) then
      raise exception 'REFUND_MORE_THAN_PAID: paid %, already refunded %', private.order_paid(o.id), v_refunded using errcode = '22023';
    end if;
  end if;
  insert into public.order_adjustments(order_id, type, amount_paise, reason, actor_id, tender)
  values (p_order_id, p_type, p_amount_paise, p_reason, auth.uid(), case when p_type = 'REFUND' then p_tender end);
  if p_type = 'REFUND' then
    update public.orders o2 set payment_status = 'REFUNDED'
     where o2.id = p_order_id and o2.payment_status in ('PAID', 'REFUND_PENDING')
       and (select coalesce(sum(a.amount_paise), 0) from public.order_adjustments a
             where a.order_id = o2.id and a.type = 'REFUND') >= o2.total_paise;
  end if;
end $$;

-- Refunds leave the drawer/bank they were actually paid from
create or replace function private.on_order_adjustment() returns trigger
language plpgsql security definer set search_path = '' as $$
declare o public.orders; v_amt bigint := abs(new.amount_paise);
begin
  select * into o from public.orders where id = new.order_id;
  if new.type = 'REFUND' then
    perform private.post_journal(null, 'Refund ' || o.order_number || ': ' || new.reason, 'REFUND', new.id::text,
      jsonb_build_array(
        jsonb_build_object('account', case when o.order_status = 'COMPLETED' then '5910' else '1150' end, 'debit', v_amt),
        jsonb_build_object('account', private.tender_account(coalesce(new.tender, 'CASH')), 'credit', v_amt)));
  elsif new.amount_paise > 0 then
    perform private.post_journal(null, 'Correction (extra charge) ' || o.order_number || ': ' || new.reason, 'CORRECTION', new.id::text,
      jsonb_build_array(jsonb_build_object('account','1150','debit',v_amt), jsonb_build_object('account','4000','credit',v_amt)));
  else
    perform private.post_journal(null, 'Correction (reduction) ' || o.order_number || ': ' || new.reason, 'CORRECTION', new.id::text,
      jsonb_build_array(jsonb_build_object('account','5910','debit',v_amt), jsonb_build_object('account','1150','credit',v_amt)));
  end if;
  return new;
end $$;

-- Cash register: owner, manager, cashier, accountant (not waiters, not riders)
create or replace function private.require_cash_role() returns void
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','STAFF','ACCOUNTANT');
  if private.my_role()::text = 'WAITER' then raise exception 'NOT_AUTHORIZED' using errcode = '42501'; end if;
end $$;

create or replace function public.open_cash_session(p_opening_paise bigint) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v_id bigint;
begin
  perform private.require_cash_role();
  if exists (select 1 from public.cash_sessions where closed_at is null) then raise exception 'SESSION_ALREADY_OPEN' using errcode = '22023'; end if;
  insert into public.cash_sessions(opened_by, opening_cash_paise) values (auth.uid(), greatest(0, p_opening_paise)) returning id into v_id;
  return jsonb_build_object('id', v_id);
end $$;

create or replace function public.cash_session_status() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare c public.cash_sessions;
begin
  perform private.require_cash_role();
  select * into c from public.cash_sessions where closed_at is null;
  if not found then
    return jsonb_build_object('open', false, 'last', (select to_jsonb(x) from public.cash_sessions x order by id desc limit 1));
  end if;
  return jsonb_build_object('open', true, 'id', c.id, 'openedAt', c.opened_at, 'openingPaise', c.opening_cash_paise,
    'cashInPaise', private.cash_movement_since(c.opened_at),
    'expectedPaise', c.opening_cash_paise + private.cash_movement_since(c.opened_at));
end $$;

create or replace function public.close_cash_session(p_counted_paise bigint, p_notes text default null) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare c public.cash_sessions; v_exp bigint; v_diff bigint;
begin
  perform private.require_cash_role();
  select * into c from public.cash_sessions where closed_at is null for update;
  if not found then raise exception 'NO_OPEN_SESSION' using errcode = '22023'; end if;
  v_exp := c.opening_cash_paise + private.cash_movement_since(c.opened_at);
  v_diff := p_counted_paise - v_exp;
  update public.cash_sessions set closed_by = auth.uid(), closed_at = now(), counted_cash_paise = p_counted_paise,
         expected_cash_paise = v_exp, difference_paise = v_diff, notes = p_notes where id = c.id;
  if v_diff <> 0 then
    perform private.post_journal(null, case when v_diff < 0 then 'Cash shortage' else 'Cash excess' end || ' — register session ' || c.id,
      'CASH_SESSION', c.id::text,
      case when v_diff < 0
        then jsonb_build_array(jsonb_build_object('account','5950','debit',-v_diff), jsonb_build_object('account','1000','credit',-v_diff))
        else jsonb_build_array(jsonb_build_object('account','1000','debit',v_diff), jsonb_build_object('account','5950','credit',v_diff)) end);
  end if;
  return jsonb_build_object('expectedPaise', v_exp, 'countedPaise', p_counted_paise, 'differencePaise', v_diff);
end $$;

-- ---------------------------------------------------------------------
-- Reviews: only feedback linked to a real completed order counts / is published
-- ---------------------------------------------------------------------
create or replace function private.feedback_order_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.order_id is not null and not exists (
       select 1 from public.orders where id = new.order_id and order_status = 'COMPLETED') then
    raise exception 'ORDER_NOT_COMPLETED' using errcode = 'check_violation';
  end if;
  if new.order_id is null then new.consent_to_publish := false; end if;
  return new;
end $$;

create or replace function public.public_reviews() returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'count', (select count(*) from public.feedback where order_id is not null),
    'average', (select round(avg(overall)::numeric, 1) from public.feedback where order_id is not null),
    'recent', coalesce((select jsonb_agg(jsonb_build_object('overall', f.overall, 'comments', f.comments, 'at', f.created_at)
                                         order by f.created_at desc)
                          from (select * from public.feedback
                                 where consent_to_publish and comments is not null and order_id is not null
                                 order by created_at desc limit 10) f), '[]'::jsonb))
$$;

-- ---------------------------------------------------------------------
-- Loyalty: points redeemed on a bill come back if that bill is cancelled
-- (not when it was merged into another bill — the discount moved with it)
-- ---------------------------------------------------------------------
create or replace function private.on_order_cancelled_loyalty() returns trigger
language plpgsql security definer set search_path = '' as $$
declare l record;
begin
  if new.order_status = 'CANCELLED' and old.order_status is distinct from 'CANCELLED'
     and coalesce(new.cancel_reason, '') not like 'Merged into %' then
    for l in select * from public.loyalty_ledger where order_id = new.id and reason = 'REDEEM' loop
      insert into public.loyalty_ledger(customer_id, order_id, points, reason, created_by)
      values (l.customer_id, new.id, -l.points, 'ADJUST', auth.uid());
      update public.customers set loyalty_points = loyalty_points - l.points where id = l.customer_id;
    end loop;
  end if;
  return new;
end $$;
drop trigger if exists trg_order_cancelled_loyalty on public.orders;
create trigger trg_order_cancelled_loyalty after update of order_status on public.orders
  for each row execute function private.on_order_cancelled_loyalty();

-- ---------------------------------------------------------------------
-- Merge bills: lock both (fixed order), never let a total dip below zero
-- ---------------------------------------------------------------------
create or replace function public.pos_merge_bills(p_target uuid, p_source uuid) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare t public.orders; s public.orders; v_sub bigint;
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  if p_target = p_source then raise exception 'SAME_BILL' using errcode = '22023'; end if;
  perform 1 from public.orders where id in (p_target, p_source) order by id for update;
  t := private.open_pos_bill(p_target);
  s := private.open_pos_bill(p_source);
  perform set_config('app.pos_edit', 'on', true);
  update public.order_items set order_id = t.id where order_id = s.id;
  -- move the discount with the lines in one step so totals never go negative; tax/fees recomputed right after
  select coalesce(sum(line_total_paise), 0) into v_sub from public.order_items where order_id = t.id;
  update public.orders set subtotal_paise = v_sub, discount_paise = least(t.discount_paise + s.discount_paise, v_sub),
         total_paise = v_sub + tax_paise + packaging_fee_paise + delivery_fee_paise - least(t.discount_paise + s.discount_paise, v_sub)
   where id = t.id;
  update public.orders set subtotal_paise = 0, discount_paise = 0,
         total_paise = tax_paise + packaging_fee_paise + delivery_fee_paise where id = s.id;
  perform private.pos_totals(t.id);
  perform private.pos_totals(s.id);
  update public.orders set order_status = 'CANCELLED', cancel_reason = 'Merged into ' || t.order_number,
         cancelled_by = 'RESTAURANT', closed_at = now(), payment_status = 'FAILED' where id = s.id;
  insert into public.order_status_history(order_id, from_status, to_status, actor_id, actor_label, reason)
  values (s.id, s.order_status, 'CANCELLED', auth.uid(), coalesce(auth.jwt()->>'email','staff'), 'Merged into ' || t.order_number);
  if s.table_number is not null then perform private.refresh_table_status(s.table_number); end if;
  return private.order_json(t.id, true);
end $$;

-- payroll: bonus / deductions can never be negative
create or replace function public.payroll_preview(p_month date, p_adjust jsonb default '{}'::jsonb) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_m date := date_trunc('month', p_month)::date; v_days int; e record; v_out jsonb := '[]'::jsonb;
        v_absent numeric; v_pay numeric; v_gross bigint; v_ot bigint; v_adv_due bigint; v_adv bigint; v_bonus bigint; v_ded bigint; a jsonb;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  v_days := extract(day from (v_m + interval '1 month - 1 day'))::int;
  for e in select * from public.employees where is_active order by name loop
    select coalesce(sum(case status when 'ABSENT' then 1 when 'LEAVE' then 1 when 'HALF_DAY' then 0.5 else 0 end), 0),
           coalesce(sum(overtime_hours), 0)
      into v_absent, v_pay
      from public.attendance where employee_id = e.id and day >= v_m and day < v_m + interval '1 month';
    a := coalesce(p_adjust->(e.id::text), '{}'::jsonb);
    -- hourly overtime rate = monthly / (days × 8 h)
    v_ot := round(v_pay * e.monthly_salary_paise::numeric / (v_days * 8))::bigint;
    v_gross := round(e.monthly_salary_paise::numeric * (v_days - v_absent) / v_days)::bigint;
    v_adv_due := private.party_balance('1400','EMPLOYEE', e.id);
    v_bonus := greatest(0, coalesce((a->>'bonusPaise')::bigint, 0));
    v_ded := greatest(0, coalesce((a->>'otherDeductionPaise')::bigint, 0));
    v_adv := least(greatest(v_adv_due, 0), coalesce((a->>'advanceRecoveryPaise')::bigint, v_adv_due), v_gross + v_ot + v_bonus - v_ded);
    v_out := v_out || jsonb_build_array(jsonb_build_object(
      'employeeId', e.id, 'name', e.name, 'designation', e.designation, 'monthlyPaise', e.monthly_salary_paise,
      'daysInMonth', v_days, 'payableDays', v_days - v_absent, 'grossPaise', v_gross, 'overtimePaise', v_ot,
      'bonusPaise', v_bonus, 'advanceOutstandingPaise', v_adv_due, 'advanceRecoveredPaise', greatest(v_adv, 0),
      'otherDeductionPaise', v_ded, 'netPaise', v_gross + v_ot + v_bonus - v_ded - greatest(v_adv, 0),
      'alreadyPaid', exists (select 1 from public.payroll_lines where month = v_m and employee_id = e.id)));
  end loop;
  return v_out;
end $$;

-- POS settle: waiters cannot close bills; app delivery orders need the delivery code
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
  if o.order_status = 'OUT_FOR_DELIVERY' or o.order_status = 'READY' then perform private.apply_status(o.id, 'COMPLETED', null, v_actor); end if;
  if o.table_number is not null and not exists (select 1 from public.orders
       where table_number = o.table_number and order_status in ('ACCEPTED','PREPARING','READY') and id <> o.id) then
    update public.restaurant_tables set status = 'CLEANING' where name = o.table_number;
  end if;
  return private.order_json(o.id, true);
end $$;

-- Reports: recipe costs computed once per call (was once per sold line)
create or replace function public.business_insights(p_from date, p_to date) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_tz text; v_fixed bigint; v_sales bigint; v_cogs bigint;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  select timezone into v_tz from public.restaurant_settings where id = 1;
  select coalesce(sum(subtotal_paise - discount_paise),0) into v_sales from public.orders
   where order_status = 'COMPLETED' and (completed_at at time zone v_tz)::date between p_from and p_to;
  select coalesce(sum(l.debit_paise - l.credit_paise),0) into v_cogs from public.journal_lines l
    join public.journal_entries e on e.id = l.entry_id where l.account_id = private.acct('5000') and e.entry_date between p_from and p_to;
  select coalesce(sum(l.debit_paise - l.credit_paise),0) into v_fixed from public.journal_lines l
    join public.journal_entries e on e.id = l.entry_id join public.accounts a on a.id = l.account_id
   where a.type = 'EXPENSE' and a.code not in ('5000','5850','5860','5900','5910','5950') and e.entry_date between p_from and p_to;
  return jsonb_build_object(
    'hourly', coalesce((select jsonb_agg(jsonb_build_object('hour', h, 'orders', n, 'salesPaise', s) order by h) from (
        select extract(hour from completed_at at time zone v_tz)::int h, count(*) n, sum(subtotal_paise - discount_paise) s
          from public.orders where order_status = 'COMPLETED' and (completed_at at time zone v_tz)::date between p_from and p_to
         group by 1) x), '[]'),
    'dishes', coalesce((select jsonb_agg(jsonb_build_object('name', name, 'qty', qty, 'revenuePaise', rev,
                 'estimatedCostPaise', cost, 'estimatedProfitPaise', rev - cost) order by rev - cost desc) from (
        select i.name_snapshot name, sum(i.quantity) qty, sum(i.line_total_paise) rev,
               sum(i.quantity * coalesce(rc.cost, 0)) cost
          from public.order_items i join public.orders o on o.id = i.order_id
          left join (select c->>'itemId' item_id, (c->>'recipeCostPaise')::bigint cost
                       from jsonb_array_elements(public.menu_costing()) c) rc on rc.item_id = i.item_id
         where o.order_status = 'COMPLETED' and (o.completed_at at time zone v_tz)::date between p_from and p_to
         group by i.name_snapshot, i.item_id) d), '[]'),
    'wastage', coalesce((select jsonb_agg(jsonb_build_object('name', si.name, 'qty', -sum_q, 'unit', si.unit, 'valuePaise', -sum_v) order by sum_v) from (
        select item_id, sum(qty) sum_q, sum(value_paise) sum_v from public.stock_movements
         where type = 'WASTAGE' and (created_at at time zone v_tz)::date between p_from and p_to group by item_id) w
        join public.stock_items si on si.id = w.item_id), '[]'),
    'supplierRates', coalesce((select jsonb_agg(jsonb_build_object('item', si.name, 'unit', si.unit, 'supplier', s.name,
                 'avgRatePaise', round(r.avg_rate), 'lastPurchase', r.last_date) order by si.name, r.avg_rate) from (
        select pl.stock_item_id, pi.supplier_id, avg(pl.amount_paise / nullif(pl.qty,0)) avg_rate, max(pi.invoice_date) last_date
          from public.purchase_lines pl join public.purchase_invoices pi on pi.id = pl.invoice_id
         where pl.stock_item_id is not null group by 1, 2) r
        join public.stock_items si on si.id = r.stock_item_id join public.suppliers s on s.id = r.supplier_id), '[]'),
    'salesPaise', v_sales, 'foodCostPaise', v_cogs, 'operatingExpensesPaise', v_fixed,
    'foodCostPercent', case when v_sales > 0 then round(100.0 * v_cogs / v_sales, 1) end,
    'breakEvenSalesPaise', case when v_sales > v_cogs and v_sales > 0 then round(v_fixed::numeric / ((v_sales - v_cogs)::numeric / v_sales)) end,
    'note', 'Dish cost uses current recipe cost; break-even = operating expenses ÷ gross-margin ratio for the period. Estimates, not audited figures.');
end $$;

-- ---------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------
do $$
declare f text;
begin
  foreach f in array array['complete_order_with_payment(uuid,public.tender,text,text,text)','list_delivery_staff()',
    'assign_delivery_staff(uuid,uuid)','delivery_my_orders()','delivery_start(uuid)',
    'update_payment_status(uuid,public.payment_status,text,public.tender)',
    'record_order_adjustment(uuid,public.adjustment_type,integer,text,public.tender)'] loop
    execute format('revoke execute on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;
revoke execute on function private.require_cash_role() from public, anon;
grant execute on function private.require_cash_role() to authenticated;
