import QRCode from "qrcode";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { rupees, time } from "../lib/format";
import { AccountView, FeedbackCard, ReviewsCard, useCustomer } from "./Account";
import { db as supabase, store, type Customer } from "./common";
import type { Category, MenuItem, Order } from "../lib/types";
import { t, type Key, type Lang } from "./i18n";

const APK_URL = "https://github.com/annustarkac-hub/Swad-Sangam/releases/latest/download/swad-sangam.apk";

interface Settings {
  restaurant_name: string; tagline: string; phone: string; address: string; maps_query: string; latitude: number | null; longitude: number | null;
  opening_hours: Record<string, { open: string; close: string }[]>; announcement: string | null; accepting_orders: boolean;
  pickup_enabled: boolean; dine_in_enabled: boolean; delivery_enabled: boolean; cash_enabled: boolean; upi_at_restaurant_enabled: boolean;
  upi_id: string | null; whatsapp_phone: string | null; default_prep_minutes: number; rating: number | null; review_count: number | null; reviews_url: string | null; temporarily_closed: boolean;
  closure_message: string | null; hero_image_url: string | null; hero_image_credit: string | null; cost_for_two_paise: number | null; min_order_paise: number; max_item_quantity: number; fssai_number: string | null; delivery_area_note: string | null;
  require_table_code: boolean;
}
interface Quote { subtotalPaise: number; discountPaise: number; taxPaise: number; packagingFeePaise: number; deliveryFeePaise: number; totalPaise: number; couponError: string | null; minOrderPaise: number }
interface Line { itemId: string; qty: number; optionIds: string[] }
type View = { name: "menu" } | { name: "orders" } | { name: "info" } | { name: "account" } | { name: "order"; token: string };
type L = (k: Key) => string;

function parseHash(): View {
  const h = location.hash.replace(/^#\/?/, "");
  if (h.startsWith("order/")) return { name: "order", token: h.slice(6) };
  if (h === "orders" || h === "info" || h === "account") return { name: h };
  return { name: "menu" };
}
const go = (path: string) => { location.hash = "#/" + path; window.scrollTo({ top: 0 }); };
const mapsUrl = (s: Settings) => "https://www.google.com/maps/dir/?api=1&destination=" +
  encodeURIComponent(s.latitude != null && s.longitude != null ? `${s.latitude},${s.longitude}` : s.maps_query);
const upiLink = (upi: string, name: string, paise: number, note: string) =>
  `upi://pay?${new URLSearchParams({ pa: upi, pn: name, am: (paise / 100).toFixed(2), cu: "INR", tn: note }).toString()}`;
const lineKey = (l: { itemId: string; optionIds: string[] }) => l.itemId + "|" + [...l.optionIds].sort().join(",");

function isOpen(s: Settings): boolean {
  const now = new Date(new Date().toLocaleString("en-US", { timeZone: "Asia/Kolkata" }));
  const dow = ((now.getDay() + 6) % 7) + 1;
  const mins = now.getHours() * 60 + now.getMinutes();
  const m = (x: string) => { const [h, mm] = x.split(":").map(Number); return h * 60 + mm; };
  const today = s.opening_hours?.[String(dow)] ?? [];
  const prev = s.opening_hours?.[String(dow === 1 ? 7 : dow - 1)] ?? [];
  return !s.temporarily_closed && (today.some((x) => (m(x.close) > m(x.open) ? mins >= m(x.open) && mins < m(x.close) : mins >= m(x.open)))
    || prev.some((x) => m(x.close) <= m(x.open) && mins < m(x.close)));
}

/** "Half ₹120 · Full ₹199" for size variants, otherwise the base price. */
function priceText(i: MenuItem): string {
  const sizes = i.options.filter((o) => o.group === "size");
  if (sizes.length) return [`${i.variant_base_label ?? "Regular"} ${rupees(i.price_paise)}`, ...sizes.map((o) => `${o.name} ${rupees(i.price_paise + o.pricePaise)}`)].join(" · ");
  return rupees(i.price_paise);
}
function variantHint(i: MenuItem): string | null {
  const g = i.options.filter((o) => o.group && o.group !== "size");
  return g.length ? [i.variant_base_label, ...g.map((o) => o.name)].filter(Boolean).join(" / ") : null;
}

/** Dish photo that falls back to a neat placeholder if the image cannot load (never shows broken-image text). */
function DishImg({ src, className }: { src: string | null; className?: string }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);
  if (!src || failed) return <div className={"ph-s" + (className ? " " + className : "")} aria-hidden="true">🍛</div>;
  return <img src={src} className={className} alt="" loading="lazy" decoding="async" onError={() => setFailed(true)} />;
}

function Veg({ type }: { type: "VEG" | "NON_VEG" }) {
  return <span className={type === "VEG" ? "veg" : "nonveg"} role="img" aria-label={type === "VEG" ? "Vegetarian" : "Non-vegetarian"} />;
}

function Sheet({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  useEffect(() => { document.body.style.overflow = "hidden"; return () => { document.body.style.overflow = ""; }; }, []);
  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="sheet-grip" />
        <div className="sheet-head"><h2>{title}</h2><button className="icon-btn" aria-label="Close" onClick={onClose}>✕</button></div>
        {children}
      </div>
    </div>
  );
}

export default function SiteApp() {
  const [lang, setLang] = useState<Lang>(store.get("ss_lang", navigator.language.startsWith("hi") ? "hi" : "en"));
  const L: L = (k) => t(lang, k);
  const [view, setView] = useState<View>(parseHash());
  const [settings, setSettings] = useState<Settings | null>(store.get("ss_settings", null));
  const [cats, setCats] = useState<Category[]>(store.get("ss_cats", []));
  const [items, setItems] = useState<MenuItem[]>(store.get("ss_items", []));
  const [offline, setOffline] = useState(false);
  const [cart, setCart] = useState<Line[]>(store.get("ss_cart", []));
  const [table, setTable] = useState<string | null>(() => new URLSearchParams(location.search).get("table") ?? store.get<string | null>("ss_table", null));
  const [cartOpen, setCartOpen] = useState(false);
  const [picker, setPicker] = useState<MenuItem | null>(null);
  const [mode, setModeState] = useState<Mode>(() => (new URLSearchParams(location.search).get("table") ? "DINE_IN" : store.get<Mode>("ss_mode", "PICKUP")));
  const setMode = (m: Mode) => { setModeState(m); store.set("ss_mode", m); };
  const [installEvt, setInstallEvt] = useState<(Event & { prompt?: () => void }) | null>(null);
  const [customer, reloadCustomer, recovery] = useCustomer();
  useEffect(() => { if (recovery) go("account"); }, [recovery]);

  useEffect(() => { const f = () => { setView(parseHash()); setCartOpen(false); setPicker(null); }; window.addEventListener("hashchange", f); return () => window.removeEventListener("hashchange", f); }, []);
  useEffect(() => store.set("ss_cart", cart), [cart]);
  useEffect(() => { store.set("ss_lang", lang); document.documentElement.lang = lang; }, [lang]);
  useEffect(() => { store.set("ss_table", table); }, [table]);
  useEffect(() => {
    const f = (e: Event) => { e.preventDefault(); setInstallEvt(e); };
    window.addEventListener("beforeinstallprompt", f);
    return () => window.removeEventListener("beforeinstallprompt", f);
  }, []);

  const loadMenu = useCallback(async () => {
    try {
      const [s, c, i] = await Promise.all([
        supabase.from("restaurant_settings").select("*").eq("id", 1).single(),
        supabase.from("categories").select("*").order("sort_order"),
        supabase.from("menu_items").select("*").order("sort_order"),
      ]);
      if (s.error || c.error || i.error) throw new Error("load");
      setSettings(s.data as Settings); store.set("ss_settings", s.data);
      setCats(c.data as Category[]); store.set("ss_cats", c.data);
      setItems(i.data as MenuItem[]); store.set("ss_items", i.data);
      setOffline(false);
    } catch { setOffline(true); }
  }, []);
  useEffect(() => { void loadMenu(); const id = setInterval(() => void loadMenu(), 120000); return () => clearInterval(id); }, [loadMenu]);

  const byId = useMemo(() => Object.fromEntries(items.map((i) => [i.id, i])), [items]);
  const count = cart.reduce((s, l) => s + l.qty, 0);
  const estimate = cart.reduce((s, l) => {
    const i = byId[l.itemId]; if (!i) return s;
    return s + (i.price_paise + i.options.filter((o) => l.optionIds.includes(o.id)).reduce((a, o) => a + o.pricePaise, 0)) * l.qty;
  }, 0);
  const max = settings?.max_item_quantity ?? 20;
  const change = (itemId: string, optionIds: string[], delta: number) => setCart((c) => {
    const k = lineKey({ itemId, optionIds });
    const ex = c.find((l) => lineKey(l) === k);
    if (!ex) return delta > 0 ? [...c, { itemId, optionIds, qty: Math.min(max, delta) }] : c;
    const q = Math.min(max, ex.qty + delta);
    return q <= 0 ? c.filter((l) => l !== ex) : c.map((l) => (l === ex ? { ...l, qty: q } : l));
  });
  const ordering = !!settings && settings.accepting_orders && (settings.pickup_enabled || settings.dine_in_enabled || settings.delivery_enabled) && isOpen(settings);
  const open = settings ? isOpen(settings) : false;

  return (
    <div className="app">
      <header className="topbar-s">
        <button className="brand-s" onClick={() => go("")}>
          <span className="logo-s">SS</span>
          <span><b>{settings?.restaurant_name ?? "Swad Sangam"}</b>
            <small>{settings ? <><i className={"dot " + (open ? "on" : "off")} />{open ? L("openNow") : L("closedNow")}</> : " "}</small></span>
        </button>
        <div className="top-actions">
          {table && <span className="table-chip">🍽 {table}</span>}
          <button className="icon-btn" onClick={() => setLang(lang === "en" ? "hi" : "en")} aria-label="Language">{lang === "en" ? "हि" : "EN"}</button>
        </div>
      </header>

      {offline && <div className="banner-s warn">{L("offline")}</div>}
      {settings?.temporarily_closed && <div className="banner-s bad">{settings.closure_message ?? L("closedNow")}</div>}
      {settings?.announcement && <div className="banner-s">{settings.announcement}</div>}

      <main className="page">
        {view.name === "menu" && (
          <div className="layout">
            <div className="col-main">
              <MenuView L={L} lang={lang} settings={settings} cats={cats} items={items} cart={cart} canOrder={ordering}
                table={table} installEvt={installEvt} mode={mode} setMode={setMode} onOpen={(i) => setPicker(i)} onChange={change} />
            </div>
            <DeskCart L={L} lang={lang} cart={cart} byId={byId} estimate={estimate} onChange={change} onCheckout={() => setCartOpen(true)} />
          </div>
        )}
        {view.name === "order" && <OrderView L={L} lang={lang} token={view.token} settings={settings} />}
        {view.name === "account" && <AccountView lang={lang} customer={customer} reload={reloadCustomer} recovery={recovery} />}
        {view.name === "orders" && <OrdersView L={L} lang={lang} customer={customer} onReorder={(o) => { o.items.forEach((i) => byId[i.itemId]?.is_available && change(i.itemId, [], i.quantity)); go(""); setCartOpen(true); }} />}
        {view.name === "info" && settings && <InfoView L={L} lang={lang} s={settings} />}
      </main>

      {count > 0 && view.name === "menu" && (
        <button className="cart-fab" onClick={() => setCartOpen(true)}>
          <span className="cart-count">{count}</span>
          <span>{L("viewCart")}</span>
          <b>{rupees(estimate)} →</b>
        </button>
      )}

      <nav className="tabbar-s">
        {([["", "menu", "🍛"], ["orders", "myOrders", "🧾"], ["account", customer ? "account" : "login", "👤"], ["info", "info", "📍"]] as [string, Key, string][]).map(([p, k, ic]) => (
          <button key={p} className={view.name === (p || "menu") ? "on" : ""} onClick={() => go(p)}><span>{ic}</span>{L(k)}</button>
        ))}
      </nav>

      {picker && <DishSheet L={L} lang={lang} item={picker} canOrder={ordering} onClose={() => setPicker(null)} onAdd={(ids, q) => { change(picker.id, ids, q); setPicker(null); }} />}
      {cartOpen && settings && (
        <CartSheet L={L} lang={lang} customer={customer ?? null} cart={cart} byId={byId} settings={settings} table={table} setTable={setTable} canOrder={ordering} mode={mode}
          onChange={change} onClose={() => setCartOpen(false)}
          onPlaced={(token) => { setCart([]); setCartOpen(false); go("order/" + token); }} />
      )}
    </div>
  );
}

type Mode = "DINE_IN" | "PICKUP" | "DELIVERY";
type Filter = "veg" | "nonveg" | "under100" | "best";

function MenuView({ L, lang, settings, cats, items, cart, canOrder, table, installEvt, mode, setMode, onOpen, onChange }: {
  L: L; lang: Lang; settings: Settings | null; cats: Category[]; items: MenuItem[]; cart: Line[]; canOrder: boolean; table: string | null;
  installEvt: (Event & { prompt?: () => void }) | null; mode: Mode; setMode: (m: Mode) => void;
  onOpen: (i: MenuItem) => void; onChange: (id: string, opts: string[], d: number) => void;
}) {
  const [q, setQ] = useState("");
  const [filters, setFilters] = useState<Filter[]>([]);
  const [sort, setSort] = useState<"" | "asc" | "desc">("");
  const [active, setActive] = useState<string>("");
  const refs = useRef<Record<string, HTMLElement | null>>({});
  const hi = lang === "hi";
  const name = (i: { name: string; name_hi?: string | null }) => (hi && i.name_hi ? i.name_hi : i.name);
  const desc = (i: MenuItem) => (hi ? i.description_hi ?? i.description : i.description);
  const has = (f: Filter) => filters.includes(f);
  const toggle = (f: Filter) => setFilters((x) => (x.includes(f) ? x.filter((y) => y !== f) : [...x.filter((y) => !(f === "veg" && y === "nonveg") && !(f === "nonveg" && y === "veg")), f]));
  let shown = items.filter((i) => (!has("veg") || i.food_type === "VEG") && (!has("nonveg") || i.food_type === "NON_VEG")
    && (!has("under100") || i.price_paise <= 10000) && (!has("best") || i.is_popular)
    && (!q || (i.name + " " + (i.name_hi ?? "") + " " + (i.description ?? "")).toLowerCase().includes(q.toLowerCase())));
  if (sort) shown = [...shown].sort((a, b) => (sort === "asc" ? a.price_paise - b.price_paise : b.price_paise - a.price_paise));
  const featured = items.filter((i) => i.is_available && i.image_url && (i.is_popular || i.category_id === "thalis")).slice(0, 8);
  const groups = cats.map((c) => ({ c, list: shown.filter((i) => i.category_id === c.id) })).filter((g) => g.list.length);
  const catImg = (id: string) => items.find((i) => i.category_id === id && i.image_url)?.image_url;
  const qtyPlain = (id: string) => cart.find((l) => l.itemId === id && l.optionIds.length === 0)?.qty ?? 0;
  const qtyAny = (id: string) => cart.filter((l) => l.itemId === id).reduce((s, l) => s + l.qty, 0);
  const modes = ([["DINE_IN", "🍽", settings?.dine_in_enabled], ["PICKUP", "🥡", settings?.pickup_enabled], ["DELIVERY", "🛵", settings?.delivery_enabled]] as [Mode, string, boolean | undefined][]);
  const hero = settings?.hero_image_url ?? null;

  useEffect(() => {
    const obs = new IntersectionObserver((es) => {
      const v = es.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0];
      if (v) setActive(v.target.id.replace("cat-", ""));
    }, { rootMargin: "-140px 0px -60% 0px" });
    Object.values(refs.current).forEach((el) => el && obs.observe(el));
    return () => obs.disconnect();
  }, [groups.length]);
  const jump = (id: string) => refs.current[id]?.scrollIntoView({ behavior: "smooth", block: "start" });

  return (
    <>
      <section className="banner">
        {hero && <img src={hero} alt="" className="banner-img" onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }} />}
        <div className="banner-shade" />
        <div className="banner-text">
          <span className="kicker">North Indian · Thalis · Snacks · Chinese</span>
          <h1>{settings?.restaurant_name ?? "Swad Sangam"}</h1>
          <p>{settings?.tagline ?? L("tagline")}</p>
        </div>
      </section>
      <section className="info-card">
        <div className="info-row">
          <div>
            <div className="info-title">{settings?.restaurant_name ?? "Swad Sangam"}</div>
            <div className="muted-s small">{settings?.address ?? "Bhaktiyana, Pauri Garhwal, Uttarakhand"}</div>
          </div>
          {settings?.rating != null && <div className="rating-box"><b>{settings.rating} ★</b><small>{settings.review_count ?? "–"} {L("reviews")}</small></div>}
        </div>
        <div className="info-meta">
          {settings && <span><i className={"dot " + (isOpen(settings) ? "on" : "off")} /> {isOpen(settings) ? L("openNow") : L("closedNow")}</span>}
          {settings?.cost_for_two_paise ? <span>₹ {rupees(settings.cost_for_two_paise)} for two</span> : null}
          {settings?.fssai_number && <span>FSSAI {settings.fssai_number}</span>}
        </div>
        <div className="hero-row">
          {settings && <a className="pill-s" href={`tel:${settings.phone}`}>📞 {L("call")}</a>}
          {settings && <a className="pill-s" href={mapsUrl(settings)} target="_blank" rel="noreferrer">📍 {L("directions")}</a>}
          {installEvt ? <button className="pill-s gold" onClick={() => installEvt.prompt?.()}>⬇ {L("install")}</button>
            : <a className="pill-s gold" href={APK_URL}>⬇ {L("getApp")}</a>}
        </div>
        {settings && canOrder && modes.filter((m) => m[2]).length > 1 && (
          <div className="mode-switch">
            {modes.filter((m) => m[2]).map(([m, ic]) => <button key={m} className={mode === m ? "on" : ""} onClick={() => setMode(m)}>{ic} {L(m)}</button>)}
          </div>
        )}
        {settings && !canOrder && <div className="banner-s inline">{L("orderingOff")} <a href={`tel:${settings.phone}`}>{settings.phone}</a></div>}
      </section>
      {table && <TableBar table={table} L={L} lang={lang} />}

      {!q && (
        <button className="promo" onClick={() => { const first = cats.find((c) => items.some((i) => i.category_id === c.id)); if (first) jump(first.id); }}
          aria-label={hi ? "मेन्यू देखें" : "See the menu"}>
          <img src={`${import.meta.env.BASE_URL}promo-banner.webp`} alt="Swad Sangam — Restaurant & Fast Food: burger, momos, spring rolls, paratha, tea" loading="lazy" width={1600} height={533}
            onError={(e) => { ((e.target as HTMLImageElement).parentElement as HTMLElement).style.display = "none"; }} />
        </button>
      )}

      {!q && cats.length > 0 && (
        <section>
          <h2 className="sec">{hi ? "आज क्या खाएँगे?" : "What's on your mind?"}</h2>
          <div className="cat-circles">
            {cats.filter((c) => items.some((i) => i.category_id === c.id)).map((c) => (
              <button key={c.id} onClick={() => jump(c.id)}>
                <span className="circle"><DishImg src={catImg(c.id) ?? null} /></span>{name(c)}
              </button>
            ))}
          </div>
        </section>
      )}

      {!q && featured.length > 0 && (
        <section>
          <h2 className="sec">{hi ? "ख़ास पसंद" : "Recommended for you"}</h2>
          <div className="feat-row">
            {featured.map((i) => (
              <article key={i.id} className="feat-card" onClick={() => onOpen(i)}>
                <DishImg src={i.image_url} />
                <div className="feat-shade" />
                <div className="feat-text"><Veg type={i.food_type} /><b>{name(i)}</b><span>{i.options.some((o) => o.group === "size") ? "from " : ""}{rupees(i.price_paise)}</span></div>
              </article>
            ))}
          </div>
        </section>
      )}

      <div className="sticky-tools">
        <div className="search-s"><span>🔍</span><input placeholder={L("search")} value={q} onChange={(e) => setQ(e.target.value)} />{q && <button className="link-s" onClick={() => setQ("")}>✕</button>}</div>
        <div className="filter-row">
          <button className={"chip-f" + (has("veg") ? " on veg-on" : "")} onClick={() => toggle("veg")}><Veg type="VEG" /> {hi ? "शाकाहारी" : "Veg"}</button>
          <button className={"chip-f" + (has("nonveg") ? " on" : "")} onClick={() => toggle("nonveg")}><Veg type="NON_VEG" /> {hi ? "मांसाहारी" : "Non-veg"}</button>
          <button className={"chip-f" + (has("under100") ? " on" : "")} onClick={() => toggle("under100")}>{hi ? "₹100 से कम" : "Under ₹100"}</button>
          <button className={"chip-f" + (has("best") ? " on" : "")} onClick={() => toggle("best")}>★ {L("bestsellers")}</button>
          <button className={"chip-f" + (sort ? " on" : "")} onClick={() => setSort(sort === "" ? "asc" : sort === "asc" ? "desc" : "")}>
            {sort === "asc" ? "₹ Low → High" : sort === "desc" ? "₹ High → Low" : (hi ? "क्रम" : "Sort")}</button>
        </div>
        <div className="cat-tabs">
          {groups.map(({ c }) => <button key={c.id} className={active === c.id ? "on" : ""} onClick={() => jump(c.id)}>{name(c)}</button>)}
        </div>
      </div>

      {groups.map(({ c, list }) => (
        <section key={c.id} id={`cat-${c.id}`} ref={(el) => { refs.current[c.id] = el; }} className="cat-sec">
          <h2 className="sec">{name(c)} <span className="muted-s">({list.length})</span></h2>
          {list.map((i) => {
            const plain = qtyPlain(i.id);
            return (
              <article key={i.id} className={"dish-s" + (i.is_available ? "" : " sold")}>
                <div className="dish-info" onClick={() => onOpen(i)}>
                  <div className="dish-top"><Veg type={i.food_type} />{i.is_popular && <span className="tag-s">★ {L("bestsellers")}</span>}</div>
                  <h3>{name(i)}</h3>
                  <div className="price-s">{priceText(i)}</div>
                  {desc(i) && <p>{desc(i)}</p>}
                  {variantHint(i) && <span className="custom-s">{variantHint(i)}</span>}
                </div>
                <div className="dish-media">
                  <button className="img-btn" onClick={() => onOpen(i)} aria-label={name(i)}>
                    <DishImg src={i.image_url} />
                  </button>
                  {!i.is_available ? <span className="sold-badge">{L("soldOut")}</span>
                    : canOrder && (i.options.length === 0 && plain > 0
                      ? <div className="stepper-s"><button onClick={() => onChange(i.id, [], -1)}>−</button><b>{plain}</b><button onClick={() => onChange(i.id, [], 1)}>+</button></div>
                      : <button className="add-s" onClick={() => (i.options.length ? onOpen(i) : onChange(i.id, [], 1))}>{L("add")}{qtyAny(i.id) ? ` · ${qtyAny(i.id)}` : ""}<sup>+</sup></button>)}
                </div>
              </article>
            );
          })}
        </section>
      ))}
      {groups.length === 0 && <p className="empty-s">{hi ? "कोई डिश नहीं मिली" : "No dishes match. Try another search or filter."}</p>}
    </>
  );
}

function DishSheet({ L, lang, item, canOrder, onClose, onAdd }: { L: L; lang: Lang; item: MenuItem; canOrder: boolean; onClose: () => void; onAdd: (ids: string[], qty: number) => void }) {
  const opts = item.options.filter((o) => o.isAvailable !== false);
  const groups = [...new Set(opts.filter((o) => o.group).map((o) => o.group!))];
  const addons = opts.filter((o) => !o.group);
  const [sel, setSel] = useState<string[]>([]);
  const pick = (g: string, id: string | null) => setSel((x) => [...x.filter((y) => opts.find((o) => o.id === y)?.group !== g), ...(id ? [id] : [])]);
  const [qty, setQty] = useState(1);
  const hi = lang === "hi";
  const total = (item.price_paise + opts.filter((o) => sel.includes(o.id)).reduce((s, o) => s + o.pricePaise, 0)) * qty;
  return (
    <Sheet title={hi && item.name_hi ? item.name_hi : item.name} onClose={onClose}>
      {item.image_url && <div className="sheet-img-wrap"><DishImg className="sheet-img" src={item.image_url} />
        </div>}
      <div className="row-s" style={{ marginTop: 10 }}><Veg type={item.food_type} /><b className="price-s">{rupees(item.price_paise)}</b>{item.is_popular && <span className="tag-s">★ {L("bestsellers")}</span>}</div>
      {(hi ? item.description_hi ?? item.description : item.description) && <p className="muted-s">{hi ? item.description_hi ?? item.description : item.description}</p>}
      {item.portion_info && <p className="small"><b>{hi ? "मात्रा" : "Portion"}:</b> {item.portion_info}</p>}
      {item.preparation_minutes && <p className="small"><b>{hi ? "समय" : "Ready in"}:</b> ~{item.preparation_minutes} min</p>}
      <p className="small"><b>{hi ? "एलर्जी" : "Allergens"}:</b> {item.allergens ?? (hi ? "एलर्जी हो तो स्टाफ़ को बताएँ" : "Ask our staff if you have an allergy")}</p>
      {groups.map((g) => (
        <div key={g}>
          <div className="lbl">{g === "size" ? (hi ? "मात्रा चुनें" : "Choose portion") : (hi ? "विकल्प चुनें" : "Choose one")}</div>
          <div className="variant-row">
            <button className={!sel.some((id) => opts.find((o) => o.id === id)?.group === g) ? "on" : ""} onClick={() => pick(g, null)}>
              {item.variant_base_label ?? (hi ? "सामान्य" : "Regular")}<small>{rupees(item.price_paise)}</small></button>
            {opts.filter((o) => o.group === g).map((o) => (
              <button key={o.id} className={sel.includes(o.id) ? "on" : ""} onClick={() => pick(g, o.id)}>{o.name}<small>{rupees(item.price_paise + o.pricePaise)}</small></button>
            ))}
          </div>
        </div>
      ))}
      {addons.length > 0 && <div className="lbl">{hi ? "अतिरिक्त" : "Add-ons"}</div>}
      {addons.map((o) => (
        <label key={o.id} className="opt-row">
          <input type="checkbox" checked={sel.includes(o.id)} onChange={(e) => setSel(e.target.checked ? [...sel, o.id] : sel.filter((x) => x !== o.id))} />
          <span>{o.name}</span><b>{o.pricePaise ? `+${rupees(o.pricePaise)}` : ""}</b>
        </label>
      ))}
      {!item.is_available ? <p className="bad-s">{L("soldOut")}</p> : canOrder && (
        <div className="sheet-foot">
          <div className="stepper-s big"><button onClick={() => setQty(Math.max(1, qty - 1))}>−</button><b>{qty}</b><button onClick={() => setQty(qty + 1)}>+</button></div>
          <button className="primary-s" onClick={() => onAdd(sel, qty)}>{L("addToOrder")} · {rupees(total)}</button>
        </div>
      )}
    </Sheet>
  );
}

/** Desktop: order summary beside the menu. */
function DeskCart({ L, lang, cart, byId, estimate, onChange, onCheckout }: { L: L; lang: Lang; cart: Line[]; byId: Record<string, MenuItem>; estimate: number; onChange: (id: string, o: string[], d: number) => void; onCheckout: () => void }) {
  const lines = cart.filter((l) => byId[l.itemId]);
  return (
    <aside className="desk-side">
      <img className="promo-side" src={`${import.meta.env.BASE_URL}promo-banner.webp`} alt="Swad Sangam — Restaurant & Fast Food" width={1600} height={533}
        onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }} />
    <div className="desk-cart">
      <h3>{L("cart")}</h3>
      {!lines.length && <p className="muted-s small">{L("emptyCart")}</p>}
      {lines.map((l) => {
        const i = byId[l.itemId];
        return (
          <div key={lineKey(l)} className="cart-line">
            <Veg type={i.food_type} /><div className="grow small">{lang === "hi" && i.name_hi ? i.name_hi : i.name}</div>
            <div className="stepper-s"><button onClick={() => onChange(l.itemId, l.optionIds, -1)}>−</button><b>{l.qty}</b><button onClick={() => onChange(l.itemId, l.optionIds, 1)}>+</button></div>
          </div>
        );
      })}
      {lines.length > 0 && <button className="primary-s wide" onClick={onCheckout}>{L("checkout")} · {rupees(estimate)}</button>}
    </div>
    </aside>
  );
}

function CartSheet({ L, lang, customer, cart, byId, settings, table, setTable, canOrder, mode, onChange, onClose, onPlaced }: {
  L: L; lang: Lang; customer: Customer | null; cart: Line[]; byId: Record<string, MenuItem>; settings: Settings; table: string | null; setTable: (t: string | null) => void; mode: string;
  canOrder: boolean; onChange: (id: string, opts: string[], d: number) => void; onClose: () => void; onPlaced: (token: string) => void;
}) {
  const types = (["DINE_IN", "PICKUP", "DELIVERY"] as const).filter((x) => (x === "PICKUP" && settings.pickup_enabled) || (x === "DINE_IN" && settings.dine_in_enabled) || (x === "DELIVERY" && settings.delivery_enabled));
  const pays = (["CASH", "UPI_AT_RESTAURANT"] as const).filter((p) => (p === "CASH" && settings.cash_enabled) || (p === "UPI_AT_RESTAURANT" && settings.upi_at_restaurant_enabled));
  const saved = customer?.profile ? { name: customer.profile.full_name, phone: customer.profile.phone } : store.get("ss_customer", { name: "", phone: "" });
  const defAddr = customer?.addresses.find((a) => a.is_default) ?? customer?.addresses[0];
  const [addrId, setAddrId] = useState<number | "new">(defAddr ? defAddr.id : "new");
  const [saveAddr, setSaveAddr] = useState(true);
  const [f, setF] = useState({ name: saved.name, phone: saved.phone, landmark: "", type: ((types as readonly string[]).includes(mode) ? mode : table && types.includes("DINE_IN") ? "DINE_IN" : types.find((x) => x !== "DINE_IN") ?? types[0]) as string,
    tableCode: store.get("ss_table_code", ""), address: "", notes: "", pay: (pays.includes("UPI_AT_RESTAURANT") ? "UPI_AT_RESTAURANT" : pays[0]) as string, coupon: "", terms: false });
  const [tables, setTables] = useState<{ name: string; seats: number; free: boolean }[]>([]);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const lines = cart.filter((l) => byId[l.itemId]);
  const items = lines.map((l) => ({ itemId: l.itemId, quantity: l.qty, optionIds: l.optionIds }));

  useEffect(() => {
    if (f.type === "DINE_IN" && !table) supabase.rpc("public_tables").then(({ data }) => setTables((data as typeof tables) ?? []));
  }, [f.type, table]);
  useEffect(() => {
    if (!lines.length) { setQuote(null); return; }
    const id = setTimeout(() => {
      supabase.rpc("quote_order", { p_order: { orderType: f.type, items, couponCode: f.coupon || null } }).then(({ data }) => data && setQuote(data as Quote));
    }, 250);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(items), f.type, f.coupon]);

  async function place() {
    setErr(null);
    const ph = f.phone.replace(/\D/g, "").slice(-10);
    if (!/^[6-9]\d{9}$/.test(ph) || f.name.trim().length < 2) return setErr(lang === "hi" ? "नाम और सही मोबाइल नंबर लिखें।" : "Please enter your name and a valid 10-digit mobile number.");
    if (f.type === "DINE_IN" && !table) return setErr(L("chooseTable"));
    const chosen = addrId === "new" ? null : customer?.addresses.find((a) => a.id === addrId) ?? null;
    const address = chosen ? chosen.address : f.address.trim();
    const landmark = chosen ? chosen.landmark : f.landmark.trim() || null;
    if (f.type === "DELIVERY" && address.length < 10) return setErr(lang === "hi" ? "पूरा डिलीवरी पता लिखें।" : "Please write the full delivery address.");
    if (!f.terms) return setErr(L("terms"));
    setBusy(true);
    const key = store.get<string | null>("ss_pending_key", null) ?? crypto.randomUUID();
    store.set("ss_pending_key", key);
    try {
      const { data, error } = await supabase.rpc("place_order", { p_order: {
        idempotencyKey: key, customerName: f.name, customerPhone: f.phone, orderType: f.type, paymentMethod: f.pay, items,
        termsAccepted: f.terms, tableNumber: f.type === "DINE_IN" ? table : null, tableCode: f.type === "DINE_IN" ? f.tableCode : null,
        deliveryAddress: f.type === "DELIVERY" ? address : null, deliveryLandmark: f.type === "DELIVERY" ? landmark : null, notes: f.notes || null, couponCode: f.coupon || null,
        expectedTotalPaise: quote?.totalPaise } });
      if (error) throw new Error(error.message);
      const r = data as { ok: boolean; error?: string; message?: string; order?: { accessToken: string } };
      localStorage.removeItem("ss_pending_key");
      if (!r.ok) { setErr(r.message ?? "Order could not be placed"); if (r.error === "PRICE_CHANGED") setQuote(null); return; }
      store.set("ss_customer", { name: f.name, phone: f.phone });
      if (customer && f.type === "DELIVERY" && !chosen && saveAddr && customer.addresses.length < 10)
        void supabase.from("customer_addresses").insert({ label: customer.addresses.length ? "Address " + (customer.addresses.length + 1) : "Home", address, landmark, is_default: customer.addresses.length === 0 });
      store.set("ss_tokens", [r.order!.accessToken, ...store.get<string[]>("ss_tokens", [])].slice(0, 50));
      onPlaced(r.order!.accessToken);
    } catch {
      setErr(lang === "hi" ? "इंटरनेट नहीं है। ऑर्डर अभी पक्का नहीं हुआ — फिर से दबाएँ (दो बार नहीं बनेगा)।"
        : "No internet. Your order is NOT confirmed yet — tap again (it will not be duplicated).");
    } finally { setBusy(false); }
  }

  return (
    <Sheet title={L("cart")} onClose={onClose}>
      {!lines.length && <p className="empty-s">{L("emptyCart")}</p>}
      {lines.map((l) => {
        const i = byId[l.itemId];
        const opts = i.options.filter((o) => l.optionIds.includes(o.id));
        const unit = i.price_paise + opts.reduce((s, o) => s + o.pricePaise, 0);
        return (
          <div key={lineKey(l)} className="cart-line">
            <Veg type={i.food_type} />
            <div className="grow">{lang === "hi" && i.name_hi ? i.name_hi : i.name}
              {(opts.length > 0 || i.variant_base_label) && <small> · {[...(i.options.some((o) => o.group) && !opts.some((o) => o.group) ? [i.variant_base_label] : []), ...opts.map((o) => o.name)].filter(Boolean).join(", ")}</small>}
              {!i.is_available && <div className="bad-s">{L("soldOut")}</div>}</div>
            <div className="stepper-s"><button onClick={() => onChange(l.itemId, l.optionIds, -1)}>−</button><b>{l.qty}</b><button onClick={() => onChange(l.itemId, l.optionIds, 1)}>+</button></div>
            <b className="amt">{rupees(unit * l.qty)}</b>
          </div>
        );
      })}

      {lines.length > 0 && !canOrder && <div className="banner-s inline">{L("orderingOff")} <a href={`tel:${settings.phone}`}>{settings.phone}</a></div>}
      {lines.length > 0 && canOrder && (
        <div className="form-s">
          <div className="seg">{types.map((x) => <button key={x} className={f.type === x ? "on" : ""} onClick={() => setF({ ...f, type: x })}>{L(x)}</button>)}</div>
          {f.type === "DINE_IN" && (table
            ? <div className="row-s">🍽 {L("table")} <b>{table}</b> <button className="link-s" onClick={() => setTable(null)}>change</button></div>
            : <div><div className="lbl">{L("chooseTable")}</div>
                <div className="table-grid">{tables.map((x) => (
                  <button key={x.name} className={x.free ? "" : "busy"} onClick={() => setTable(x.name)}>{x.name}<small>{x.seats} · {x.free ? L("free") : "—"}</small></button>
                ))}</div></div>)}
          {f.type === "DINE_IN" && settings.require_table_code && (
            <label>{L("tableCode")}<input inputMode="numeric" maxLength={4} value={f.tableCode} onChange={(e) => { setF({ ...f, tableCode: e.target.value }); store.set("ss_table_code", e.target.value); }} /></label>)}
          <h3>{L("details")}</h3>
          <label>{L("name")}<input value={f.name} autoComplete="name" onChange={(e) => setF({ ...f, name: e.target.value })} /></label>
          <label>{L("phone")}<input value={f.phone} inputMode="tel" autoComplete="tel" onChange={(e) => setF({ ...f, phone: e.target.value })} /></label>
          {f.type === "DELIVERY" && <>
            {customer && customer.addresses.length > 0 && (
              <div><div className="lbl">{L("address")}</div>
                <div className="addr-pick">{customer.addresses.map((a) => (
                  <button key={a.id} className={addrId === a.id ? "on" : ""} onClick={() => setAddrId(a.id)}><b>{a.label}</b><small>{a.address}</small></button>
                ))}<button className={addrId === "new" ? "on" : ""} onClick={() => setAddrId("new")}><b>+ {lang === "hi" ? "नया पता" : "New address"}</b></button></div></div>)}
            {addrId === "new" && <>
              <label>{L("address")}<textarea value={f.address} onChange={(e) => setF({ ...f, address: e.target.value })} /></label>
              <label>{lang === "hi" ? "लैंडमार्क (वैकल्पिक)" : "Landmark (optional)"}<input value={f.landmark} onChange={(e) => setF({ ...f, landmark: e.target.value })} /></label>
              {customer && customer.addresses.length < 10 && <label className="check-s"><input type="checkbox" checked={saveAddr} onChange={(e) => setSaveAddr(e.target.checked)} /><span>{lang === "hi" ? "यह पता मेरे खाते में सेव करें" : "Save this address to my account"}</span></label>}
            </>}
            {settings.delivery_area_note && <small className="muted-s">{settings.delivery_area_note}</small>}
            {!customer && <p className="muted-s small">{lang === "hi" ? "पते सेव करने के लिए " : "To save addresses, "}<a href="#/account">{lang === "hi" ? "लॉगिन करें" : "log in"}</a>.</p>}
          </>}
          <label>{L("notes")}<input value={f.notes} onChange={(e) => setF({ ...f, notes: e.target.value })} /></label>
          <label>{L("coupon")}<input value={f.coupon} onChange={(e) => setF({ ...f, coupon: e.target.value.toUpperCase() })} />
            {quote?.couponError && f.coupon && <small className="bad-s">Coupon not valid</small>}</label>
          <div className="lbl">{L("payment")}</div>
          <div className="pay-opts">{pays.map((p) => (
            <button key={p} className={f.pay === p ? "on" : ""} onClick={() => setF({ ...f, pay: p })}>{p === "UPI_AT_RESTAURANT" ? "📱 UPI" : "💵 " + (lang === "hi" ? "नकद" : "Cash")}<small>{L(p)}</small></button>
          ))}</div>
          {quote && (
            <table className="bill-s"><tbody>
              <tr><td>{L("subtotal")}</td><td>{rupees(quote.subtotalPaise)}</td></tr>
              {quote.discountPaise > 0 && <tr><td>{L("discount")}</td><td>−{rupees(quote.discountPaise)}</td></tr>}
              {quote.taxPaise > 0 && <tr><td>{L("tax")}</td><td>{rupees(quote.taxPaise)}</td></tr>}
              {quote.packagingFeePaise > 0 && <tr><td>{L("packaging")}</td><td>{rupees(quote.packagingFeePaise)}</td></tr>}
              {quote.deliveryFeePaise > 0 && <tr><td>{L("deliveryFee")}</td><td>{rupees(quote.deliveryFeePaise)}</td></tr>}
              <tr className="tot"><td>{L("total")}</td><td>{rupees(quote.totalPaise)}</td></tr>
            </tbody></table>
          )}
          {quote && quote.subtotalPaise < quote.minOrderPaise && <p className="bad-s">{L("minOrder")}: {rupees(quote.minOrderPaise)}</p>}
          <label className="check-s"><input type="checkbox" checked={f.terms} onChange={(e) => setF({ ...f, terms: e.target.checked })} />
            <span>{L("terms")} — <a href="./terms.html" target="_blank">{L("orderTerms")}</a></span></label>
          {err && <p className="bad-s">{err}</p>}
          <button className="primary-s wide" disabled={busy || !quote || quote.subtotalPaise < quote.minOrderPaise} onClick={() => void place()}>
            {busy ? "…" : `${L("placeOrder")} · ${rupees(quote?.totalPaise ?? 0)}`}
          </button>
          <p className="muted-s small">{L("priceNote")}</p>
        </div>
      )}
    </Sheet>
  );
}

const STEPS = ["PLACED", "ACCEPTED", "PREPARING", "READY", "COMPLETED"] as const;

function OrderView({ L, lang, token, settings }: { L: L; lang: Lang; token: string; settings: Settings | null }) {
  const [o, setO] = useState<(Order & { canCancel: boolean; restaurantPhone: string }) | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const load = useCallback(async () => {
    const { data, error } = await supabase.rpc("get_order_by_token", { p_token: token });
    if (error) setErr(L("offline")); else { setErr(null); setO(data); }
  }, [token, L]);
  useEffect(() => {
    void load();
    const id = setInterval(() => { if (document.visibilityState === "visible") void load(); }, 10000);
    return () => clearInterval(id);
  }, [load]);
  const canPay = !!o && !!settings?.upi_id && o.paymentStatus === "PENDING" && !["REJECTED", "CANCELLED"].includes(o.orderStatus);
  const link = canPay ? upiLink(settings!.upi_id!, settings!.restaurant_name, o!.totalPaise, o!.orderNumber) : null;
  useEffect(() => { if (link) void QRCode.toDataURL(link, { width: 220, margin: 1 }).then(setQr); else setQr(null); }, [link]);

  if (!o) return <p className="empty-s">{err ?? "…"}</p>;
  const reached = Object.fromEntries(o.history.map((h) => [h.status, h.at]));
  const failed = o.orderStatus === "REJECTED" || o.orderStatus === "CANCELLED";
  const steps: string[] = o.orderType === "DELIVERY" ? ["PLACED", "ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY", "COMPLETED"] : [...STEPS];
  const stepIdx = steps.indexOf(o.orderStatus);
  const stepLabel = (s: string) => (s === "COMPLETED" && o.orderType === "DELIVERY" ? (lang === "hi" ? "डिलीवर हो गया" : "Delivered")
    : s === "READY" && o.orderType === "PICKUP" ? (lang === "hi" ? "पिकअप के लिए तैयार" : "Ready for pickup")
    : s === "READY" && o.orderType === "DELIVERY" ? (lang === "hi" ? "पैक हो गया" : "Packed") : L(s as Key));
  const waiting = o.orderStatus === "PLACED" && Date.now() - new Date(o.createdAt).getTime() > 120000;
  return (
    <div className="stack">
      <div className="card-s2 status-card">
        <div className="muted-s">{o.orderNumber}{o.tableNumber ? ` · ${L("table")} ${o.tableNumber}` : ""}</div>
        <div className={"status-big" + (failed ? " bad" : "")}>{stepLabel(o.orderStatus)}</div>
        {o.orderStatus === "PLACED" && <p>{L("awaiting")}</p>}
        {waiting && <div className="banner-s inline">{L("notConfirmed")} <a href={`tel:${o.restaurantPhone}`}>📞 {o.restaurantPhone}</a></div>}
        {(o.rejectionReason || o.cancelReason) && <p className="bad-s">{o.rejectionReason ?? o.cancelReason}</p>}
        {o.estimatedReadyAt && ["ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY"].includes(o.orderStatus) && <p>{o.orderType === "DELIVERY" ? (lang === "hi" ? "लगभग पहुँचने का समय" : "Estimated delivery by") : L("estimated")} <b>{time(o.estimatedReadyAt)}</b></p>}
        {!failed && (
          <ol className="progress-s">
            {steps.map((s, idx) => (
              <li key={s} className={idx <= stepIdx ? "done" : ""}><i />{stepLabel(s)}{reached[s] && <small>{time(reached[s])}</small>}</li>
            ))}
          </ol>
        )}
        {err && <div className="banner-s warn inline">{err}</div>}
      </div>

      {canPay && (
        <div className="card-s2 pay-card">
          <h3>{L("payNow")} · {rupees(o.totalPaise)}</h3>
          {qr && <img src={qr} alt="UPI QR" />}
          <a className="primary-s wide" href={link!}>{L("openUpi")}</a>
          <p className="muted-s small">{L("scanToPay")}</p>
        </div>
      )}
      {o.paymentStatus === "PAID" && <div className="card-s2 paid">{L("paid")}</div>}

      <div className="card-s2">
        <h3 style={{ marginTop: 0 }}>{lang === "hi" ? "बिल" : "Bill"} · {o.orderNumber}</h3>
        {o.items.map((i, k) => <div key={k} className="row-s between"><span>{i.quantity} × {i.nameSnapshot}</span><span>{rupees(i.lineTotalPaise)}</span></div>)}
        <div className="row-s between muted-s"><span>{L("subtotal")}</span><span>{rupees(o.subtotalPaise)}</span></div>
        {o.discountPaise > 0 && <div className="row-s between muted-s"><span>{L("discount")}{o.couponCode ? ` (${o.couponCode})` : ""}</span><span>−{rupees(o.discountPaise)}</span></div>}
        {o.taxPaise > 0 && <div className="row-s between muted-s"><span>{L("tax")}</span><span>{rupees(o.taxPaise)}</span></div>}
        {o.packagingFeePaise > 0 && <div className="row-s between muted-s"><span>{L("packaging")}</span><span>{rupees(o.packagingFeePaise)}</span></div>}
        {o.deliveryFeePaise > 0 && <div className="row-s between muted-s"><span>{L("deliveryFee")}</span><span>{rupees(o.deliveryFeePaise)}</span></div>}
        <div className="row-s between tot"><span>{L("total")}</span><b>{rupees(o.totalPaise)}</b></div>
        <div className="muted-s small">{L("payment")}: {L(o.paymentMethod as Key)} · {o.paymentStatus === "PAID" ? L("paid") : (lang === "hi" ? "बाकी" : "Due")}</div>
        {o.orderType === "DELIVERY" && o.deliveryAddress && <div className="small">🛵 {o.deliveryAddress}{o.deliveryLandmark ? ` · ${o.deliveryLandmark}` : ""}</div>}
      </div>
      {o.orderStatus === "COMPLETED" && <FeedbackCard lang={lang} token={token} />}
      {o.canCancel && <button className="ghost-s" onClick={async () => {
        if (!confirm(L("cancelOrder") + "?")) return;
        await supabase.rpc("cancel_order_by_customer", { p_token: token, p_reason: "Cancelled by customer" }); void load();
      }}>{L("cancelOrder")}</button>}
    </div>
  );
}

type OrderRow = Order & { accessToken: string };
function OrdersView({ L, lang, customer, onReorder }: { L: L; lang: Lang; customer: Customer | null | undefined; onReorder: (o: Order) => void }) {
  const [list, setList] = useState<OrderRow[] | null>(null);
  useEffect(() => {
    if (customer === undefined) return;
    const tokens = store.get<string[]>("ss_tokens", []);
    void (async () => {
      const [mine, device] = await Promise.all([
        customer ? supabase.rpc("my_orders", { p_limit: 100 }) : Promise.resolve({ data: [] }),
        tokens.length ? supabase.rpc("get_orders_by_tokens", { p_tokens: tokens }) : Promise.resolve({ data: [] }),
      ]);
      const all = [...((mine.data as OrderRow[]) ?? []), ...((device.data as OrderRow[]) ?? [])];
      const seen = new Set<string>();
      setList(all.filter((o) => (seen.has(o.id) ? false : (seen.add(o.id), true))).sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
    })();
  }, [customer]);
  if (!list) return <p className="empty-s">…</p>;
  const loginHint = !customer && <p className="muted-s small">{lang === "hi" ? "किसी भी फ़ोन पर अपने सारे ऑर्डर देखने के लिए " : "To see your orders on any phone, "}<a href="#/account">{lang === "hi" ? "लॉगिन करें" : "log in"}</a>.</p>;
  if (!list.length) return <div className="stack"><p className="empty-s">{L("noOrders")}</p>{loginHint}</div>;
  return (
    <div className="stack">
      <h2 className="sec">{L("myOrders")}</h2>
      {loginHint}
      {list.map((o) => (
        <div key={o.id} className="card-s2 tap" onClick={() => go("order/" + o.accessToken)}>
          <div className="row-s between"><b>{o.orderNumber}</b><span className="tag-s">{L(o.orderStatus as Key)}</span></div>
          <div className="muted-s small">{new Date(o.createdAt).toLocaleString("en-IN")} · {rupees(o.totalPaise)}</div>
          <div className="small">{o.items.map((i) => `${i.quantity}× ${i.nameSnapshot}`).join(", ")}</div>
          {!["PLACED", "ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY"].includes(o.orderStatus) &&
            <button className="ghost-s" onClick={(e) => { e.stopPropagation(); onReorder(o); }}>{L("reorder")}</button>}
        </div>
      ))}
    </div>
  );
}

function InfoView({ L, lang, s }: { L: L; lang: Lang; s: Settings }) {
  const wa = (s.whatsapp_phone ?? "").replace(/\D/g, "");
  const days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  return (
    <div className="stack">
      <div className="card-s2">
        <h2 className="sec" style={{ marginTop: 0 }}>{s.restaurant_name}</h2>
        <p>{s.address}</p>
        <div className="hero-row"><a className="pill-s" href={`tel:${s.phone}`}>📞 {s.phone}</a>
          {wa.length >= 10 && <a className="pill-s" href={`https://wa.me/${wa.length === 10 ? "91" + wa : wa}?text=${encodeURIComponent(lang === "hi" ? "नमस्ते, मुझे ऑर्डर करना है" : "Hello, I would like to place an order")}`} target="_blank" rel="noreferrer">💬 WhatsApp</a>}
          <a className="pill-s" href={mapsUrl(s)} target="_blank" rel="noreferrer">📍 {L("directions")}</a></div>
      </div>
      <div className="card-s2">
        <h3>{lang === "hi" ? "हमारे बारे में" : "About us"}</h3>
        <p>{lang === "hi"
          ? `${s.restaurant_name} — ${s.tagline}. श्रीनगर गढ़वाल में थाली, पराठा, मोमो, चाइनीज़, स्नैक्स और चाय का रेस्टोरेंट। यहाँ बैठकर खाएँ, पैक करवाएँ${s.delivery_enabled ? " या घर मँगवाएँ" : ""}।`
          : `${s.restaurant_name} — ${s.tagline}. A restaurant in Srinagar Garhwal serving thalis, parathas, momos, Chinese, snacks and tea. Eat in, take away${s.delivery_enabled ? " or get it delivered" : ""}.`}</p>
        <p className="muted-s small">{s.address}</p>
      </div>
      <ReviewsCard lang={lang} />
      <div className="card-s2">
        <h3>{L("hours")}</h3>
        {days.map((d, i) => <div key={d} className="row-s between"><span>{d}</span><span>{(s.opening_hours[String(i + 1)] ?? []).map((x) => `${x.open}–${x.close}`).join(", ") || "Closed"}</span></div>)}
      </div>
      <div className="card-s2">
        {s.reviews_url && <p><a href={s.reviews_url} target="_blank" rel="noreferrer">★ {s.rating} — Google reviews</a></p>}
        {s.fssai_number && <p className="muted-s">FSSAI Lic. No. {s.fssai_number}</p>}
        <p><a href={APK_URL}>⬇ {L("getApp")} (APK)</a></p>
        <p className="small"><a href="./photo-credits.html" target="_blank">📷 Photo credits</a></p>
        <p className="muted-s small"><a href="./privacy.html">{L("privacy")}</a> · <a href="./terms.html">{L("orderTerms")}</a> · <a href="./admin.html">{L("staffLogin")}</a></p>
      </div>
    </div>
  );
}

/** Table guests: call a waiter, ask for the bill or water. */
function TableBar({ table, L, lang }: { table: string; L: L; lang: Lang }) {
  const [msg, setMsg] = useState<string | null>(null);
  async function ask(type: string) {
    let code = store.get("ss_table_code", "");
    let r = (await supabase.rpc("request_service", { p_table: table, p_type: type, p_code: code || null })).data as { ok: boolean; error?: string; message?: string } | null;
    if (r && !r.ok && r.error === "TABLE_CODE_REQUIRED") {
      code = prompt(L("tableCode")) ?? "";
      store.set("ss_table_code", code);
      r = (await supabase.rpc("request_service", { p_table: table, p_type: type, p_code: code })).data;
    }
    setMsg(r?.ok ? (lang === "hi" ? "स्टाफ़ को सूचना भेज दी गई ✓" : "Staff has been notified ✓") : (r?.message ?? "Please call the staff"));
    setTimeout(() => setMsg(null), 5000);
  }
  return (
    <div className="table-bar">
      <span>🍽 {L("table")} <b>{table}</b></span>
      <div className="row-s">
        <button className="pill-s" onClick={() => void ask("CALL_WAITER")}>🙋 {L("callWaiter")}</button>
        <button className="pill-s" onClick={() => void ask("REQUEST_BILL")}>🧾 {L("requestBill")}</button>
        <button className="pill-s" onClick={() => void ask("WATER")}>💧 {L("water")}</button>
      </div>
      {msg && <div className="small toast-s">{msg}</div>}
    </div>
  );
}
