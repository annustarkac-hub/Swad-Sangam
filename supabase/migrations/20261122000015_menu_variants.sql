-- Menu variants: options may belong to a "group" (e.g. size: Half/Full, style: Dry/Gravy).
-- Within a group the customer can pick at most one option; picking none means the base
-- variant named by menu_items.variant_base_label (e.g. "Half", "Dry", "Fried Rice").
-- Prices stay server-side: option pricePaise is added to the base price as before.
alter table public.menu_items add column if not exists variant_base_label text;

create or replace function private.check_option_groups() returns trigger
language plpgsql security definer set search_path = '' as $$
declare v_bad text;
begin
  if jsonb_array_length(coalesce(new.options_snapshot, '[]'::jsonb)) < 2 then return new; end if;
  select g into v_bad from (
    select o->>'group' g, count(*) n
      from jsonb_array_elements(new.options_snapshot) s
      join public.menu_items m on m.id = new.item_id
      cross join lateral jsonb_array_elements(m.options) o
     where o->>'id' = s->>'id' and o->>'group' is not null
     group by o->>'group') x
   where n > 1 limit 1;
  if v_bad is not null then
    raise exception 'INVALID_OPTION: choose only one % for %', v_bad, new.name_snapshot using errcode = '22023';
  end if;
  return new;
end $$;
create trigger trg_order_item_option_groups before insert on public.order_items
  for each row execute function private.check_option_groups();
revoke execute on function private.check_option_groups() from public, anon, authenticated;
