-- =====================================================================
-- Swad Sangam — POS upgrade (independent implementation; no third-party code):
--   * Kitchen display: stations, item-wise prep status, priority, auto-ready
--   * Bills: split, merge, table transfer
--   * QR table sessions (table code) + call waiter / request bill
--   * Loyalty points: earn on completed bills, redeem at POS
-- All changes are additive and backwards compatible.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Kitchen stations + item-level preparation status
-- ---------------------------------------------------------------------
create table public.kitchen_stations (
  id         bigint generated always as identity primary key,
  name       text not null unique,
  sort_order int not null default 0,
  is_active  boolean not null default true
);
insert into public.kitchen_stations(name, sort_order) values ('Main kitchen', 1), ('Tandoor / Roti', 2), ('Chinese / Momo', 3);

alter table public.categories add column if not exists station_id bigint references public.kitchen_stations(id);
update public.categories set station_id = (select id from public.kitchen_stations where name = 'Chinese / Momo') where id in ('snacks','combos') and station_id is null;
update public.categories set station_id = (select id from public.kitchen_stations where name = 'Main kitchen') where station_id is null;

alter table public.order_items
  add column if not exists prep_status text not null default 'PENDING' check (prep_status in ('PENDING','PREPARING','READY','SERVED','VOID')),
  add column if not exists prep_updated_at timestamptz,
  add column if not exists station_id bigint references public.kitchen_stations(id);
alter table public.orders add column if not exists priority boolean not null default false;

create or replace function private.order_item_station() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.station_id is null then
    select c.station_id into new.station_id from public.menu_items m join public.categories c on c.id = m.category_id where m.id = new.item_id;
  end if;
  return new;
end $$;
create trigger trg_order_item_station before insert on public.order_items for each row execute function private.order_item_station();

-- Price snapshots stay immutable; kitchen status may change, and open POS bills may move lines (split/merge).
drop trigger if exists trg_order_items_immutable on public.order_items;
create or replace function private.order_items_guard() returns trigger
language plpgsql as $$
declare v_pos boolean := coalesce(current_setting('app.pos_edit', true), '') = 'on';
begin
  if tg_op = 'DELETE' then raise exception 'order_items rows are immutable'; end if;
  if (to_jsonb(new) - 'prep_status' - 'prep_updated_at') = (to_jsonb(old) - 'prep_status' - 'prep_updated_at') then return new; end if;
  if v_pos and (to_jsonb(new) - 'order_id' - 'prep_status' - 'prep_updated_at') = (to_jsonb(old) - 'order_id' - 'prep_status' - 'prep_updated_at') then
    return new;
  end if;
  raise exception 'order_items rows are immutable';
end $$;
create trigger trg_order_items_immutable before update or delete on public.order_items
  for each row when (pg_trigger_depth() = 0) execute function private.order_items_guard();

/** Kitchen display board. Kitchen staff never see customer phone numbers. */
create or replace function public.kds_board(p_station_id bigint default null) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','STAFF','KITCHEN');
  return coalesce((select jsonb_agg(x order by (x->>'priority')::boolean desc, x->>'acceptedAt') from (
    select jsonb_build_object('id', o.id, 'orderNumber', o.order_number, 'orderType', o.order_type, 'tableNumber', o.table_number,
      'orderStatus', o.order_status, 'priority', o.priority, 'notes', concat_ws(' · ', o.notes, o.pickup_instructions),
      'source', o.source, 'createdAt', o.created_at, 'acceptedAt', coalesce(o.accepted_at, o.created_at),
      'items', (select jsonb_agg(jsonb_build_object('id', i.id, 'name', i.name_snapshot, 'quantity', i.quantity,
                  'options', i.options_snapshot, 'foodType', i.food_type, 'prepStatus', i.prep_status, 'stationId', i.station_id,
                  'updatedAt', i.prep_updated_at) order by i.id)
                from public.order_items i where i.order_id = o.id and i.prep_status <> 'VOID'
                  and (p_station_id is null or i.station_id = p_station_id))) x
      from public.orders o
     where o.order_status in ('ACCEPTED','PREPARING','READY')
       and exists (select 1 from public.order_items i where i.order_id = o.id and i.prep_status <> 'VOID'
                     and (p_station_id is null or i.station_id = p_station_id))) t), '[]'::jsonb);
end $$;

/** Marks one item PREPARING / READY / SERVED; the order follows automatically. */
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

create or replace function public.set_order_priority(p_order_id uuid, p_priority boolean) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  update public.orders set priority = p_priority where id = p_order_id;
end $$;

-- ---------------------------------------------------------------------
-- Bill operations: table transfer, merge, split
-- ---------------------------------------------------------------------
create or replace function private.open_pos_bill(p_id uuid) returns public.orders
language plpgsql stable security definer set search_path = '' as $$
declare o public.orders;
begin
  select * into o from public.orders where id = p_id;
  if not found or o.source <> 'POS' or o.order_status not in ('ACCEPTED','PREPARING','READY')
     or o.payment_status <> 'PENDING' or private.order_paid(o.id) > 0 then
    raise exception 'BILL_NOT_EDITABLE: only open, unpaid POS bills can be changed' using errcode = '22023';
  end if;
  return o;
end $$;

create or replace function private.refresh_table_status(p_table text) returns void
language sql volatile security definer set search_path = '' as $$
  update public.restaurant_tables t set status = case
      when exists (select 1 from public.orders o where o.table_number = t.name and o.order_status in ('PLACED','ACCEPTED','PREPARING','READY')) then 'OCCUPIED'
      when t.status = 'OCCUPIED' then 'CLEANING' else t.status end
   where t.name = p_table;
$$;

create or replace function public.pos_transfer_table(p_order_id uuid, p_table text) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders;
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  select * into o from public.orders where id = p_order_id for update;
  if not found or o.order_status not in ('ACCEPTED','PREPARING','READY','PLACED') then raise exception 'BILL_NOT_OPEN' using errcode = '22023'; end if;
  update public.orders set table_number = nullif(trim(p_table), '') where id = o.id;
  if o.table_number is not null then perform private.refresh_table_status(o.table_number); end if;
  if nullif(trim(p_table), '') is not null then perform private.refresh_table_status(trim(p_table)); end if;
  perform private.write_audit('TABLE_TRANSFER', 'orders', o.id::text, jsonb_build_object('table', o.table_number), jsonb_build_object('table', p_table));
  return private.order_json(o.id, true);
end $$;

/** Moves every line of p_source into p_target, then closes p_source. */
create or replace function public.pos_merge_bills(p_target uuid, p_source uuid) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare t public.orders; s public.orders;
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  if p_target = p_source then raise exception 'SAME_BILL' using errcode = '22023'; end if;
  t := private.open_pos_bill(p_target);
  s := private.open_pos_bill(p_source);
  perform set_config('app.pos_edit', 'on', true);
  update public.order_items set order_id = t.id where order_id = s.id;
  update public.orders set discount_paise = t.discount_paise + s.discount_paise, total_paise = t.total_paise - s.discount_paise where id = t.id;
  update public.orders set discount_paise = 0, total_paise = s.total_paise + s.discount_paise where id = s.id;
  perform private.pos_totals(t.id);   -- recalculates both bills (source becomes ₹0)
  perform private.pos_totals(s.id);
  update public.orders set order_status = 'CANCELLED', cancel_reason = 'Merged into ' || t.order_number,
         cancelled_by = 'RESTAURANT', closed_at = now(), payment_status = 'FAILED' where id = s.id;
  insert into public.order_status_history(order_id, from_status, to_status, actor_id, actor_label, reason)
  values (s.id, s.order_status, 'CANCELLED', auth.uid(), coalesce(auth.jwt()->>'email','staff'), 'Merged into ' || t.order_number);
  if s.table_number is not null then perform private.refresh_table_status(s.table_number); end if;
  return private.order_json(t.id, true);
end $$;

/** Moves the chosen lines to a new bill for the same table (e.g. friends paying separately). */
create or replace function public.pos_split_bill(p_order_id uuid, p_item_ids bigint[]) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders; s public.restaurant_settings; v_new uuid; v_year int; v_seq int;
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  o := private.open_pos_bill(p_order_id);
  if coalesce(array_length(p_item_ids, 1), 0) = 0 then raise exception 'NO_ITEMS' using errcode = '22023'; end if;
  if (select count(*) from public.order_items where order_id = o.id and id = any(p_item_ids)) <> array_length(p_item_ids, 1) then
    raise exception 'ITEM_NOT_IN_BILL' using errcode = '22023';
  end if;
  if not exists (select 1 from public.order_items where order_id = o.id and not (id = any(p_item_ids))) then
    raise exception 'CANNOT_MOVE_ALL_ITEMS' using errcode = '22023';
  end if;
  select * into s from public.restaurant_settings where id = 1;
  v_year := extract(year from (now() at time zone s.timezone))::int;
  insert into public.order_counters(year, last) values (v_year, 1)
    on conflict (year) do update set last = public.order_counters.last + 1 returning last into v_seq;
  insert into public.orders(order_number, idempotency_key, customer_name, customer_phone, order_type, table_number, notes,
      payment_method, subtotal_paise, total_paise, tax_rate_bps, prices_include_tax, terms_accepted_at, source, created_by,
      order_status, accepted_at, estimated_ready_at)
  values (format('SS-%s-%s', v_year, lpad(v_seq::text, 4, '0')), gen_random_uuid(), o.customer_name, o.customer_phone, o.order_type,
      o.table_number, 'Split from ' || o.order_number, 'CASH', 0, 0, o.tax_rate_bps, o.prices_include_tax, now(), 'POS', auth.uid(),
      o.order_status, o.accepted_at, o.estimated_ready_at)
  returning id into v_new;
  insert into public.order_status_history(order_id, from_status, to_status, actor_id, actor_label, reason)
  values (v_new, null, o.order_status, auth.uid(), coalesce(auth.jwt()->>'email','staff'), 'Split from ' || o.order_number);
  perform set_config('app.pos_edit', 'on', true);
  update public.order_items set order_id = v_new where order_id = o.id and id = any(p_item_ids);
  perform set_config('app.pos_edit', 'off', true);
  perform private.pos_totals(o.id);
  perform private.pos_totals(v_new);
  return jsonb_build_object('original', private.order_json(o.id, true), 'newBill', private.order_json(v_new, true));
end $$;

-- ---------------------------------------------------------------------
-- Loyalty points (off until enabled in Settings)
-- ---------------------------------------------------------------------
alter table public.restaurant_settings
  add column if not exists loyalty_enabled boolean not null default false,
  add column if not exists loyalty_points_per_100 int not null default 5 check (loyalty_points_per_100 between 0 and 100),
  add column if not exists loyalty_point_value_paise int not null default 100 check (loyalty_point_value_paise between 1 and 10000),
  add column if not exists require_table_code boolean not null default false;
alter table public.customers add column if not exists loyalty_points int not null default 0 check (loyalty_points >= 0);

create table public.loyalty_ledger (
  id          bigint generated always as identity primary key,
  customer_id bigint not null references public.customers(id),
  order_id    uuid references public.orders(id),
  points      int not null check (points <> 0),
  reason      text not null check (reason in ('EARN','REDEEM','ADJUST')),
  created_by  uuid,
  created_at  timestamptz not null default now()
);
create unique index loyalty_once_per_order on public.loyalty_ledger(order_id, reason) where reason in ('EARN','REDEEM');

create or replace function private.on_order_completed() returns trigger
language plpgsql security definer set search_path = '' as $$
declare s public.restaurant_settings; v_pts int;
begin
  if new.order_status = 'COMPLETED' and old.order_status is distinct from 'COMPLETED' then
    perform private.post_sale(new.id);
    perform private.consume_stock(new.id);
    select * into s from public.restaurant_settings where id = 1;
    if s.loyalty_enabled and new.customer_id is not null then
      v_pts := (new.total_paise / 10000) * s.loyalty_points_per_100;
      if v_pts > 0 then
        insert into public.loyalty_ledger(customer_id, order_id, points, reason) values (new.customer_id, new.id, v_pts, 'EARN')
        on conflict do nothing;
        if found then update public.customers set loyalty_points = loyalty_points + v_pts where id = new.customer_id; end if;
      end if;
    end if;
  end if;
  return new;
end $$;

/** Attaches a customer to an open POS bill (for khata, loyalty earning). */
create or replace function public.pos_set_customer(p_order_id uuid, p_customer_id bigint) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare c public.customers;
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  perform private.open_pos_bill(p_order_id);
  select * into c from public.customers where id = p_customer_id and is_active;
  if not found then raise exception 'CUSTOMER_NOT_FOUND'; end if;
  update public.orders set customer_id = c.id, customer_name = c.name,
         customer_phone = coalesce(c.phone, customer_phone) where id = p_order_id;
  return private.order_json(p_order_id, true);
end $$;

create or replace function public.pos_redeem_points(p_order_id uuid, p_points int) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders; s public.restaurant_settings; c public.customers; v_value bigint;
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  o := private.open_pos_bill(p_order_id);
  select * into s from public.restaurant_settings where id = 1;
  if not s.loyalty_enabled then raise exception 'LOYALTY_DISABLED' using errcode = '22023'; end if;
  if o.customer_id is null then raise exception 'CUSTOMER_REQUIRED' using errcode = '22023'; end if;
  select * into c from public.customers where id = o.customer_id for update;
  if p_points <= 0 or p_points > c.loyalty_points then raise exception 'NOT_ENOUGH_POINTS: available %', c.loyalty_points using errcode = '22023'; end if;
  v_value := least(p_points::bigint * s.loyalty_point_value_paise, o.subtotal_paise - o.discount_paise);
  p_points := ceil(v_value::numeric / s.loyalty_point_value_paise)::int;
  insert into public.loyalty_ledger(customer_id, order_id, points, reason, created_by) values (c.id, o.id, -p_points, 'REDEEM', auth.uid());
  update public.customers set loyalty_points = loyalty_points - p_points where id = c.id;
  perform set_config('app.pos_edit', 'on', true);
  update public.orders set discount_paise = discount_paise + v_value, total_paise = total_paise - v_value where id = o.id;
  perform set_config('app.pos_edit', 'off', true);
  perform private.pos_totals(o.id);
  return private.order_json(o.id, true) || jsonb_build_object('redeemedPoints', p_points, 'redeemedPaise', v_value);
exception when unique_violation then
  raise exception 'ALREADY_REDEEMED_ON_THIS_BILL' using errcode = '22023';
end $$;

-- ---------------------------------------------------------------------
-- QR table sessions + service requests (call waiter / request bill)
-- ---------------------------------------------------------------------
alter table public.restaurant_tables
  add column if not exists session_code text,
  add column if not exists session_opened_at timestamptz;

create or replace function public.open_table_session(p_table_id bigint) returns text
language plpgsql volatile security definer set search_path = '' as $$
declare v text := lpad((floor(random() * 10000))::int::text, 4, '0');
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  update public.restaurant_tables set session_code = v, session_opened_at = now(), status = 'OCCUPIED' where id = p_table_id;
  if not found then raise exception 'TABLE_NOT_FOUND'; end if;
  return v;
end $$;

create or replace function public.close_table_session(p_table_id bigint) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  update public.restaurant_tables set session_code = null, session_opened_at = null where id = p_table_id;
end $$;

create or replace function private.table_code_ok(p_table text, p_code text) returns boolean
language sql stable security definer set search_path = '' as $$
  select not (select require_table_code from public.restaurant_settings where id = 1)
      or exists (select 1 from public.restaurant_tables where name = trim(p_table) and session_code is not null
                   and session_code = trim(coalesce(p_code, '')) and session_opened_at > now() - interval '12 hours')
$$;

-- Wrap place_order so dine-in QR orders can require the live table code.
alter function public.place_order(jsonb) set schema private;
alter function private.place_order(jsonb) rename to place_order_core;
revoke execute on function private.place_order_core(jsonb) from public, anon, authenticated;

create or replace function public.place_order(p_order jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
begin
  if p_order->>'orderType' = 'DINE_IN' and not private.table_code_ok(coalesce(p_order->>'tableNumber',''), p_order->>'tableCode') then
    return private.err('TABLE_CODE_REQUIRED', 'Please enter the 4-digit table code shown by our staff.');
  end if;
  return private.place_order_core(p_order);
end $$;
revoke execute on function public.place_order(jsonb) from public;
grant execute on function public.place_order(jsonb) to anon, authenticated;

create table public.service_requests (
  id           bigint generated always as identity primary key,
  table_number text not null,
  type         text not null check (type in ('CALL_WAITER','REQUEST_BILL','WATER')),
  status       text not null default 'OPEN' check (status in ('OPEN','DONE')),
  created_at   timestamptz not null default now(),
  done_at      timestamptz,
  done_by      uuid
);
create unique index one_open_request on public.service_requests(table_number, type) where status = 'OPEN';

create or replace function public.request_service(p_table text, p_type text, p_code text default null) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
begin
  if p_type not in ('CALL_WAITER','REQUEST_BILL','WATER') then return private.err('INVALID_TYPE', 'Unknown request'); end if;
  if not exists (select 1 from public.restaurant_tables where name = trim(coalesce(p_table,''))) then
    return private.err('UNKNOWN_TABLE', 'Unknown table');
  end if;
  if not private.table_code_ok(p_table, p_code) then
    return private.err('TABLE_CODE_REQUIRED', 'Please enter the 4-digit table code shown by our staff.');
  end if;
  insert into public.service_requests(table_number, type) values (trim(p_table), p_type) on conflict do nothing;
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.complete_service_request(p_id bigint) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','STAFF');
  update public.service_requests set status = 'DONE', done_at = now(), done_by = auth.uid() where id = p_id;
end $$;

-- ---------------------------------------------------------------------
-- Security, realtime, grants
-- ---------------------------------------------------------------------
alter table public.kitchen_stations enable row level security;
alter table public.loyalty_ledger   enable row level security;
alter table public.service_requests enable row level security;
create policy stations_read on public.kitchen_stations for select to authenticated using (private.has_role('OWNER','MANAGER','STAFF','KITCHEN'));
create policy stations_write on public.kitchen_stations for all to authenticated
  using (private.has_role('OWNER','MANAGER')) with check (private.has_role('OWNER','MANAGER'));
create policy loyalty_read on public.loyalty_ledger for select to authenticated using (private.has_role('OWNER','MANAGER','ACCOUNTANT','STAFF'));
create policy service_read on public.service_requests for select to authenticated using (private.has_role('OWNER','MANAGER','STAFF'));

grant select on public.kitchen_stations, public.loyalty_ledger, public.service_requests to authenticated;
grant insert, update, delete on public.kitchen_stations to authenticated;
revoke all on public.kitchen_stations, public.loyalty_ledger, public.service_requests from anon;
revoke insert, update, delete on public.loyalty_ledger, public.service_requests from authenticated;
grant usage, select on all sequences in schema public to authenticated;

create trigger audit_stations after insert or update or delete on public.kitchen_stations for each row execute function private.audit_row();

do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    alter publication supabase_realtime add table public.service_requests;
  end if;
end $$;

revoke execute on all functions in schema private from public, anon, authenticated;
grant execute on function private.my_role(), private.has_role(public.staff_role[]),
  private.role_matches(public.staff_role, public.staff_role[]) to anon, authenticated;
do $$
declare f text;
begin
  foreach f in array array['kds_board(bigint)','kds_set_item_status(bigint,text)','set_order_priority(uuid,boolean)',
    'pos_transfer_table(uuid,text)','pos_merge_bills(uuid,uuid)','pos_split_bill(uuid,bigint[])','pos_set_customer(uuid,bigint)',
    'pos_redeem_points(uuid,integer)','open_table_session(bigint)','close_table_session(bigint)','complete_service_request(bigint)'] loop
    execute format('revoke execute on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;
revoke execute on function public.request_service(text,text,text) from public;
grant execute on function public.request_service(text,text,text) to anon, authenticated;

-- Fix: changing a bill discount must keep total = subtotal + tax + fees − discount in the same update.
create or replace function public.pos_set_discount(p_order_id uuid, p_discount_paise bigint, p_reason text) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare o public.orders; v_disc bigint;
begin
  perform private.require_role('OWNER','MANAGER');
  o := private.open_pos_bill(p_order_id);
  v_disc := least(greatest(0, p_discount_paise), o.subtotal_paise);
  perform set_config('app.pos_edit', 'on', true);
  update public.orders set discount_paise = v_disc, total_paise = total_paise + o.discount_paise - v_disc where id = o.id;
  perform set_config('app.pos_edit', 'off', true);
  perform private.pos_totals(o.id);
  perform private.write_audit('POS_DISCOUNT', 'orders', o.id::text, jsonb_build_object('discountPaise', o.discount_paise),
                              jsonb_build_object('discountPaise', v_disc, 'reason', p_reason));
  return private.order_json(o.id, true);
end $$;
