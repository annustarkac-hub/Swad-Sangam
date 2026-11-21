-- Customer website: list table names so a guest without a table QR can pick a free table.
-- Only names, seats and status are exposed — never session codes.
create or replace function public.public_tables() returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(jsonb_build_object('name', name, 'seats', seats, 'free', status = 'FREE')
                            order by sort_order, name), '[]'::jsonb)
    from public.restaurant_tables
$$;
revoke execute on function public.public_tables() from public;
grant execute on function public.public_tables() to anon, authenticated;
