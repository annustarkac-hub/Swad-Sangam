-- =====================================================================
-- Swad Sangam — database schema (PRD §10)
-- Target: Supabase (PostgreSQL 15+). All money is stored as integer paise.
-- =====================================================================

create extension if not exists pgcrypto;

create schema if not exists private;          -- never exposed through the API
revoke all on schema private from public;

-- ---------------------------------------------------------------------
-- Enumerations
-- ---------------------------------------------------------------------
create type public.food_type      as enum ('VEG', 'NON_VEG');
create type public.order_type     as enum ('PICKUP', 'DINE_IN', 'DELIVERY');
create type public.order_status   as enum ('PLACED', 'ACCEPTED', 'PREPARING', 'READY',
                                           'OUT_FOR_DELIVERY', 'COMPLETED', 'REJECTED', 'CANCELLED');
create type public.payment_method as enum ('CASH', 'UPI_AT_RESTAURANT', 'ONLINE');
create type public.payment_status as enum ('PENDING', 'PAID', 'FAILED', 'REFUND_PENDING', 'REFUNDED');
create type public.staff_role     as enum ('OWNER', 'MANAGER', 'STAFF', 'KITCHEN');
create type public.discount_type  as enum ('PERCENT', 'FLAT');
create type public.adjustment_type as enum ('REFUND', 'CORRECTION');

-- ---------------------------------------------------------------------
-- Generic updated_at helper
-- ---------------------------------------------------------------------
create or replace function private.touch_updated_at() returns trigger
language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end $$;

-- ---------------------------------------------------------------------
-- 10.5 RestaurantSettings (singleton row, id = 1)
-- Operational switches default to FALSE until the owner configures them.
-- ---------------------------------------------------------------------
create table public.restaurant_settings (
  id                         int primary key default 1 check (id = 1),
  restaurant_name            text not null default 'Swad Sangam',
  tagline                    text not null default 'Good Food. Honest Prices.',
  phone                      text not null default '+919675750485',
  whatsapp_phone             text,
  support_email              text,
  address                    text not null default 'Bhaktiyana, Badrinath Marg, near NIT Ground, Srinagar, Uttarakhand 246174, India',
  latitude                   double precision,
  longitude                  double precision,
  maps_query                 text not null default 'Swad Sangam, Bhaktiyana, Srinagar, Uttarakhand',
  currency                   text not null default 'INR' check (currency = 'INR'),
  timezone                   text not null default 'Asia/Kolkata',
  -- {"1":[{"open":"06:00","close":"23:00"}], ... "7":[...]}  (ISO day: 1 = Monday)
  opening_hours              jsonb not null default
    '{"1":[{"open":"06:00","close":"23:00"}],"2":[{"open":"06:00","close":"23:00"}],
      "3":[{"open":"06:00","close":"23:00"}],"4":[{"open":"06:00","close":"23:00"}],
      "5":[{"open":"06:00","close":"23:00"}],"6":[{"open":"06:00","close":"23:00"}],
      "7":[{"open":"06:00","close":"23:00"}]}'::jsonb,
  hours_verified             boolean not null default false,  -- owner must confirm before publishing
  temporarily_closed         boolean not null default false,
  closure_message            text,
  announcement               text,
  -- ordering switches (all off until configured and tested)
  accepting_orders           boolean not null default false,
  pickup_enabled             boolean not null default false,
  dine_in_enabled            boolean not null default false,
  delivery_enabled           boolean not null default false,
  cash_enabled               boolean not null default true,
  upi_at_restaurant_enabled  boolean not null default false,
  online_payment_enabled     boolean not null default false, -- Phase 2, requires gateway
  upi_id                     text,
  allow_cancel_after_accept  boolean not null default false,
  -- money settings (paise / basis points)
  min_order_paise            int not null default 0     check (min_order_paise >= 0),
  delivery_min_order_paise   int not null default 0     check (delivery_min_order_paise >= 0),
  delivery_fee_paise         int not null default 0     check (delivery_fee_paise >= 0),
  delivery_radius_km         numeric(5,2),
  delivery_area_note         text,
  packaging_fee_paise        int not null default 0     check (packaging_fee_paise >= 0),
  tax_rate_bps               int not null default 0     check (tax_rate_bps between 0 and 5000),
  tax_label                  text not null default 'GST',
  prices_include_tax         boolean not null default true,
  budget_threshold_paise     int not null default 10000,
  default_prep_minutes       int not null default 20    check (default_prep_minutes between 1 and 240),
  max_open_orders_per_phone  int not null default 3     check (max_open_orders_per_phone between 1 and 50),
  max_item_quantity          int not null default 20    check (max_item_quantity between 1 and 200),
  -- compliance / info
  fssai_number               text check (fssai_number is null or fssai_number ~ '^[0-9]{14}$'),
  gstin                      text,
  legal_business_name        text,
  parking_info               text,
  dine_in_info               text,
  rating                     numeric(2,1) check (rating is null or rating between 0 and 5),
  review_count               int,
  reviews_url                text,
  privacy_policy_url         text,
  terms_url                  text,
  pii_retention_days         int not null default 365 check (pii_retention_days between 30 and 3650),
  updated_at                 timestamptz not null default now()
);
create trigger trg_settings_touch before update on public.restaurant_settings
  for each row execute function private.touch_updated_at();

-- ---------------------------------------------------------------------
-- 10.2 Categories
-- ---------------------------------------------------------------------
create table public.categories (
  id          text primary key check (id ~ '^[a-z0-9_-]{2,40}$'),
  name        text not null check (length(name) between 1 and 60),
  name_hi     text,
  sort_order  int  not null default 0,
  is_active   boolean not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);
create trigger trg_categories_touch before update on public.categories
  for each row execute function private.touch_updated_at();

-- ---------------------------------------------------------------------
-- 10.1 MenuItems
-- options: [{"id":"extra_butter","name":"Extra butter","pricePaise":1000}]
-- ---------------------------------------------------------------------
create table public.menu_items (
  id                   text primary key default ('dish_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 10))
                       check (id ~ '^[a-z0-9_-]{2,60}$'),
  name                 text not null check (length(name) between 1 and 80),
  name_hi              text,
  category_id          text not null references public.categories(id) on update cascade,
  description          text,
  description_hi       text,
  price_paise          int  not null check (price_paise > 0 and price_paise <= 10000000),
  food_type            public.food_type not null,
  image_url            text,
  is_available         boolean not null default true,
  is_archived          boolean not null default false,
  is_popular           boolean not null default false,   -- only when owner-approved / data-backed
  preparation_minutes  int check (preparation_minutes is null or preparation_minutes between 1 and 240),
  allergens            text,
  portion_info         text,
  options              jsonb not null default '[]'::jsonb check (jsonb_typeof(options) = 'array'),
  details_verified     boolean not null default false,   -- owner approved description/allergens/portion
  sort_order           int not null default 0,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);
create index menu_items_category_idx on public.menu_items(category_id, sort_order);
create trigger trg_menu_items_touch before update on public.menu_items
  for each row execute function private.touch_updated_at();

-- ---------------------------------------------------------------------
-- Offers / promotions (Phase 2) and coupons (Phase 3)
-- ---------------------------------------------------------------------
create table public.offers (
  id           bigint generated always as identity primary key,
  title        text not null,
  description  text,
  image_url    text,
  coupon_code  text,
  is_active    boolean not null default true,
  starts_at    timestamptz,
  ends_at      timestamptz,
  sort_order   int not null default 0,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
create trigger trg_offers_touch before update on public.offers
  for each row execute function private.touch_updated_at();

create table public.coupons (
  code                text primary key check (code ~ '^[A-Z0-9]{3,20}$'),
  description         text,
  discount_type       public.discount_type not null,
  value               int not null check (value > 0),          -- percent (1-100) or paise
  max_discount_paise  int check (max_discount_paise is null or max_discount_paise > 0),
  min_order_paise     int not null default 0,
  max_uses            int,
  used_count          int not null default 0,
  is_active           boolean not null default true,
  starts_at           timestamptz,
  ends_at             timestamptz,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  check (discount_type <> 'PERCENT' or value between 1 and 100)
);
create trigger trg_coupons_touch before update on public.coupons
  for each row execute function private.touch_updated_at();

-- ---------------------------------------------------------------------
-- Staff (admin roles, PRD §6.2)
-- A row is created automatically for every new auth user, inactive and
-- without a role. The owner activates staff from the dashboard.
-- ---------------------------------------------------------------------
create table public.staff (
  user_id       uuid primary key,             -- references auth.users(id) (FK added in auth migration)
  email         text not null,
  display_name  text,
  role          public.staff_role,
  is_active     boolean not null default false,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);
create trigger trg_staff_touch before update on public.staff
  for each row execute function private.touch_updated_at();

-- ---------------------------------------------------------------------
-- 10.4 Orders
-- ---------------------------------------------------------------------
create table public.order_counters (
  year  int primary key,
  last  int not null default 0
);

create table public.orders (
  id                  uuid primary key default gen_random_uuid(),
  order_number        text not null unique,
  access_token        uuid not null unique default gen_random_uuid(), -- secret held by the customer device
  idempotency_key     uuid not null unique,
  customer_name       text not null,
  customer_phone      text not null,
  order_type          public.order_type not null,
  table_number        text,
  delivery_address    text,
  delivery_landmark   text,
  pickup_instructions text,
  notes               text,
  payment_method      public.payment_method not null,
  payment_status      public.payment_status not null default 'PENDING',
  order_status        public.order_status   not null default 'PLACED',
  subtotal_paise      int not null check (subtotal_paise >= 0),
  tax_paise           int not null default 0 check (tax_paise >= 0),
  packaging_fee_paise int not null default 0 check (packaging_fee_paise >= 0),
  delivery_fee_paise  int not null default 0 check (delivery_fee_paise >= 0),
  discount_paise      int not null default 0 check (discount_paise >= 0),
  total_paise         int not null check (total_paise >= 0),
  tax_rate_bps        int not null default 0,
  prices_include_tax  boolean not null default true,
  coupon_code         text,
  rejection_reason    text,
  cancel_reason       text,
  cancelled_by        text check (cancelled_by in ('CUSTOMER', 'RESTAURANT')),
  estimated_ready_at  timestamptz,
  fcm_token           text,
  terms_accepted_at   timestamptz not null,
  payment_reference   text,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  accepted_at         timestamptz,
  ready_at            timestamptz,
  completed_at        timestamptz,
  closed_at           timestamptz,
  pii_purged          boolean not null default false,
  check (total_paise = subtotal_paise + tax_paise + packaging_fee_paise + delivery_fee_paise - discount_paise),
  check (order_type <> 'DELIVERY' or delivery_address is not null)
);
create index orders_status_idx  on public.orders(order_status, created_at desc);
create index orders_created_idx on public.orders(created_at desc);
create index orders_phone_idx   on public.orders(customer_phone);
create trigger trg_orders_touch before update on public.orders
  for each row execute function private.touch_updated_at();

-- Immutable item-price snapshots
create table public.order_items (
  id                bigint generated always as identity primary key,
  order_id          uuid not null references public.orders(id) on delete cascade,
  item_id           text not null,
  name_snapshot     text not null,
  food_type         public.food_type not null,
  unit_price_paise  int not null check (unit_price_paise >= 0),   -- base + options
  options_snapshot  jsonb not null default '[]'::jsonb,
  quantity          int not null check (quantity > 0),
  line_total_paise  int not null check (line_total_paise = unit_price_paise * quantity)
);
create index order_items_order_idx on public.order_items(order_id);
create index order_items_item_idx  on public.order_items(item_id);

create table public.order_status_history (
  id           bigint generated always as identity primary key,
  order_id     uuid not null references public.orders(id) on delete cascade,
  from_status  public.order_status,
  to_status    public.order_status not null,
  actor_id     uuid,
  actor_label  text not null,                 -- 'CUSTOMER', 'SYSTEM' or staff email
  reason       text,
  created_at   timestamptz not null default now()
);
create index order_status_history_order_idx on public.order_status_history(order_id, created_at);

-- Auditable corrections / refunds (orders themselves are never edited)
create table public.order_adjustments (
  id            bigint generated always as identity primary key,
  order_id      uuid not null references public.orders(id),
  type          public.adjustment_type not null,
  amount_paise  int not null check (amount_paise <> 0),
  reason        text not null check (length(reason) >= 3),
  actor_id      uuid not null,
  created_at    timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- Private customer feedback (FR-12) — visible to admins only
-- ---------------------------------------------------------------------
create table public.feedback (
  id                 bigint generated always as identity primary key,
  order_id           uuid references public.orders(id) on delete set null,
  food_quality       smallint check (food_quality between 1 and 5),
  order_accuracy     smallint check (order_accuracy between 1 and 5),
  packaging          smallint check (packaging between 1 and 5),
  staff_behaviour    smallint check (staff_behaviour between 1 and 5),
  overall            smallint not null check (overall between 1 and 5),
  comments           text check (comments is null or length(comments) <= 1000),
  consent_to_publish boolean not null default false,
  created_at         timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- 10.6 AuditLogs
-- ---------------------------------------------------------------------
create table public.audit_logs (
  id           bigint generated always as identity primary key,
  actor_id     uuid,
  actor_email  text,
  action       text not null,      -- INSERT / UPDATE / DELETE / custom
  entity       text not null,      -- table name
  entity_id    text,
  old_value    jsonb,
  new_value    jsonb,
  created_at   timestamptz not null default now()
);
create index audit_logs_created_idx on public.audit_logs(created_at desc);

-- ---------------------------------------------------------------------
-- Privacy-conscious analytics (PRD §14): no device identifiers,
-- only a random per-install session id generated by the app.
-- ---------------------------------------------------------------------
create table public.analytics_events (
  id          bigint generated always as identity primary key,
  event_name  text not null check (event_name in (
                'app_opened','home_viewed','menu_opened','item_viewed','item_added_to_cart',
                'cart_viewed','checkout_started','order_submitted','order_accepted','order_rejected',
                'order_completed','order_cancelled','directions_clicked','call_clicked','search_performed',
                'feedback_submitted','share_clicked')),
  session_id  text check (session_id is null or length(session_id) <= 64),
  properties  jsonb not null default '{}'::jsonb check (pg_column_size(properties) < 2048),
  created_at  timestamptz not null default now()
);
create index analytics_events_created_idx on public.analytics_events(created_at desc, event_name);

-- ---------------------------------------------------------------------
-- Private configuration (push-notification function URL + shared secret)
-- ---------------------------------------------------------------------
create table private.app_config (
  key    text primary key,
  value  text not null
);
