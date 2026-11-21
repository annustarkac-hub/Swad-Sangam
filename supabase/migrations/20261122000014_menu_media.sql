-- Customer website redesign: image provenance for dishes and a configurable banner.
-- Representative (non-restaurant) photos are labelled on the website until the owner
-- uploads a real Swad Sangam photo from the admin Menu page.
alter table public.menu_items
  add column if not exists image_is_representative boolean not null default false,
  add column if not exists image_credit text;
alter table public.restaurant_settings
  add column if not exists hero_image_url text,
  add column if not exists hero_image_credit text,
  add column if not exists cost_for_two_paise int check (cost_for_two_paise is null or cost_for_two_paise >= 0);
