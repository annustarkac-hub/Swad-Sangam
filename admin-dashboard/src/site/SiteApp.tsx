import { useCallback, useEffect, useMemo, useState } from "react";
import { rupees, time } from "../lib/format";
import { supabase } from "../lib/supabase";
import type { Category, MenuItem, Order } from "../lib/types";
import { t, type Key, type Lang } from "./i18n";

const APK_URL = "https://github.com/annustarkac-hub/Swad-Sangam/releases/latest/download/swad-sangam.apk";

interface Settings {
  restaurant_name: string; phone: string; address: string; maps_query: string; latitude: number | null; longitude: number | null;
  opening_hours: Record<string, { open: string; close: string }[]>; announcement: string | null; accepting_orders: boolean;
  pickup_enabled: boolean; dine_in_enabled: boolean; delivery_enabled: boolean; cash_enabled: boolean; upi_at_restaurant_enabled: boolean;
  upi_id: string | null; rating: number | null; review_count: number | null; reviews_url: string | null; temporarily_closed: boolean;
  closure_message: string | null; min_order_paise: number; max_item_quantity: number; fssai_number: string | null; delivery_area_note: string | null;
}
interface Quote { subtotalPaise: number; discountPaise: number; taxPaise: number; packagingFeePaise: number; deliveryFeePaise: number; totalPaise: number; couponError: string | null; minOrderPaise: number; pricesIncludeTax: boolean }
interface Line { itemId: string; qty: number; optionIds: string[] }
type View = { name: "menu" } | { name: "cart" } | { name: "orders" } | { name: "info" } | { name: "order"; token: string };

const store = {
  get<T>(k: string, d: T): T { try { const v = localStorage.getItem(k); return v ? (JSON.parse(v) as T) : d; } catch { return d; } },
  set(k: string, v: unknown) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* private mode */ } },
};

function parseHash(): View {
  const h = location.hash.replace(/^#\/?/, "");
  if (h.startsWith("order/")) return { name: "order", token: h.slice(6) };
  if (h === "cart" || h === "orders" || h === "info") return { name: h };
  return { name: "menu" };
}
const go = (path: string) => { location.hash = "#/" + path; };

function mapsUrl(s: Settings) {
  const d = s.latitude != null && s.longitude != null ? `${s.latitude},${s.longitude}` : s.maps_query;
  return "https://www.google.com/maps/dir/?api=1&destination=" + encodeURIComponent(d);
}

function isOpen(s: Settings): boolean {
  const now = new Date(new Date().toLocaleString("en-US", { timeZone: "Asia/Kolkata" }));
  const dow = ((now.getDay() + 6) % 7) + 1;
  const mins = now.getHours() * 60 + now.getMinutes();
  const toM = (x: string) => { const [h, m] = x.split(":").map(Number); return h * 60 + m; };
  const today = s.opening_hours?.[String(dow)] ?? [];
  const prev = s.opening_hours?.[String(dow === 1 ? 7 : dow - 1)] ?? [];
  return !s.temporarily_closed && (today.some((sl) => { const o = toM(sl.open), c = toM(sl.close); return c > o ? mins >= o && mins < c : mins >= o; })
    || prev.some((sl) => toM(sl.close) <= toM(sl.open) && mins < toM(sl.close)));
}

export default function SiteApp() {
  const [lang, setLang] = useState<Lang>(store.get("ss_lang", navigator.language.startsWith("hi") ? "hi" : "en"));
  const L = (k: Key) => t(lang, k);
  const [view, setView] = useState<View>(parseHash());
  const [settings, setSettings] = useState<Settings | null>(store.get("ss_settings", null));
  const [cats, setCats] = useState<Category[]>(store.get("ss_cats", []));
  const [items, setItems] = useState<MenuItem[]>(store.get("ss_items", []));
  const [offline, setOffline] = useState(false);
  const [cart, setCart] = useState<Line[]>(store.get("ss_cart", []));
  const table = useMemo(() => new URLSearchParams(location.search).get("table") ?? store.get<string | null>("ss_table", null), []);
  const [installEvt, setInstallEvt] = useState<Event & { prompt?: () => void } | null>(null);

  useEffect(() => { const f = () => setView(parseHash()); window.addEventListener("hashchange", f); return () => window.removeEventListener("hashchange", f); }, []);
  useEffect(() => store.set("ss_cart", cart), [cart]);
  useEffect(() => store.set("ss_lang", lang), [lang]);
  useEffect(() => { if (table) store.set("ss_table", table); }, [table]);
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
  const max = settings?.max_item_quantity ?? 20;
  const add = (itemId: string, optionIds: string[] = []) => setCart((c) => {
    const k = c.find((l) => l.itemId === itemId && l.optionIds.join() === optionIds.join());
    return k ? c.map((l) => (l === k ? { ...l, qty: Math.min(max, l.qty + 1) } : l)) : [...c, { itemId, qty: 1, optionIds }];
  });
  const setQty = (line: Line, q: number) => setCart((c) => (q <= 0 ? c.filter((l) => l !== line) : c.map((l) => (l === line ? { ...l, qty: Math.min(max, q) } : l))));
  const ordering = !!settings && settings.accepting_orders && (settings.pickup_enabled || settings.dine_in_enabled || settings.delivery_enabled) && isOpen(settings);

  return (
    <div className="site">
      <header className="hero">
        <div className="hero-top">
          <div className="logo">SS</div>
          <div style={{ flex: 1 }}>
            <h1 onClick={() => go("")}>{settings?.restaurant_name ?? "Swad Sangam"}</h1>
            <div className="tag">{L("tagline")}</div>
          </div>
          <button className="chip light" onClick={() => setLang(lang === "en" ? "hi" : "en")}>{lang === "en" ? "हिन्दी" : "English"}</button>
        </div>
        {settings && (
          <div className="hero-meta">
            <span className={"dot " + (isOpen(settings) ? "on" : "off")} /> {isOpen(settings) ? L("openNow") : L("closedNow")}
            {settings.rating != null && <> · ★ {settings.rating} {L("rating")}{settings.review_count ? ` (${settings.review_count} ${L("reviews")})` : ""}</>}
          </div>
        )}
        <div className="hero-actions">
          {settings && <a className="btn light" href={`tel:${settings.phone}`}>📞 {L("call")}</a>}
          {settings && <a className="btn light" href={mapsUrl(settings)} target="_blank" rel="noreferrer">📍 {L("directions")}</a>}
          {installEvt ? <button className="btn gold" onClick={() => installEvt.prompt?.()}>⬇ {L("install")}</button>
            : <a className="btn gold" href={APK_URL}>⬇ {L("getApp")}</a>}
        </div>
      </header>

      {offline && <div className="note warn">{L("offline")}</div>}
      {settings?.temporarily_closed && <div className="note bad">{settings.closure_message ?? L("closedNow")}</div>}
      {settings?.announcement && <div className="note">{settings.announcement}</div>}
      {settings && !ordering && view.name !== "order" && <div className="note">{L("orderingOff")} <a href={`tel:${settings.phone}`}>{settings.phone}</a></div>}
      {table && <div className="note">🍽 {L("table")}: <b>{table}</b></div>}

      <main className="site-main">
        {view.name === "menu" && <MenuView L={L} lang={lang} cats={cats} items={items} cart={cart} onAdd={add} canOrder={ordering} />}
        {view.name === "cart" && settings && <CartView L={L} cart={cart} byId={byId} settings={settings} table={table} canOrder={ordering}
          setQty={setQty} onPlaced={(token) => { setCart([]); go("order/" + token); }} />}
        {view.name === "order" && <OrderView L={L} token={view.token} phone={settings?.phone ?? ""} />}
        {view.name === "orders" && <OrdersView L={L} onReorder={(o) => { o.items.forEach((i) => byId[i.itemId]?.is_available && add(i.itemId)); go("cart"); }} />}
        {view.name === "info" && settings && <InfoView L={L} s={settings} />}
      </main>

      {count > 0 && view.name === "menu" && (
        <button className="cartbar" onClick={() => go("cart")}>
          <span>{count} {L("items")}</span><b>{L("viewCart")} →</b>
        </button>
      )}
      <nav className="tabbar">
        {([["", "menu", "🍛"], ["cart", "cart", "🛒"], ["orders", "myOrders", "🧾"], ["info", "info", "ℹ️"]] as [string, Key, string][]).map(([p, k, ic]) => (
          <button key={p} className={(view.name === (p || "menu")) ? "active" : ""} onClick={() => go(p)}>
            <span>{ic}{p === "cart" && count > 0 ? <sup>{count}</sup> : null}</span>{L(k)}
          </button>
        ))}
      </nav>
    </div>
  );
}

function Veg({ type }: { type: "VEG" | "NON_VEG" }) {
  return <span className={type === "VEG" ? "veg" : "nonveg"} aria-label={type === "VEG" ? "Vegetarian" : "Non-vegetarian"} />;
}

function MenuView({ L, lang, cats, items, cart, onAdd, canOrder }: { L: (k: Key) => string; lang: Lang; cats: Category[]; items: MenuItem[]; cart: Line[]; onAdd: (id: string, opt?: string[]) => void; canOrder: boolean }) {
  const [q, setQ] = useState("");
  const [cat, setCat] = useState("");
  const [veg, setVeg] = useState(false);
  const shown = items.filter((i) => (!cat || i.category_id === cat) && (!veg || i.food_type === "VEG") &&
    (!q || (i.name + " " + (i.name_hi ?? "") + " " + (i.description ?? "")).toLowerCase().includes(q.toLowerCase())));
  const qty = (id: string) => cart.filter((l) => l.itemId === id).reduce((s, l) => s + l.qty, 0);
  const name = (i: MenuItem) => (lang === "hi" && i.name_hi ? i.name_hi : i.name);
  return (
    <>
      <input className="search" placeholder={L("search")} value={q} onChange={(e) => setQ(e.target.value)} />
      <div className="chips">
        <button className={"chip" + (!cat ? " on" : "")} onClick={() => setCat("")}>{L("all")}</button>
        {cats.map((c) => <button key={c.id} className={"chip" + (cat === c.id ? " on" : "")} onClick={() => setCat(c.id)}>{lang === "hi" && c.name_hi ? c.name_hi : c.name}</button>)}
        <button className={"chip" + (veg ? " on" : "")} onClick={() => setVeg(!veg)}><Veg type="VEG" /> {L("vegOnly")}</button>
      </div>
      {cats.filter((c) => shown.some((i) => i.category_id === c.id)).map((c) => (
        <section key={c.id}>
          <h2 className="cat">{lang === "hi" && c.name_hi ? c.name_hi : c.name}</h2>
          {shown.filter((i) => i.category_id === c.id).map((i) => (
            <article key={i.id} className={"dish" + (i.is_available ? "" : " off")}>
              <div className="dish-body">
                <div><Veg type={i.food_type} /> {i.is_popular && <span className="pill">★ Popular</span>}</div>
                <h3>{name(i)}</h3>
                <div className="price">{rupees(i.price_paise)}</div>
                {(lang === "hi" ? i.description_hi ?? i.description : i.description) && <p>{lang === "hi" ? i.description_hi ?? i.description : i.description}</p>}
                {i.portion_info && <p className="muted">{i.portion_info}</p>}
                {!i.is_available && <p className="bad-text">{L("unavailable")}</p>}
              </div>
              <div className="dish-side">
                {i.image_url ? <img src={i.image_url} alt={i.name} loading="lazy" /> : <div className="ph">🍛</div>}
                {i.is_available && canOrder && (
                  i.options.length > 0
                    ? <select className="addbtn" value="" onChange={(e) => e.target.value && onAdd(i.id, [e.target.value])}>
                        <option value="">{L("add")} {qty(i.id) ? `(${qty(i.id)})` : ""}</option>
                        {i.options.filter((o) => o.isAvailable !== false).map((o) => <option key={o.id} value={o.id}>{o.name}{o.pricePaise ? ` +${rupees(o.pricePaise)}` : ""}</option>)}
                      </select>
                    : <button className="addbtn" onClick={() => onAdd(i.id)}>{L("add")} {qty(i.id) ? `(${qty(i.id)})` : "+"}</button>
                )}
              </div>
            </article>
          ))}
        </section>
      ))}
    </>
  );
}

function CartView({ L, cart, byId, settings, table, canOrder, setQty, onPlaced }: {
  L: (k: Key) => string; cart: Line[]; byId: Record<string, MenuItem>; settings: Settings; table: string | null; canOrder: boolean;
  setQty: (l: Line, q: number) => void; onPlaced: (token: string) => void;
}) {
  const types = (["PICKUP", "DINE_IN", "DELIVERY"] as const).filter((x) => (x === "PICKUP" && settings.pickup_enabled) || (x === "DINE_IN" && settings.dine_in_enabled) || (x === "DELIVERY" && settings.delivery_enabled));
  const pays = (["CASH", "UPI_AT_RESTAURANT"] as const).filter((p) => (p === "CASH" && settings.cash_enabled) || (p === "UPI_AT_RESTAURANT" && settings.upi_at_restaurant_enabled));
  const saved = store.get("ss_customer", { name: "", phone: "" });
  const [f, setF] = useState({ name: saved.name, phone: saved.phone, type: (table && types.includes("DINE_IN") ? "DINE_IN" : types[0]) as string,
    table: table ?? "", address: "", notes: "", pay: pays[0] as string, coupon: "", terms: false });
  const [quote, setQuote] = useState<Quote | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const lines = cart.filter((l) => byId[l.itemId]);
  const items = lines.map((l) => ({ itemId: l.itemId, quantity: l.qty, optionIds: l.optionIds }));

  useEffect(() => {
    if (!lines.length) { setQuote(null); return; }
    const id = setTimeout(() => {
      supabase.rpc("quote_order", { p_order: { orderType: f.type, items, couponCode: f.coupon || null } }).then(({ data }) => data && setQuote(data as Quote));
    }, 300);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(items), f.type, f.coupon]);

  async function place() {
    setErr(null);
    if (!/^[6-9]\d{9}$/.test(f.phone.replace(/\D/g, "").slice(-10)) || f.name.trim().length < 2 || !f.terms) { setErr("Please fill your name, a valid mobile number and accept the terms."); return; }
    setBusy(true);
    const key = store.get<string | null>("ss_pending_key", null) ?? crypto.randomUUID();
    store.set("ss_pending_key", key); // reused on retry → no duplicate orders
    try {
      const { data, error } = await supabase.rpc("place_order", { p_order: {
        idempotencyKey: key, customerName: f.name, customerPhone: f.phone, orderType: f.type, paymentMethod: f.pay, items,
        termsAccepted: f.terms, tableNumber: f.type === "DINE_IN" ? f.table : null, deliveryAddress: f.type === "DELIVERY" ? f.address : null,
        notes: f.notes || null, couponCode: f.coupon || null, expectedTotalPaise: quote?.totalPaise } });
      if (error) throw new Error(error.message);
      const r = data as { ok: boolean; error?: string; message?: string; order?: { accessToken: string } };
      if (!r.ok) {
        localStorage.removeItem("ss_pending_key");
        setErr(r.message ?? "Order could not be placed");
        if (r.error === "PRICE_CHANGED") setQuote(null);
        return;
      }
      localStorage.removeItem("ss_pending_key");
      store.set("ss_customer", { name: f.name, phone: f.phone });
      store.set("ss_tokens", [r.order!.accessToken, ...store.get<string[]>("ss_tokens", [])].slice(0, 50));
      onPlaced(r.order!.accessToken);
    } catch {
      setErr("No internet connection. Your order was NOT confirmed — tap Place order again (it will not be duplicated).");
    } finally { setBusy(false); }
  }

  if (!lines.length) return <p className="empty">{L("emptyCart")}</p>;
  return (
    <div className="card-s">
      <h2>{L("cart")}</h2>
      {lines.map((l, k) => {
        const i = byId[l.itemId];
        const opts = i.options.filter((o) => l.optionIds.includes(o.id));
        const unit = i.price_paise + opts.reduce((s, o) => s + o.pricePaise, 0);
        return (
          <div key={k} className="cline">
            <div style={{ flex: 1 }}><Veg type={i.food_type} /> {i.name}{opts.length ? <small> ({opts.map((o) => o.name).join(", ")})</small> : null}
              {!i.is_available && <div className="bad-text">{L("unavailable")}</div>}<div className="muted">{rupees(unit)} × {l.qty}</div></div>
            <div className="stepper"><button onClick={() => setQty(l, l.qty - 1)}>−</button><b>{l.qty}</b><button onClick={() => setQty(l, l.qty + 1)}>+</button></div>
          </div>
        );
      })}
      {quote && (
        <table className="sum"><tbody>
          <tr><td>{L("subtotal")}</td><td>{rupees(quote.subtotalPaise)}</td></tr>
          {quote.discountPaise > 0 && <tr><td>{L("discount")}</td><td>−{rupees(quote.discountPaise)}</td></tr>}
          {quote.taxPaise > 0 && <tr><td>{L("tax")}</td><td>{rupees(quote.taxPaise)}</td></tr>}
          {quote.packagingFeePaise > 0 && <tr><td>{L("packaging")}</td><td>{rupees(quote.packagingFeePaise)}</td></tr>}
          {quote.deliveryFeePaise > 0 && <tr><td>{L("deliveryFee")}</td><td>{rupees(quote.deliveryFeePaise)}</td></tr>}
          <tr className="tot"><td>{L("total")}</td><td>{rupees(quote.totalPaise)}</td></tr>
        </tbody></table>
      )}
      {quote && quote.subtotalPaise < quote.minOrderPaise && <p className="bad-text">{L("minOrder")}: {rupees(quote.minOrderPaise)}</p>}
      {!canOrder ? <div className="note">{L("orderingOff")} <a href={`tel:${settings.phone}`}>{settings.phone}</a></div> : (
        <div className="form">
          <label>{L("name")}<input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} autoComplete="name" /></label>
          <label>{L("phone")}<input value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} inputMode="tel" autoComplete="tel" /></label>
          <label>{L("orderType")}</label>
          <div className="chips">{types.map((x) => <button key={x} className={"chip" + (f.type === x ? " on" : "")} onClick={() => setF({ ...f, type: x })}>{L(x)}</button>)}</div>
          {f.type === "DINE_IN" && <label>{L("table")}<input value={f.table} onChange={(e) => setF({ ...f, table: e.target.value })} /></label>}
          {f.type === "DELIVERY" && <label>{L("address")}<textarea value={f.address} onChange={(e) => setF({ ...f, address: e.target.value })} />{settings.delivery_area_note && <small>{settings.delivery_area_note}</small>}</label>}
          <label>{L("notes")}<input value={f.notes} onChange={(e) => setF({ ...f, notes: e.target.value })} /></label>
          <label>{L("coupon")}<input value={f.coupon} onChange={(e) => setF({ ...f, coupon: e.target.value.toUpperCase() })} />
            {quote?.couponError && f.coupon && <small className="bad-text">Coupon not valid</small>}</label>
          <label>{L("payment")}</label>
          {pays.map((p) => <label key={p} className="radio"><input type="radio" checked={f.pay === p} onChange={() => setF({ ...f, pay: p })} /> {L(p)}{p === "UPI_AT_RESTAURANT" && settings.upi_id ? ` (${settings.upi_id})` : ""}</label>)}
          <label className="radio"><input type="checkbox" checked={f.terms} onChange={(e) => setF({ ...f, terms: e.target.checked })} /> <span>{L("terms")} — <a href="./terms.html" target="_blank">{L("orderTerms")}</a></span></label>
          {err && <p className="bad-text">{err}</p>}
          <button className="primary" disabled={busy || !quote || quote.subtotalPaise < quote.minOrderPaise} onClick={() => void place()}>
            {busy ? "…" : `${L("placeOrder")} · ${rupees(quote?.totalPaise ?? 0)}`}
          </button>
          <p className="muted small">{L("priceNote")}</p>
        </div>
      )}
    </div>
  );
}

const STEPS = ["PLACED", "ACCEPTED", "PREPARING", "READY", "COMPLETED"] as const;

function OrderView({ L, token, phone }: { L: (k: Key) => string; token: string; phone: string }) {
  const [o, setO] = useState<(Order & { canCancel: boolean; restaurantPhone: string }) | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const load = useCallback(async () => {
    const { data, error } = await supabase.rpc("get_order_by_token", { p_token: token });
    if (error) setErr("Offline — last known status shown."); else { setErr(null); setO(data); }
  }, [token]);
  useEffect(() => {
    void load();
    const id = setInterval(() => { if (document.visibilityState === "visible") void load(); }, 15000);
    return () => clearInterval(id);
  }, [load]);
  if (!o) return <p className="empty">{err ?? "…"}</p>;
  const reached = Object.fromEntries(o.history.map((h) => [h.status, h.at]));
  const waiting = o.orderStatus === "PLACED" && Date.now() - new Date(o.createdAt).getTime() > 120000;
  const failed = o.orderStatus === "REJECTED" || o.orderStatus === "CANCELLED";
  return (
    <div className="card-s">
      <h2>{o.orderNumber}</h2>
      {err && <div className="note warn">{err}</div>}
      <div className={"status " + (failed ? "bad" : "")}>{L(o.orderStatus as Key)}</div>
      {o.orderStatus === "PLACED" && <p>{L("awaiting")}</p>}
      {waiting && <div className="note">{L("notConfirmed")} <a href={`tel:${o.restaurantPhone || phone}`}>📞 {o.restaurantPhone || phone}</a></div>}
      {o.rejectionReason && <p className="bad-text">{o.rejectionReason}</p>}
      {o.cancelReason && <p className="bad-text">{o.cancelReason}</p>}
      {o.estimatedReadyAt && ["ACCEPTED", "PREPARING"].includes(o.orderStatus) && <p>{L("estimated")} {time(o.estimatedReadyAt)}</p>}
      {!failed && <ol className="timeline">{STEPS.map((s) => <li key={s} className={reached[s] ? "done" : ""}>{L(s)} {reached[s] && <small>{time(reached[s])}</small>}</li>)}</ol>}
      <table className="sum"><tbody>
        {o.items.map((i, k) => <tr key={k}><td>{i.quantity} × {i.nameSnapshot}</td><td>{rupees(i.lineTotalPaise)}</td></tr>)}
        <tr className="tot"><td>{L("total")}</td><td>{rupees(o.totalPaise)}</td></tr>
      </tbody></table>
      {o.canCancel && <button className="ghost" onClick={async () => {
        if (!confirm(L("cancelOrder") + "?")) return;
        await supabase.rpc("cancel_order_by_customer", { p_token: token, p_reason: "Cancelled by customer" }); void load();
      }}>{L("cancelOrder")}</button>}
    </div>
  );
}

function OrdersView({ L, onReorder }: { L: (k: Key) => string; onReorder: (o: Order) => void }) {
  const [list, setList] = useState<(Order & { accessToken: string })[] | null>(null);
  useEffect(() => {
    const tokens = store.get<string[]>("ss_tokens", []);
    if (!tokens.length) { setList([]); return; }
    supabase.rpc("get_orders_by_tokens", { p_tokens: tokens }).then(({ data }) => setList((data as (Order & { accessToken: string })[]) ?? []));
  }, []);
  if (!list) return <p className="empty">…</p>;
  if (!list.length) return <p className="empty">{L("noOrders")}</p>;
  return (
    <>
      {list.map((o) => (
        <div key={o.id} className="card-s" onClick={() => go("order/" + o.accessToken)} style={{ cursor: "pointer" }}>
          <div className="row-s"><b>{o.orderNumber}</b><span className="pill">{L(o.orderStatus as Key)}</span></div>
          <div className="muted">{new Date(o.createdAt).toLocaleString("en-IN")} · {rupees(o.totalPaise)}</div>
          <div>{o.items.map((i) => `${i.quantity}× ${i.nameSnapshot}`).join(", ")}</div>
          {!["PLACED", "ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY"].includes(o.orderStatus) &&
            <button className="ghost" onClick={(e) => { e.stopPropagation(); onReorder(o); }}>{L("reorder")}</button>}
        </div>
      ))}
    </>
  );
}

function InfoView({ L, s }: { L: (k: Key) => string; s: Settings }) {
  const days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  return (
    <div className="card-s">
      <h2>{s.restaurant_name}</h2>
      <p>{s.address}</p>
      <p><a href={`tel:${s.phone}`}>📞 {s.phone}</a> · <a href={mapsUrl(s)} target="_blank" rel="noreferrer">📍 {L("directions")}</a></p>
      <h3>{L("hours")}</h3>
      <table className="sum"><tbody>{days.map((d, i) => <tr key={d}><td>{d}</td><td>{(s.opening_hours[String(i + 1)] ?? []).map((x) => `${x.open}–${x.close}`).join(", ") || "Closed"}</td></tr>)}</tbody></table>
      {s.reviews_url && <p><a href={s.reviews_url} target="_blank" rel="noreferrer">★ {s.rating} — Google reviews</a></p>}
      {s.fssai_number && <p className="muted">FSSAI Lic. No. {s.fssai_number}</p>}
      <p><a href={APK_URL}>⬇ {L("getApp")} (APK)</a></p>
      <p className="muted small"><a href="./privacy.html">{L("privacy")}</a> · <a href="./terms.html">{L("orderTerms")}</a> · <a href="./admin.html">{L("staffLogin")}</a></p>
    </div>
  );
}
