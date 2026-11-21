-- =====================================================================
-- Explicit table privileges for the API roles. Newer Supabase projects may not
-- grant these automatically. Row level security (migration 2) still decides
-- which ROWS each role can see or change; these grants only open the tables.
-- =====================================================================
grant usage on schema public to anon, authenticated;

-- Public catalogue (customer app, no login)
grant select on public.restaurant_settings, public.categories, public.menu_items, public.offers to anon, authenticated;

-- Staff dashboard (RLS limits to the right roles)
grant update on public.restaurant_settings to authenticated;
grant insert, update, delete on public.categories, public.menu_items, public.offers, public.coupons to authenticated;
grant select on public.coupons, public.staff, public.orders, public.order_items, public.order_status_history,
  public.order_adjustments, public.feedback, public.audit_logs, public.analytics_events to authenticated;
grant usage, select on all sequences in schema public to authenticated;

-- Never: direct writes to orders/financial tables by API roles (RPC only)
revoke insert, update, delete on public.orders, public.order_items, public.order_status_history,
  public.order_adjustments, public.feedback, public.audit_logs, public.analytics_events,
  public.order_counters, public.staff from anon, authenticated;
revoke all on public.coupons, public.order_counters from anon;
