-- Customer accounts (free: Supabase email + password auth, no SMS OTP).
-- A customer is any signed-in user WITHOUT a staff row. Staff access is unchanged (staff table + roles).

-- ---------------------------------------------------------------------
-- Profile + saved addresses (each customer sees and edits only their own rows)
-- ---------------------------------------------------------------------
create table if not exists public.customer_profiles (
  user_id     uuid primary key references auth.users(id) on delete cascade,
  full_name   text not null check (length(trim(full_name)) between 2 and 60),
  phone       text not null check (phone ~ '^[6-9][0-9]{9}$'),
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create table if not exists public.customer_addresses (
  id          bigint generated always as identity primary key,
  user_id     uuid not null references auth.users(id) on delete cascade default auth.uid(),
  label       text not null default 'Home' check (length(trim(label)) between 1 and 30),
  address     text not null check (length(trim(address)) between 10 and 300),
  landmark    text check (landmark is null or length(landmark) <= 120),
  is_default  boolean not null default false,
  created_at  timestamptz not null default now()
);
create index if not exists customer_addresses_user_idx on public.customer_addresses(user_id);

alter table public.customer_profiles  enable row level security;
alter table public.customer_addresses enable row level security;

drop policy if exists customer_profiles_own on public.customer_profiles;
create policy customer_profiles_own on public.customer_profiles for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop policy if exists customer_profiles_staff_read on public.customer_profiles;
create policy customer_profiles_staff_read on public.customer_profiles for select to authenticated
  using (private.has_role('OWNER','MANAGER'));

drop policy if exists customer_addresses_own on public.customer_addresses;
create policy customer_addresses_own on public.customer_addresses for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

grant select, insert, update, delete on public.customer_profiles, public.customer_addresses to authenticated;
revoke all on public.customer_profiles, public.customer_addresses from anon;

-- At most 10 saved addresses; only one default per customer
create or replace function private.customer_address_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'INSERT' and (select count(*) from public.customer_addresses where user_id = new.user_id) >= 10 then
    raise exception 'TOO_MANY_ADDRESSES' using errcode = 'check_violation';
  end if;
  if new.is_default then
    update public.customer_addresses set is_default = false
     where user_id = new.user_id and id is distinct from new.id and is_default;
  end if;
  new.label := trim(new.label); new.address := trim(new.address); new.landmark := nullif(trim(coalesce(new.landmark, '')), '');
  return new;
end $$;
drop trigger if exists customer_address_guard on public.customer_addresses;
create trigger customer_address_guard before insert or update on public.customer_addresses
  for each row execute function private.customer_address_guard();

create or replace function private.touch_customer_profile() returns trigger
language plpgsql set search_path = '' as $$
begin new.updated_at := now(); new.full_name := regexp_replace(trim(new.full_name), '\s+', ' ', 'g'); return new; end $$;
drop trigger if exists touch_customer_profile on public.customer_profiles;
create trigger touch_customer_profile before insert or update on public.customer_profiles
  for each row execute function private.touch_customer_profile();

-- ---------------------------------------------------------------------
-- Link orders to the signed-in customer
-- ---------------------------------------------------------------------
alter table public.orders add column if not exists customer_user_id uuid references auth.users(id) on delete set null;
create index if not exists orders_customer_user_idx on public.orders(customer_user_id, created_at desc);

create or replace function private.link_order_customer() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  -- only for signed-in customers; staff-created (POS) orders are never linked to the staff member
  if auth.uid() is not null and private.my_role() is null then
    new.customer_user_id := auth.uid();
  else
    new.customer_user_id := null;
  end if;
  return new;
end $$;
drop trigger if exists link_order_customer on public.orders;
create trigger link_order_customer before insert on public.orders
  for each row execute function private.link_order_customer();

-- The customer's own orders (newest first), same shape as get_orders_by_tokens
create or replace function public.my_orders(p_limit int default 50) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(x.j order by x.created_at desc), '[]'::jsonb) from (
    select private.order_json(o.id) || jsonb_build_object('accessToken', o.access_token) as j, o.created_at
      from public.orders o
     where auth.uid() is not null and o.customer_user_id = auth.uid()
     order by o.created_at desc
     limit least(greatest(coalesce(p_limit, 50), 1), 200)) x
$$;
revoke execute on function public.my_orders(int) from public, anon;
grant execute on function public.my_orders(int) to authenticated;

-- After login, attach orders this device placed as a guest (the device holds their secret tokens)
create or replace function public.claim_my_orders(p_tokens uuid[]) returns int
language plpgsql volatile security definer set search_path = '' as $$
declare n int;
begin
  if auth.uid() is null or private.my_role() is not null then return 0; end if;
  update public.orders set customer_user_id = auth.uid()
   where access_token = any(p_tokens[1:50]) and customer_user_id is null;
  get diagnostics n = row_count;
  return n;
end $$;
revoke execute on function public.claim_my_orders(uuid[]) from public, anon;
grant execute on function public.claim_my_orders(uuid[]) to authenticated;

-- ---------------------------------------------------------------------
-- Customer ratings shown on the website: only real feedback, only comments the customer agreed to publish
-- ---------------------------------------------------------------------
create or replace function public.public_reviews() returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'count', (select count(*) from public.feedback),
    'average', (select round(avg(overall)::numeric, 1) from public.feedback),
    'recent', coalesce((select jsonb_agg(jsonb_build_object('overall', f.overall, 'comments', f.comments, 'at', f.created_at)
                                         order by f.created_at desc)
                          from (select * from public.feedback
                                 where consent_to_publish and comments is not null
                                 order by created_at desc limit 10) f), '[]'::jsonb))
$$;
revoke execute on function public.public_reviews() from public;
grant execute on function public.public_reviews() to anon, authenticated;

-- One rating per order, and only once the order is finished
create or replace function private.feedback_order_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.order_id is not null and not exists (
       select 1 from public.orders where id = new.order_id and order_status = 'COMPLETED') then
    raise exception 'ORDER_NOT_COMPLETED' using errcode = 'check_violation';
  end if;
  return new;
end $$;
drop trigger if exists feedback_order_guard on public.feedback;
create trigger feedback_order_guard before insert on public.feedback
  for each row execute function private.feedback_order_guard();

-- Customer sign-ups (website account page) must not appear as pending staff.
-- Staff still sign up on admin.html and get an inactive staff row that the owner activates.
create or replace function private.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if coalesce(new.raw_user_meta_data->>'account_type', '') = 'customer' then
    return new;
  end if;
  insert into public.staff(user_id, email, display_name)
  values (new.id, coalesce(new.email, ''), coalesce(new.raw_user_meta_data->>'display_name', split_part(coalesce(new.email,''), '@', 1)))
  on conflict (user_id) do nothing;
  return new;
end $$;
