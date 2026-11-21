-- =====================================================================
-- Swad Sangam ERP — POS billing, split/partial payments, receipts,
-- automatic sales/refund postings, cash register sessions, tables,
-- reservations.
-- =====================================================================

alter table public.orders
  add column if not exists source text not null default 'APP' check (source in ('APP','POS')),
  add column if not exists customer_id bigint references public.customers(id),
  add column if not exists created_by uuid,
  add column if not exists tax_included_paise bigint not null default 0,
  add column if not exists receipt_print_count int not null default 0;

-- Open POS bills may be edited (items added) until paid/closed; app orders and
-- closed bills stay immutable.
create or replace function private.protect_order_money() returns trigger
language plpgsql as $$
begin
  if (new.subtotal_paise, new.tax_paise, new.packaging_fee_paise, new.delivery_fee_paise,
      new.discount_paise, new.total_paise, new.order_number, new.idempotency_key)
     is distinct from
     (old.subtotal_paise, old.tax_paise, old.packaging_fee_paise, old.delivery_fee_paise,
      old.discount_paise, old.total_paise, old.order_number, old.idempotency_key) then
    if not (coalesce(current_setting('app.pos_edit', true), '') = 'on' and old.source = 'POS'
            and old.order_status in ('ACCEPTED','PREPARING','READY') and old.payment_status = 'PENDING'
            and new.order_number = old.order_number and new.idempotency_key = old.idempotency_key) then
      raise exception 'ORDER_AMOUNTS_IMMUTABLE: use an order adjustment (refund/correction) instead';
    end if;
  end if;
  return new;
end $$;

-- ---------------------------------------------------------------------
-- Payments (split, partial, credit/khata)
-- ---------------------------------------------------------------------
create table public.cash_sessions (
  id                 bigint generated always as identity primary key,
  opened_by          uuid,
  opened_at          timestamptz not null default now(),
  opening_cash_paise bigint not null check (opening_cash_paise >= 0),
  closed_by          uuid,
  closed_at          timestamptz,
  counted_cash_paise bigint,
  expected_cash_paise bigint,
  difference_paise   bigint,
  notes              text
);
create unique index one_open_cash_session on public.cash_sessions((true)) where closed_at is null;

create table public.order_payments (
  id            bigint generated always as identity primary key,
  order_id      uuid not null references public.orders(id),
  tender        public.tender not null,
  amount_paise  bigint not null check (amount_paise > 0),
  reference     text,
  customer_id   bigint references public.customers(id),
  client_ref    uuid,
  line_no       int not null default 1,
  cash_session_id bigint references public.cash_sessions(id),
  received_by   uuid,
  journal_entry_id bigint references public.journal_entries(id),
  created_at    timestamptz not null default now(),
  unique (client_ref, line_no),
  check (tender <> 'CREDIT' or customer_id is not null)
);
create index order_payments_order_idx on public.order_payments(order_id);
-- Payments are immutable; the only allowed change is linking the journal entry once.
create or replace function private.order_payment_guard() returns trigger
language plpgsql as $$
begin
  if tg_op = 'DELETE' or old.journal_entry_id is not null
     or (to_jsonb(new) - 'journal_entry_id') <> (to_jsonb(old) - 'journal_entry_id') then
    raise exception 'order_payments rows are immutable';
  end if;
  return new;
end $$;
create trigger trg_order_payments_immutable before update or delete on public.order_payments
  for each row execute function private.order_payment_guard();

create or replace function private.order_paid(p_order uuid) returns bigint
language sql stable security definer set search_path = '' as $$
  select coalesce(sum(amount_paise), 0)::bigint from public.order_payments where order_id = p_order
$$;

create or replace function private.insert_payment(p_order uuid, p_tender public.tender, p_amount bigint, p_ref text,
    p_customer bigint, p_client_ref uuid, p_line int) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare v_id bigint; v_je bigint; o public.orders;
begin
  select * into o from public.orders where id = p_order;
  insert into public.order_payments(order_id, tender, amount_paise, reference, customer_id, client_ref, line_no, cash_session_id, received_by)
  values (p_order, p_tender, p_amount, p_ref, p_customer, p_client_ref, p_line,
          (select id from public.cash_sessions where closed_at is null), auth.uid())
  returning id into v_id;
  v_je := private.post_journal(null, 'Payment for ' || o.order_number || ' (' || p_tender || ')', 'PAYMENT', v_id::text,
    jsonb_build_array(
      case when p_tender = 'CREDIT'
        then jsonb_build_object('account','1100','debit',p_amount,'partyType','CUSTOMER','partyId',p_customer)
        else jsonb_build_object('account', private.tender_account(p_tender), 'debit', p_amount) end,
      jsonb_build_object('account','1150','credit',p_amount)));
  update public.order_payments set journal_entry_id = v_je where id = v_id;
end $$;

/**
 * Records one or more payments (split payment) against a bill. Idempotent per clientRef.
 * p_payments: [{"tender":"CASH","amountPaise":5000,"reference":"","customerId":null}]
 */
create or replace function public.record_order_payment(p_order_id uuid, p_payments jsonb, p_client_ref uuid)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders; v_paid bigint; v_sum bigint := 0; x jsonb; i int := 0; c public.customers; v_bal bigint;
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  if private.my_role()::text = 'WAITER' then raise exception 'NOT_AUTHORIZED' using errcode = '42501'; end if;
  if exists (select 1 from public.order_payments where client_ref = p_client_ref) then
    return jsonb_build_object('ok', true, 'duplicate', true, 'paidPaise', private.order_paid(p_order_id));
  end if;
  select * into o from public.orders where id = p_order_id for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if o.order_status in ('REJECTED','CANCELLED') then raise exception 'ORDER_CLOSED' using errcode = '22023'; end if;
  v_paid := private.order_paid(p_order_id);
  for x in select * from jsonb_array_elements(p_payments) loop
    if coalesce((x->>'amountPaise')::bigint, 0) <= 0 then raise exception 'INVALID_AMOUNT' using errcode = '22023'; end if;
    v_sum := v_sum + (x->>'amountPaise')::bigint;
  end loop;
  if v_sum = 0 then raise exception 'INVALID_AMOUNT' using errcode = '22023'; end if;
  if v_paid + v_sum > o.total_paise then raise exception 'OVERPAYMENT: due %', o.total_paise - v_paid using errcode = '22023'; end if;

  for x in select * from jsonb_array_elements(p_payments) loop
    i := i + 1;
    if x->>'tender' = 'CREDIT' then
      select * into c from public.customers where id = (x->>'customerId')::bigint and is_active;
      if not found then raise exception 'CUSTOMER_REQUIRED_FOR_CREDIT' using errcode = '22023'; end if;
      v_bal := private.party_balance('1100','CUSTOMER',c.id);
      if c.credit_limit_paise = 0 or v_bal + (x->>'amountPaise')::bigint > c.credit_limit_paise then
        raise exception 'CREDIT_LIMIT_EXCEEDED: outstanding %, limit %', v_bal, c.credit_limit_paise using errcode = '22023';
      end if;
      update public.orders set customer_id = c.id where id = o.id and customer_id is null;
    end if;
    perform private.insert_payment(o.id, (x->>'tender')::public.tender, (x->>'amountPaise')::bigint, x->>'reference',
                                   (x->>'customerId')::bigint, p_client_ref, i);
  end loop;

  if v_paid + v_sum >= o.total_paise then
    update public.orders set payment_status = 'PAID' where id = o.id and payment_status in ('PENDING','FAILED');
  end if;
  return jsonb_build_object('ok', true, 'paidPaise', v_paid + v_sum, 'duePaise', o.total_paise - v_paid - v_sum);
end $$;

-- App orders marked paid from the dashboard also create the payment + journal.
create or replace function public.update_payment_status(p_order_id uuid, p_status public.payment_status,
                                                        p_reference text default null)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders; v_due bigint;
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  select * into o from public.orders where id = p_order_id for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if not ((o.payment_status, p_status) in (
      ('PENDING'::public.payment_status, 'PAID'::public.payment_status), ('PENDING','FAILED'),
      ('FAILED','PAID'), ('PAID','REFUND_PENDING'), ('REFUND_PENDING','REFUNDED'))) then
    raise exception 'INVALID_PAYMENT_TRANSITION: % -> %', o.payment_status, p_status using errcode = '22023';
  end if;
  if p_status = 'PAID' then
    v_due := o.total_paise - private.order_paid(o.id);
    if v_due > 0 then
      perform private.insert_payment(o.id, case when o.payment_method = 'UPI_AT_RESTAURANT' then 'UPI'::public.tender else 'CASH'::public.tender end,
                                     v_due, p_reference, null, gen_random_uuid(), 1);
    end if;
  end if;
  update public.orders set payment_status = p_status,
         payment_reference = coalesce(nullif(left(trim(coalesce(p_reference, '')), 100), ''), payment_reference)
   where id = p_order_id;
  perform private.write_audit('PAYMENT_STATUS', 'orders', p_order_id::text,
    jsonb_build_object('paymentStatus', o.payment_status), jsonb_build_object('paymentStatus', p_status, 'reference', p_reference));
  return private.order_json(p_order_id, true);
end $$;

-- ---------------------------------------------------------------------
-- Sales posting + recipe stock deduction when an order is completed
-- GST: CGST/SGST split equally (intra-state). Tax included in prices is
-- separated out so the books show net sales.
-- ---------------------------------------------------------------------
create or replace function private.post_sale(p_order uuid) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders; v_food bigint; v_incl bigint := 0; v_tax bigint; v_cgst bigint;
begin
  select * into o from public.orders where id = p_order;
  v_food := o.subtotal_paise - o.discount_paise;
  if o.prices_include_tax and o.tax_rate_bps > 0 then
    v_incl := v_food - round(v_food::numeric * 10000 / (10000 + o.tax_rate_bps))::bigint;
    update public.orders set tax_included_paise = v_incl where id = p_order;
  end if;
  v_tax := o.tax_paise + v_incl;
  v_cgst := v_tax / 2;
  perform private.post_journal((o.completed_at at time zone 'Asia/Kolkata')::date, 'Sale ' || o.order_number, 'SALE', o.id::text,
    jsonb_build_array(
      jsonb_build_object('account','1150','debit', o.total_paise),
      jsonb_build_object('account','5900','debit', o.discount_paise),
      jsonb_build_object('account','4000','credit', o.subtotal_paise - v_incl),
      jsonb_build_object('account','2100','credit', v_cgst),
      jsonb_build_object('account','2101','credit', v_tax - v_cgst),
      jsonb_build_object('account','4010','credit', o.packaging_fee_paise + o.delivery_fee_paise)));
end $$;

create or replace function private.on_order_completed() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.order_status = 'COMPLETED' and old.order_status is distinct from 'COMPLETED' then
    perform private.post_sale(new.id);
    perform private.consume_stock(new.id);
  end if;
  return new;
end $$;
create trigger trg_order_completed after update of order_status on public.orders
  for each row execute function private.on_order_completed();

-- Refunds and corrections (order_adjustments) post to the ledger as well.
create or replace function private.on_order_adjustment() returns trigger
language plpgsql security definer set search_path = '' as $$
declare o public.orders; v_amt bigint := abs(new.amount_paise);
begin
  select * into o from public.orders where id = new.order_id;
  if new.type = 'REFUND' then
    perform private.post_journal(null, 'Refund ' || o.order_number || ': ' || new.reason, 'REFUND', new.id::text,
      jsonb_build_array(
        jsonb_build_object('account', case when o.order_status = 'COMPLETED' then '5910' else '1150' end, 'debit', v_amt),
        jsonb_build_object('account','1000','credit', v_amt)));
  elsif new.amount_paise > 0 then
    perform private.post_journal(null, 'Correction (extra charge) ' || o.order_number || ': ' || new.reason, 'CORRECTION', new.id::text,
      jsonb_build_array(jsonb_build_object('account','1150','debit',v_amt), jsonb_build_object('account','4000','credit',v_amt)));
  else
    perform private.post_journal(null, 'Correction (reduction) ' || o.order_number || ': ' || new.reason, 'CORRECTION', new.id::text,
      jsonb_build_array(jsonb_build_object('account','5910','debit',v_amt), jsonb_build_object('account','1150','credit',v_amt)));
  end if;
  return new;
end $$;
create trigger trg_order_adjustment_post after insert on public.order_adjustments
  for each row execute function private.on_order_adjustment();

-- ---------------------------------------------------------------------
-- POS billing (counter / dine-in / takeaway / delivery)
-- ---------------------------------------------------------------------
/** Prices lines from the current menu. Complimentary lines are free but recorded. */
create or replace function private.price_lines(p_items jsonb, p_max_qty int) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_line jsonb; m public.menu_items; v_qty int; v_unit int; v_opt jsonb; v_opt_id text; v_snap jsonb;
        v_lines jsonb := '[]'::jsonb; v_sub bigint := 0; v_comp boolean;
begin
  if jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then raise exception 'EMPTY_CART' using errcode = '22023'; end if;
  for v_line in select * from jsonb_array_elements(p_items) loop
    v_qty := (v_line->>'quantity')::int;
    if v_qty is null or v_qty < 1 or v_qty > p_max_qty then raise exception 'INVALID_QUANTITY' using errcode = '22023'; end if;
    select * into m from public.menu_items where id = v_line->>'itemId' and not is_archived;
    if not found or not m.is_available then
      raise exception 'ITEM_UNAVAILABLE: %', coalesce(m.name, v_line->>'itemId') using errcode = '22023';
    end if;
    v_unit := m.price_paise; v_snap := '[]'::jsonb;
    if jsonb_typeof(v_line->'optionIds') = 'array' then
      for v_opt_id in select distinct x from jsonb_array_elements_text(v_line->'optionIds') x loop
        select o into v_opt from jsonb_array_elements(m.options) o where o->>'id' = v_opt_id;
        if v_opt is null then raise exception 'INVALID_OPTION' using errcode = '22023'; end if;
        v_unit := v_unit + coalesce((v_opt->>'pricePaise')::int, 0);
        v_snap := v_snap || jsonb_build_array(jsonb_build_object('id', v_opt->>'id', 'name', v_opt->>'name',
                    'pricePaise', coalesce((v_opt->>'pricePaise')::int, 0)));
        v_opt := null;
      end loop;
    end if;
    v_comp := coalesce((v_line->>'complimentary')::boolean, false);
    if v_comp then
      v_unit := 0;
      v_snap := v_snap || jsonb_build_array(jsonb_build_object('id','complimentary','name','Complimentary','pricePaise',0));
    end if;
    v_sub := v_sub + v_unit * v_qty;
    v_lines := v_lines || jsonb_build_array(jsonb_build_object('itemId', m.id, 'name', m.name, 'foodType', m.food_type,
                 'unit', v_unit, 'qty', v_qty, 'options', v_snap, 'complimentary', v_comp));
  end loop;
  return jsonb_build_object('lines', v_lines, 'subtotal', v_sub,
    'hasComplimentary', exists (select 1 from jsonb_array_elements(v_lines) l where (l->>'complimentary')::boolean));
end $$;

create or replace function private.pos_totals(p_order uuid) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders; s public.restaurant_settings; v_sub bigint; v_disc bigint; v_tax bigint := 0; v_pack bigint := 0;
begin
  select * into o from public.orders where id = p_order;
  select * into s from public.restaurant_settings where id = 1;
  select coalesce(sum(line_total_paise),0) into v_sub from public.order_items where order_id = p_order;
  v_disc := least(o.discount_paise, v_sub);
  if o.order_type in ('PICKUP','DELIVERY') then v_pack := s.packaging_fee_paise; end if;
  if not o.prices_include_tax and o.tax_rate_bps > 0 then
    v_tax := round(((v_sub - v_disc)::numeric * o.tax_rate_bps) / 10000)::bigint;
  end if;
  perform set_config('app.pos_edit', 'on', true);
  update public.orders set subtotal_paise = v_sub, discount_paise = v_disc, tax_paise = v_tax, packaging_fee_paise = v_pack,
         total_paise = v_sub + v_tax + v_pack + delivery_fee_paise - v_disc
   where id = p_order;
  perform set_config('app.pos_edit', 'off', true);
end $$;

/**
 * Creates a POS bill. Server prices every line. Discounts and complimentary items need OWNER/MANAGER.
 * {idempotencyKey, orderType, tableNumber, customerName, customerPhone, customerId, notes,
 *  discountPaise | discountPercent, items:[{itemId, quantity, optionIds, complimentary}]}
 */
create or replace function public.pos_create_order(p jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare r public.staff_role; s public.restaurant_settings; v_key uuid := (p->>'idempotencyKey')::uuid; v_existing uuid;
        v_priced jsonb; v_sub bigint; v_disc bigint := 0; v_id uuid; v_phone text; v_number text; v_year int; v_seq int;
        v_type public.order_type := coalesce(p->>'orderType','DINE_IN')::public.order_type;
begin
  r := private.require_role('OWNER','MANAGER','STAFF');
  if v_key is null then raise exception 'IDEMPOTENCY_KEY_REQUIRED' using errcode = '22023'; end if;
  select id into v_existing from public.orders where idempotency_key = v_key;
  if v_existing is not null then return private.order_json(v_existing, true) || jsonb_build_object('duplicate', true); end if;
  select * into s from public.restaurant_settings where id = 1;

  v_priced := private.price_lines(p->'items', s.max_item_quantity);
  v_sub := (v_priced->>'subtotal')::bigint;
  if (v_priced->>'hasComplimentary')::boolean and r::text not in ('OWNER','MANAGER') then
    raise exception 'MANAGER_APPROVAL_REQUIRED: complimentary items' using errcode = '42501';
  end if;
  if coalesce((p->>'discountPaise')::bigint,0) > 0 or coalesce((p->>'discountPercent')::numeric,0) > 0 then
    if r::text not in ('OWNER','MANAGER') then raise exception 'MANAGER_APPROVAL_REQUIRED: discount' using errcode = '42501'; end if;
    v_disc := least(v_sub, coalesce((p->>'discountPaise')::bigint, round(v_sub * (p->>'discountPercent')::numeric / 100)::bigint));
  end if;

  v_phone := regexp_replace(coalesce(p->>'customerPhone',''), '[^0-9]', '', 'g');
  if length(v_phone) = 12 and left(v_phone,2) = '91' then v_phone := substr(v_phone,3); end if;
  v_phone := case when v_phone ~ '^[6-9][0-9]{9}$' then '+91' || v_phone else 'WALK-IN' end;

  v_year := extract(year from (now() at time zone s.timezone))::int;
  insert into public.order_counters(year, last) values (v_year, 1)
    on conflict (year) do update set last = public.order_counters.last + 1 returning last into v_seq;
  v_number := format('SS-%s-%s', v_year, lpad(v_seq::text, 4, '0'));

  insert into public.orders(order_number, idempotency_key, customer_name, customer_phone, order_type, table_number,
      delivery_address, notes, payment_method, subtotal_paise, tax_paise, packaging_fee_paise, delivery_fee_paise,
      discount_paise, total_paise, tax_rate_bps, prices_include_tax, terms_accepted_at, source, customer_id, created_by,
      order_status, accepted_at, estimated_ready_at)
  values (v_number, v_key, coalesce(nullif(trim(p->>'customerName'),''), 'Walk-in'), v_phone, v_type,
      nullif(left(trim(coalesce(p->>'tableNumber','')),10),''),
      case when v_type = 'DELIVERY' then coalesce(nullif(trim(p->>'deliveryAddress'),''), 'Counter delivery') end,
      nullif(left(trim(coalesce(p->>'notes','')),300),''), 'CASH', v_sub, 0, 0, 0, v_disc, v_sub - v_disc,
      s.tax_rate_bps, s.prices_include_tax, now(), 'POS', (p->>'customerId')::bigint, auth.uid(),
      'ACCEPTED', now(), now() + make_interval(mins => s.default_prep_minutes))
  returning id into v_id;

  insert into public.order_items(order_id, item_id, name_snapshot, food_type, unit_price_paise, options_snapshot, quantity, line_total_paise)
  select v_id, l->>'itemId', l->>'name', (l->>'foodType')::public.food_type, (l->>'unit')::int, l->'options',
         (l->>'qty')::int, (l->>'unit')::int * (l->>'qty')::int
    from jsonb_array_elements(v_priced->'lines') l;
  insert into public.order_status_history(order_id, from_status, to_status, actor_id, actor_label)
  values (v_id, null, 'PLACED', auth.uid(), coalesce(auth.jwt()->>'email','staff')),
         (v_id, 'PLACED', 'ACCEPTED', auth.uid(), coalesce(auth.jwt()->>'email','staff'));
  perform private.pos_totals(v_id);
  if p->>'tableNumber' is not null then
    update public.restaurant_tables set status = 'OCCUPIED' where name = p->>'tableNumber';
  end if;
  return private.order_json(v_id, true) || jsonb_build_object('duplicate', false);
end $$;

/** Adds items to an open (unpaid) POS bill — "hold and resume". */
create or replace function public.pos_add_items(p_order_id uuid, p_items jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare r public.staff_role; o public.orders; v_priced jsonb;
begin
  r := private.require_role('OWNER','MANAGER','STAFF');
  select * into o from public.orders where id = p_order_id for update;
  if not found or o.source <> 'POS' or o.order_status not in ('ACCEPTED','PREPARING','READY') or o.payment_status <> 'PENDING'
     or private.order_paid(o.id) > 0 then
    raise exception 'BILL_NOT_EDITABLE' using errcode = '22023';
  end if;
  v_priced := private.price_lines(p_items, (select max_item_quantity from public.restaurant_settings where id = 1));
  if (v_priced->>'hasComplimentary')::boolean and r::text not in ('OWNER','MANAGER') then
    raise exception 'MANAGER_APPROVAL_REQUIRED: complimentary items' using errcode = '42501';
  end if;
  insert into public.order_items(order_id, item_id, name_snapshot, food_type, unit_price_paise, options_snapshot, quantity, line_total_paise)
  select o.id, l->>'itemId', l->>'name', (l->>'foodType')::public.food_type, (l->>'unit')::int, l->'options',
         (l->>'qty')::int, (l->>'unit')::int * (l->>'qty')::int
    from jsonb_array_elements(v_priced->'lines') l;
  perform private.pos_totals(o.id);
  -- send back to the kitchen if the earlier items were already ready
  if o.order_status = 'READY' then
    perform set_config('app.pos_edit', 'on', true);
    update public.orders set order_status = 'PREPARING' where id = o.id;
    perform set_config('app.pos_edit', 'off', true);
    insert into public.order_status_history(order_id, from_status, to_status, actor_id, actor_label, reason)
    values (o.id, 'READY', 'PREPARING', auth.uid(), coalesce(auth.jwt()->>'email','staff'), 'Items added');
  end if;
  return private.order_json(o.id, true);
end $$;

/** Applies or changes a bill discount on an open POS bill (manager authorised). */
create or replace function public.pos_set_discount(p_order_id uuid, p_discount_paise bigint, p_reason text) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders;
begin
  perform private.require_role('OWNER','MANAGER');
  select * into o from public.orders where id = p_order_id for update;
  if o.source <> 'POS' or o.payment_status <> 'PENDING' or private.order_paid(o.id) > 0
     or o.order_status not in ('ACCEPTED','PREPARING','READY') then
    raise exception 'BILL_NOT_EDITABLE' using errcode = '22023';
  end if;
  perform set_config('app.pos_edit', 'on', true);
  update public.orders set discount_paise = greatest(0, p_discount_paise) where id = o.id;
  perform set_config('app.pos_edit', 'off', true);
  perform private.pos_totals(o.id);
  perform private.write_audit('POS_DISCOUNT', 'orders', o.id::text, jsonb_build_object('discountPaise', o.discount_paise),
                              jsonb_build_object('discountPaise', p_discount_paise, 'reason', p_reason));
  return private.order_json(o.id, true);
end $$;

/** Closes a fully-paid POS bill: moves it through the normal lifecycle to COMPLETED. */
create or replace function public.pos_settle(p_order_id uuid) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders; v_actor text := coalesce(auth.jwt()->>'email','staff');
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  select * into o from public.orders where id = p_order_id;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
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

/** Receipt / tax-invoice data; counts prints so reprints are marked DUPLICATE. */
create or replace function public.get_receipt(p_order_id uuid, p_count_print boolean default true) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v jsonb; s public.restaurant_settings; o public.orders;
begin
  perform private.require_role('OWNER','MANAGER','STAFF','ACCOUNTANT');
  select * into o from public.orders where id = p_order_id;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  select * into s from public.restaurant_settings where id = 1;
  if p_count_print then update public.orders set receipt_print_count = receipt_print_count + 1 where id = o.id; end if;
  v := private.order_json(o.id, true) || jsonb_build_object(
    'printNumber', o.receipt_print_count + case when p_count_print then 1 else 0 end,
    'taxIncludedPaise', o.tax_included_paise,
    'payments', coalesce((select jsonb_agg(jsonb_build_object('tender', tender, 'amountPaise', amount_paise, 'reference', reference, 'at', created_at) order by id)
                  from public.order_payments where order_id = o.id), '[]'::jsonb),
    'business', jsonb_build_object('name', coalesce(s.legal_business_name, s.restaurant_name), 'tradeName', s.restaurant_name,
        'address', s.address, 'phone', s.phone, 'gstin', s.gstin, 'fssai', s.fssai_number, 'gstRegistered', s.gst_registered,
        'gstScheme', s.gst_scheme, 'sacCode', s.sac_code, 'placeOfSupply', s.place_of_supply, 'upiId', s.upi_id,
        'footer', s.invoice_footer, 'widthMm', s.receipt_width_mm));
  return v;
end $$;

-- ---------------------------------------------------------------------
-- Cash register: opening cash, closing count, shortage/excess posting
-- ---------------------------------------------------------------------
create or replace function public.open_cash_session(p_opening_paise bigint) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v_id bigint;
begin
  perform private.require_role('OWNER','MANAGER','STAFF','ACCOUNTANT');
  if exists (select 1 from public.cash_sessions where closed_at is null) then raise exception 'SESSION_ALREADY_OPEN' using errcode = '22023'; end if;
  insert into public.cash_sessions(opened_by, opening_cash_paise) values (auth.uid(), greatest(0, p_opening_paise)) returning id into v_id;
  return jsonb_build_object('id', v_id);
end $$;

create or replace function private.cash_movement_since(p_from timestamptz) returns bigint
language sql stable security definer set search_path = '' as $$
  select coalesce(sum(l.debit_paise - l.credit_paise), 0)::bigint
    from public.journal_lines l join public.journal_entries e on e.id = l.entry_id
   where l.account_id = private.acct('1000') and e.created_at >= p_from and e.source <> 'CASH_SESSION'
$$;

create or replace function public.cash_session_status() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare c public.cash_sessions;
begin
  perform private.require_role('OWNER','MANAGER','STAFF','ACCOUNTANT');
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
  perform private.require_role('OWNER','MANAGER','STAFF','ACCOUNTANT');
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
-- Tables and reservations
-- ---------------------------------------------------------------------
create table public.restaurant_tables (
  id         bigint generated always as identity primary key,
  name       text not null unique,
  seats      int not null default 4 check (seats between 1 and 50),
  status     text not null default 'FREE' check (status in ('FREE','OCCUPIED','CLEANING','RESERVED')),
  sort_order int not null default 0
);

create table public.reservations (
  id           bigint generated always as identity primary key,
  customer_name text not null,
  phone        text,
  party_size   int not null check (party_size between 1 and 100),
  reserved_for timestamptz not null,
  table_id     bigint references public.restaurant_tables(id),
  status       text not null default 'BOOKED' check (status in ('BOOKED','SEATED','COMPLETED','CANCELLED','NO_SHOW','WAITING')),
  notes        text,
  created_by   uuid,
  created_at   timestamptz not null default now()
);
create index reservations_time_idx on public.reservations(reserved_for);
