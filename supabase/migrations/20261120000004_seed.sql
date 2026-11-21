-- =====================================================================
-- Swad Sangam — seed data (PRD §2.1 and §2.3)
-- Prices come from supplied business information and are NOT verified
-- against a current restaurant-issued menu. The owner must review and
-- approve every price, description and photo in the admin dashboard
-- before enabling ordering. All values are editable without an app update.
-- =====================================================================

insert into public.restaurant_settings (id, rating, review_count, phone, accepting_orders,
                                        pickup_enabled, delivery_enabled, dine_in_enabled)
values (1, 4.5, 49, '+919675750485', false, false, false, false)
on conflict (id) do nothing;

insert into public.categories (id, name, name_hi, sort_order) values
  ('breakfast',  'Breakfast',  'नाश्ता',        1),
  ('snacks',     'Snacks',     'स्नैक्स',        2),
  ('thalis',     'Thalis',     'थाली',          3),
  ('vegetarian', 'Vegetarian', 'शाकाहारी',      4),
  ('paneer',     'Paneer',     'पनीर',          5),
  ('chicken',    'Chicken',    'चिकन',          6),
  ('combos',     'Combos',     'कॉम्बो',         7),
  ('beverages',  'Beverages',  'पेय पदार्थ',     8)
on conflict (id) do nothing;

-- Beverages category stays hidden until the owner adds confirmed drinks.
update public.categories set is_active = false
 where id = 'beverages' and not exists (select 1 from public.menu_items where category_id = 'beverages');

insert into public.menu_items (id, name, name_hi, category_id, description, price_paise, food_type, sort_order) values
  ('dish_001', 'Mooli Paratha',                    'मूली पराठा',               'breakfast',  'Freshly prepared radish-stuffed paratha',            5000, 'VEG',     1),
  ('dish_002', 'Fried Veg Momo',                   'फ्राइड वेज मोमो',           'snacks',     'Fried vegetable momos',                              5000, 'VEG',     1),
  ('dish_003', 'Special Veg Chilli Garlic Momo',   'स्पेशल वेज चिली गार्लिक मोमो', 'snacks',  'Vegetable momos tossed in chilli garlic sauce',      8000, 'VEG',     2),
  ('dish_004', 'Veg Rice with Roti Thali',         'वेज राइस रोटी थाली',         'thalis',     'Rice, roti and vegetable accompaniments',            6000, 'VEG',     1),
  ('dish_005', 'Roti Thali',                       'रोटी थाली',                 'thalis',     'Roti with dal and sabzi',                            7000, 'VEG',     2),
  ('dish_006', 'Special Thali',                    'स्पेशल थाली',               'thalis',     'Our complete special vegetarian thali',              19000, 'VEG',     3),
  ('dish_007', 'Chicken Thali',                    'चिकन थाली',                 'thalis',     'Chicken curry served as a full thali',               25000, 'NON_VEG', 4),
  ('dish_008', 'Channa Masala',                    'छोले मसाला',                'vegetarian', 'Chickpeas cooked in a spiced onion-tomato gravy',    14000, 'VEG',     1),
  ('dish_009', 'Veg Jalfrezi',                     'वेज जालफ्रेज़ी',              'vegetarian', 'Mixed vegetables stir-fried in a tangy masala',      16000, 'VEG',     2),
  ('dish_010', 'Paneer Do Pyaza',                  'पनीर दो प्याज़ा',             'paneer',     'Paneer cooked with plenty of onions',                23000, 'VEG',     1),
  ('dish_011', 'Chicken Handi',                    'चिकन हांडी',                'chicken',    'Chicken curry cooked handi style',                   32000, 'NON_VEG', 1),
  ('dish_012', 'Chilli Chicken + Fried Rice/Noodles', 'चिली चिकन + फ्राइड राइस/नूडल्स', 'combos', 'Chilli chicken with your choice of fried rice or noodles', 30000, 'NON_VEG', 1)
on conflict (id) do nothing;

-- Combo choice modelled as a free option so the kitchen sees the choice.
update public.menu_items
   set options = '[{"id":"with_fried_rice","name":"With Fried Rice","pricePaise":0},
                   {"id":"with_noodles","name":"With Noodles","pricePaise":0}]'::jsonb
 where id = 'dish_012' and options = '[]'::jsonb;
