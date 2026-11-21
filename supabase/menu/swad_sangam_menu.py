#!/usr/bin/env python3
"""Swad Sangam menu as printed on the restaurant's own menu card (photos shared by the owner, Nov 2026).
Generates supabase/menu/swad_sangam_menu.sql. Prices are exactly as printed. Where the card shows two
prices without saying what they mean (e.g. "₹40 | ₹70") the variants are labelled Half / Full and the
dish is left "Unverified" so the owner can confirm the label in Admin → Menu.
Run: python3 supabase/menu/swad_sangam_menu.py"""
import json, re, os

# id, name, station
CATEGORIES = [
 ("breakfast","Breakfast","Tandoor / Roti"), ("thalis","Thali","Main kitchen"), ("momo","Momo","Chinese / Momo"),
 ("snacks","Snacks","Main kitchen"), ("starters","Starters","Chinese / Momo"), ("soups","Soups","Chinese / Momo"),
 ("spring_roll","Spring Rolls & Kathi Rolls","Chinese / Momo"), ("noodles","Chowmein / Noodles","Chinese / Momo"),
 ("fried_rice","Fried Rice","Chinese / Momo"), ("combos","Combo Meals","Chinese / Momo"),
 ("main_veg","Main Course (Veg)","Main kitchen"), ("main_nonveg","Main Course (Non-Veg)","Main kitchen"),
 ("rice","Rice","Main kitchen"), ("biryani","Biryani","Main kitchen"), ("breads","Breads","Tandoor / Roti"),
 ("maggi","Maggi","Main kitchen"), ("pasta","Pasta","Main kitchen"), ("dahi","Dahi & Raita","Main kitchen"),
 ("papad","Papad","Main kitchen"), ("salad","Salad","Main kitchen"), ("beverages","Beverages","Main kitchen"),
 ("shakes","Shakes","Main kitchen"),
]
HF = ("size", "Half", "Full")      # two prices, meaning not printed → confirm with owner
DG = ("style", "Dry", "Gravy")     # printed as (Dry/Gravy)

# (category, name, price, second_price_or_None, variant_spec_or_None, food 'V'/'N', description, fixed_id)
M = []
def add(cat, name, p, p2=None, var=HF, food="V", desc=None, fid=None, extra=None):
    M.append(dict(cat=cat, name=name, p=p, p2=p2, var=var if p2 is not None else None, food=food, desc=desc, id=fid, extra=extra))

for n,p in [("Masala Chai",10),("Lemon Tea",15),("Green Tea",25),("Coffee",40),("Cold Coffee",40),("Hot Milk",30),("Masala Milk",60),
            ("Masala Chaanch",30),("Nimbu Pani",20),("Water Bottle",20),("Dry Fruit Lassi",80)]: add("beverages",n,p)
add("beverages","Lime Soda",50,extra=("style","Sweet",[("salty","Salty",0)]))

add("breakfast","Plain Paratha",30); add("breakfast","Aloo Paratha",40,70); add("breakfast","Onion Paratha",50)
add("breakfast","Mooli Paratha",50,fid="dish_001"); add("breakfast","Gobi Paratha",50); add("breakfast","Paneer Paratha",80,140)
add("breakfast","Mix Paratha",70); add("breakfast","Egg Paratha",60,food="N"); add("breakfast","Cheese Paratha",80,140)
add("breakfast","Chole Bhature",80); add("breakfast","Puri Bhaji",60)

for n,p,f in [("Veg Pakoda",60,"V"),("Paneer Pakoda",120,"V"),("Aloo Pakoda",40,"V"),("Aloo Samosa",15,"V"),("Bread Pakoda",15,"V"),
              ("Veg Sandwich",60,"V"),("Veg Cheese Sandwich",80,"V"),("Egg Cheese Sandwich",100,"N"),("Chicken Cheese Sandwich",140,"N"),
              ("Egg Sandwich",80,"N"),("Chicken Sandwich",120,"N"),("Veg Burger",60,"V"),("Paneer Burger",80,"V"),("Egg Burger",70,"N"),
              ("Chicken Burger",120,"N"),("Bun Omelette",40,"N"),("Plain Omelette",50,"N"),("Masala Omelette",60,"N"),("Boiled Egg",10,"N")]:
    add("snacks",n,p,food=f)

for n,p in [("Tawa Roti",10),("Tawa Butter Roti",15),("Rumali Roti",30),("Lachha Paratha (Tava)",45)]: add("breads",n,p)
for n,p,f in [("Plain Maggi",40,"V"),("Veg Maggi",50,"V"),("Egg Maggi",70,"N"),("Masala Maggi",50,"V"),("Chicken Maggi",90,"N"),("Cheese Maggi",80,"V")]:
    add("maggi",n,p,food=f)
for n,p in [("Plain Dahi",40),("Jeera Raita",50),("Mix Raita",60),("Boondi Raita",70)]: add("dahi",n,p)
add("papad","Fried Papad",15); add("papad","Masala Papad",25)
for n in ["Green Salad","Onion Salad","Cucumber Salad"]: add("salad",n,60)

add("thalis","Veg Rice Thali (Student Special)",50,desc="Full Rice + Seasonal Veg + Dal + Achar + Salad")
add("thalis","Veg Rice with Roti Thali",60,desc="Half Rice + 4 Roti + Seasonal Veg + Dal + Achar + Salad",fid="dish_004")
add("thalis","Roti Thali",70,desc="6 Roti + Mix Veg + Dal + Achar + Salad",fid="dish_005")
add("thalis","Special Thali",190,desc="4 Roti + Half Rice + Paneer Masala + Mix Veg + Achar + Salad + Papad",fid="dish_006")
add("thalis","Chicken Thali",250,food="N",desc="4 Roti + Half Rice + Chicken Curry + 2 Egg (Boil/Bhurji) + Salad + Papad",fid="dish_007",
    extra=("egg","Boiled eggs",[("egg_bhurji","Egg Bhurji",0)]))

RN = ("base","Fried Rice",[("noodles","Noodles",0)])
add("combos","Veg Triple Rice",180); add("combos","Veg Triple Noodles",180)
add("combos","Chilli Paneer + Fried Rice/Noodles",220,extra=RN); add("combos","Veg Manchurian + Fried Rice/Noodles",220,extra=RN)
add("combos","Chilli Chicken + Fried Rice/Noodles",300,food="N",extra=RN,fid="dish_012")
add("combos","Chicken Manchurian + Fried Rice/Noodles",300,food="N",extra=RN); add("combos","Chicken Chop Suey",290,food="N")

for n in ["Vanilla","Banana","Strawberry","Oreo"]: add("shakes",f"{n} Shake",90)

PASTA = ("pasta","Spaghetti",[("penne","Penne",0),("macaroni","Macaroni",0)])
for n,v,c in [("Arrabbiata Pasta (Red Sauce)",110,180),("Mushroom Alfredo Pasta (White Sauce)",120,230),("Aglio Pasta",90,160)]:
    add("pasta",f"{n} – Veg",v,extra=PASTA)
    add("pasta",f"{n} – Chicken",c,food="N",extra=PASTA)

for n,p,f in [("Tomato Soup",60,"V"),("Veg Manchow Soup",60,"V"),("Veg Lemon Coriander Soup",70,"V"),("Chicken Manchow Soup",80,"N"),("Chicken Lemon Coriander Soup",90,"N")]:
    add("soups",n,p,food=f)

add("starters","French Fries",60,extra=("style","Salted",[("spicy","Spicy",0)]))
for n,p in [("Chilli Potato",90),("Honey Chilli Potato",100),("Peanut Masala",90),("Veg Crispy",120),("Crunchy Dahi Roll",120),("Chana Garlic",120)]:
    add("starters",n,p)
add("starters","Chicken Lollipop",190,370,food="N"); add("starters","Chicken Pakoda",220,399,food="N")
for n,p,p2,f in [("Chilli Paneer",180,200,"V"),("Paneer 65",190,210,"V"),("Veg Manchurian",150,170,"V"),("Gobi Manchurian",150,170,"V"),
                 ("Chicken Manchurian",240,270,"N"),("Chicken Chilli",250,280,"N"),("Chicken 65",280,299,"N")]:
    add("starters",n,p,p2,var=DG,food=f)
add("starters","Honey Glazed Lemon Chicken",350,food="N"); add("starters","Shredded Chilli Chicken",320,food="N"); add("starters","Pan Fried Chilli Chicken",299,food="N")

add("momo","Veg Momo",30,50); add("momo","Fried Veg Momo",50,70,fid="dish_002"); add("momo","Special Veg Chilli Garlic Momo",80,150,fid="dish_003")
add("momo","Chicken Momo",50,90,food="N"); add("momo","Fried Chicken Momo",70,130,food="N"); add("momo","Special Chicken Chilli Garlic Momo",110,199,food="N")

add("spring_roll","Veg Spring Roll",20,40); add("spring_roll","Paneer Spring Roll",140); add("spring_roll","Egg Spring Roll",100,food="N")
add("spring_roll","Chicken Spring Roll",150,food="N"); add("spring_roll","Veg Kathi Roll",110); add("spring_roll","Chicken Kathi Roll",160,food="N")

for n,p,p2,f in [("Veg Hakka Noodles",30,60,"V"),("Veg Schezwan Noodles",60,120,"V"),("Veg Singapore Noodles",80,150,"V"),("Chilli Paneer Noodles",80,150,"V"),
                 ("Chilli Garlic Noodles",60,120,"V"),("Egg Chowmein",60,120,"N"),("Paneer Hakka Noodles",80,150,"V"),("Chicken Hakka Noodles",90,170,"N"),
                 ("Chicken Schezwan Noodles",100,190,"N"),("Chicken Singapore Noodles",120,220,"N"),("Chicken Walk Toss Noodles",110,199,"N"),
                 ("Chicken Chilli Garlic Noodles",100,190,"N")]:
    add("noodles",n,p,p2,food=f)
for n,p,f in [("Veg Fried Rice",90,"V"),("Veg Schezwan Rice",100,"V"),("Veg Singapore Rice",120,"V"),("Veg Chilli Garlic Rice",120,"V"),("Chicken Fried Rice",140,"N"),
              ("Chicken Schezwan Fried Rice",140,"N"),("Chicken Singapore Rice",170,"N"),("Chicken Chilli Garlic Rice",150,"N"),("Egg Rice",100,"N")]:
    add("fried_rice",n,p,food=f)

for n,p,p2 in [("Dal Fry",120,199),("Dal Tadka",130,210),("Dal Palak",130,210),("Dal Makhani",150,260),("Rajma Masala",160,280),("Jeera Aloo",99,150),
               ("Aloo Gobi",120,220),("Mix Veg",130,250),("Veg Diwani Handi",180,340),("Kaju Paneer Masala",300,590),("Shahi Paneer",230,370),
               ("Kadhai Paneer",230,370),("Paneer Pasanda",250,399),("Paneer Bhurji",230,370),("Palak Paneer",260,430),("Paneer Butter Masala",240,380),
               ("Mutter Paneer",240,450),("Malai Kofta",290,490),("Mushroom Masala",230,370),("Mushroom Do Pyaza",230,370),("Aloo Methi",120,240),
               ("Kaju Curry",230,450),("Kaju Masala",250,490)]:
    add("main_veg",n,p,p2)
add("main_veg","Channa Masala",140,250,fid="dish_008"); add("main_veg","Paneer Do Pyaza",230,370,fid="dish_010"); add("main_veg","Veg Jalfrezi",160,310,fid="dish_009")
for n,p in [("Veg Patiyala",220),("Paneer Patiyala",250),("Veg Maratha",160)]: add("main_veg",n,p)

for n,p in [("Egg Bhurji",90),("Egg Curry",130),("Egg Masala",140)]: add("main_nonveg",n,p,food="N")
add("main_nonveg","Chicken Handi",320,590,food="N",fid="dish_011")
for n,p,p2 in [("Chicken Rogan Josh",300,550),("Chicken Patiyala",350,590),("Butter Chicken",350,590),("Kadhai Chicken",350,590),("Chicken Masala",330,550),
               ("Chicken Labhdaar",330,550),("Chicken Curry",320,550),("Chicken Angara",380,600),("Chicken Rara",380,600)]:
    add("main_nonveg",n,p,p2,food="N")
for n,p in [("Mutton Rogan Josh",650),("Mutton Masala",690),("Mutton Rara",690),("Mutton Hyderabadi Handi",750),("Fish Curry",350)]:
    add("main_nonveg",n,p,food="N")

add("rice","Plain Rice",60,100); add("rice","Jeera Rice",70,110); add("rice","Curd Rice",120); add("rice","Matar Pulao",120)
add("rice","Veg Pulao",130,250); add("rice","Lemon Rice",150); add("rice","Biryani Rice",130)
add("biryani","Soya Biryani",130,240); add("biryani","Veg Biryani",140,260); add("biryani","Egg Biryani",130,250,food="N")
add("biryani","Chicken Biryani",250,420,food="N"); add("biryani","Mutton Biryani",550,food="N")

def slug(s): return "m_" + re.sub(r"[^a-z0-9]+","_", s.lower()).strip("_")[:56]
def q(s): return "null" if s is None else "'" + str(s).replace("'","''") + "'"

ids=set(); out=["-- GENERATED by supabase/menu/swad_sangam_menu.py — Swad Sangam menu card (owner photos, Nov 2026)",
 "-- Upserts categories + dishes. Keeps photos, availability and Hindi names already set by the owner.","begin;"]
for i,(cid,name,station) in enumerate(CATEGORIES,1):
    out.append(f"insert into public.categories(id,name,sort_order,is_active,station_id) values ({q(cid)},{q(name)},{i},true,(select id from public.kitchen_stations where name={q(station)})) "
               f"on conflict (id) do update set name=excluded.name, sort_order=excluded.sort_order, is_active=true, station_id=coalesce(public.categories.station_id, excluded.station_id);")
order={}
for it in M:
    iid = it["id"] or slug(it["name"]); assert iid not in ids, iid; ids.add(iid)
    order[it["cat"]] = order.get(it["cat"],0)+1
    opts=[]; base=None
    if it["var"]:
        g,b,lbl = it["var"]; base=b; opts.append({"id":lbl.lower(),"name":lbl,"pricePaise":(it["p2"]-it["p"])*100,"group":g})
    if it["extra"]:
        g,b,lst = it["extra"]; base=b; opts += [{"id":oid,"name":on,"pricePaise":pp*100,"group":g} for oid,on,pp in lst]
    verified = "false"   # owner confirms description / allergens / photo (and Half/Full labels) in Admin
    out.append("insert into public.menu_items(id,name,category_id,description,price_paise,food_type,options,variant_base_label,sort_order,details_verified,is_archived) values ("
      f"{q(iid)},{q(it['name'])},{q(it['cat'])},{q(it['desc'])},{it['p']*100},'{'VEG' if it['food']=='V' else 'NON_VEG'}',{q(json.dumps(opts))}::jsonb,{q(base)},{order[it['cat']]},{verified},false) "
      "on conflict (id) do update set name=excluded.name, category_id=excluded.category_id, price_paise=excluded.price_paise, food_type=excluded.food_type, "
      "options=excluded.options, variant_base_label=excluded.variant_base_label, sort_order=excluded.sort_order, is_archived=false, "
      "description=coalesce(excluded.description, public.menu_items.description);")
out.append("update public.categories set is_active=false where id in ('vegetarian','paneer','chicken') and not exists (select 1 from public.menu_items m where m.category_id=categories.id and not m.is_archived);")
out.append("commit;")
open(os.path.join(os.path.dirname(os.path.abspath(__file__)),"swad_sangam_menu.sql"),"w").write("\n".join(out)+"\n")
print(len(CATEGORIES),"categories,",len(M),"dishes")
