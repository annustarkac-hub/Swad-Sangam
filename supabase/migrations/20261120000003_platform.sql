-- =====================================================================
-- Swad Sangam — Supabase platform features:
-- realtime order feed, image storage, push-notification hook, scheduled jobs
-- Every block is guarded so the migration also runs on plain PostgreSQL.
-- =====================================================================

-- Realtime: the admin dashboard listens to new / changed orders.
-- (Realtime respects RLS, so only staff receive order rows.)
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    alter publication supabase_realtime add table public.orders;
  end if;
end $$;

-- ---------------------------------------------------------------------
-- Image storage bucket for dish photos / offers (public read, staff write)
-- ---------------------------------------------------------------------
do $$
begin
  if exists (select 1 from pg_namespace where nspname = 'storage')
     and to_regclass('storage.buckets') is not null then
    insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
    values ('menu-images', 'menu-images', true, 2097152, array['image/webp','image/jpeg','image/png'])
    on conflict (id) do nothing;

    execute $p$create policy "menu images public read" on storage.objects for select
              using (bucket_id = 'menu-images')$p$;
    execute $p$create policy "menu images staff insert" on storage.objects for insert to authenticated
              with check (bucket_id = 'menu-images' and private.has_role('OWNER','MANAGER'))$p$;
    execute $p$create policy "menu images staff update" on storage.objects for update to authenticated
              using (bucket_id = 'menu-images' and private.has_role('OWNER','MANAGER'))$p$;
    execute $p$create policy "menu images staff delete" on storage.objects for delete to authenticated
              using (bucket_id = 'menu-images' and private.has_role('OWNER','MANAGER'))$p$;
  end if;
end $$;

-- ---------------------------------------------------------------------
-- Push notifications (FR-10)
-- When an order status changes, call the `push-order-status` Edge Function,
-- which sends a Firebase Cloud Messaging notification to the customer.
-- Configure once (see docs/SETUP_GUIDE.md):
--   insert into private.app_config values
--     ('push_function_url', 'https://<project-ref>.supabase.co/functions/v1/push-order-status'),
--     ('push_function_secret', '<same random secret as the PUSH_WEBHOOK_SECRET function secret>');
-- If not configured, nothing happens — status remains visible in the app.
-- ---------------------------------------------------------------------
do $$
begin
  begin
    create extension if not exists pg_net;
  exception when others then
    raise notice 'pg_net not available — push notifications disabled on this database';
  end;
end $$;

create or replace function private.notify_order_push() returns trigger
language plpgsql security definer set search_path = '' as $$
declare v_url text; v_secret text;
begin
  if new.fcm_token is null or new.order_status is not distinct from old.order_status then
    return new;
  end if;
  select value into v_url    from private.app_config where key = 'push_function_url';
  select value into v_secret from private.app_config where key = 'push_function_secret';
  if v_url is null or v_secret is null then return new; end if;
  begin
    execute 'select net.http_post(url := $1, body := $2, headers := $3)'
      using v_url,
            jsonb_build_object('orderId', new.id, 'status', new.order_status),
            jsonb_build_object('Content-Type', 'application/json', 'x-webhook-secret', v_secret);
  exception when others then
    raise warning 'push notification request failed: %', sqlerrm;  -- never block the status change
  end;
  return new;
end $$;

create trigger trg_orders_push after update of order_status on public.orders
  for each row execute function private.notify_order_push();

-- ---------------------------------------------------------------------
-- Scheduled jobs (pg_cron): daily personal-data retention clean-up
-- ---------------------------------------------------------------------
do $$
begin
  begin
    create extension if not exists pg_cron;
    perform cron.schedule('swad-sangam-pii-retention', '30 21 * * *',  -- 03:00 IST
                          'select private.purge_old_personal_data()');
  exception when others then
    raise notice 'pg_cron not available — run select private.purge_old_personal_data() manually';
  end;
end $$;
