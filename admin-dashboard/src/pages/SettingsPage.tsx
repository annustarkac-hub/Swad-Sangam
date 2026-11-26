import { MapPin, X } from "lucide-react";
import { lazy, Suspense, useEffect, useState } from "react";
import { useAuth } from "../auth";
import Modal from "../components/Modal";
import { fromPaise, toPaise } from "../lib/format";
import { friendlyError, supabase } from "../lib/supabase";

const MapPicker = lazy(() => import("../site/MapPicker"));

type Hours = Record<string, { open: string; close: string }[]>;
type Settings = Record<string, unknown> & { opening_hours: Hours };

const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

const TEXT: [string, string][] = [
  ["restaurant_name", "Restaurant name"], ["tagline", "Tagline"], ["phone", "Phone (with +91)"], ["whatsapp_phone", "WhatsApp number (optional)"],
  ["support_email", "Customer support email"], ["address", "Address"], ["maps_query", "Google Maps search text"],
  ["latitude", "Latitude (optional, for exact directions)"], ["longitude", "Longitude"],
  ["announcement", "Announcement shown in app (optional)"], ["closure_message", "Message when temporarily closed"],
  ["upi_id", "UPI ID shown for pay-at-restaurant"], ["fssai_number", "FSSAI licence/registration no. (14 digits)"],
  ["gstin", "GSTIN (if registered)"], ["legal_business_name", "Legal business name"], ["parking_info", "Parking information"],
  ["dine_in_info", "Dine-in information"], ["delivery_area_note", "Delivery area description"], ["delivery_radius_km", "Max delivery distance (km, blank = no limit)"],
  ["delivery_base_km", "Km included in the delivery fee"], ["delivery_road_factor", "Road distance factor (1 = straight line; hilly roads ≈ 1.4)"],
  ["tax_label", "Tax label (e.g. GST)"], ["sac_code", "SAC code on invoices"],
  ["place_of_supply", "Place of supply (state)"], ["invoice_footer", "Bill footer text"], ["rating", "Public rating (e.g. 4.5)"], ["review_count", "Public review count"],
  ["reviews_url", "Link to public reviews (Google)"], ["hero_image_url", "Website banner photo URL (real restaurant photo)"], ["privacy_policy_url", "Privacy policy URL"], ["terms_url", "Order terms URL"],
];
const MONEY: [string, string][] = [
  ["min_order_paise", "Minimum order (₹)"], ["delivery_min_order_paise", "Minimum order for delivery (₹)"],
  ["delivery_fee_paise", "Delivery fee (₹)"], ["delivery_per_km_paise", "Extra delivery fee per km after the included km (₹)"], ["packaging_fee_paise", "Packaging fee for pickup/delivery (₹)"],
  ["budget_threshold_paise", "“Budget meals” price limit (₹)"], ["cost_for_two_paise", "Approx. cost for two (₹, shown on website; leave 0 to hide)"],
];
const NUM: [string, string][] = [
  ["tax_rate_bps", "Tax rate in basis points (500 = 5%)"], ["default_prep_minutes", "Default preparation time (min)"],
  ["max_open_orders_per_phone", "Max open orders per phone"], ["max_item_quantity", "Max quantity per item"],
  ["pii_retention_days", "Delete customer details after (days)"],
  ["loyalty_points_per_100", "Loyalty points earned per ₹100"], ["loyalty_point_value_paise", "Value of 1 point in paise (100 = ₹1)"],
];
const SWITCHES: [string, string][] = [
  ["accepting_orders", "Accepting app orders (master switch)"], ["pickup_enabled", "Pickup orders"],
  ["dine_in_enabled", "Dine-in / table orders"], ["delivery_enabled", "Delivery orders (only if you deliver)"], ["delivery_require_pin", "Customer must mark the delivery location on the map"],
  ["cash_enabled", "Cash on pickup/delivery"], ["upi_at_restaurant_enabled", "UPI at restaurant"],
  ["temporarily_closed", "Temporarily closed"], ["allow_cancel_after_accept", "Customers may cancel after acceptance"],
  ["prices_include_tax", "Menu prices include tax"], ["hours_verified", "Opening hours confirmed by owner"],
  ["loyalty_enabled", "Loyalty points for regular customers"], ["require_table_code", "QR table orders need the table code"],
  ["gst_registered", "GST registered (print tax invoices)"], ["gst_input_credit", "Claim input tax credit on purchases"],
];

export default function SettingsPage() {
  const { reauthenticate } = useAuth();
  const [s, setS] = useState<Settings | null>(null);
  const [orig, setOrig] = useState<Settings | null>(null);
  const [msg, setMsg] = useState<{ ok: boolean; t: string } | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [pickShop, setPickShop] = useState(false);
  const [pw, setPw] = useState("");
  /** Money fields as typed (string) — never silently turned into ₹0 */
  const [money, setMoney] = useState<Record<string, string>>({});
  const badMoney = MONEY.filter(([k]) => money[k] !== undefined && toPaise(money[k] || "0") == null).map(([, l]) => l);

  async function load() {
    const { data, error } = await supabase.from("restaurant_settings").select("*").single();
    if (error) setMsg({ ok: false, t: friendlyError(error.message) });
    else { setS(data as Settings); setOrig(data as Settings); setMoney({}); }
  }
  useEffect(() => { void load(); }, []);
  if (!s) return <p>Loading…</p>;

  const set = (k: string, v: unknown) => setS({ ...s, [k]: v });
  const setHours = (day: number, idx: number, field: "open" | "close", v: string) => {
    const h: Hours = JSON.parse(JSON.stringify(s.opening_hours));
    h[String(day)] = h[String(day)] ?? [];
    h[String(day)][idx][field] = v;
    set("opening_hours", h);
  };

  async function save() {
    if (badMoney.length) { setConfirming(false); setMsg({ ok: false, t: "Fix the amounts first: " + badMoney.join(", ") }); return; }
    if (!(await reauthenticate(pw))) { setMsg({ ok: false, t: "Password incorrect." }); return; }
    setConfirming(false);
    setPw("");
    const changed: Record<string, unknown> = {};
    for (const k of Object.keys(s!)) {
      if (k === "updated_at" || k === "id") continue;
      if (JSON.stringify(s![k]) !== JSON.stringify(orig![k])) changed[k] = s![k] === "" ? null : s![k];
    }
    if (!Object.keys(changed).length) { setMsg({ ok: true, t: "No changes." }); return; }
    const { error } = await supabase.from("restaurant_settings").update(changed).eq("id", 1);
    if (error) setMsg({ ok: false, t: friendlyError(error.message) });
    else { setMsg({ ok: true, t: "Saved. Changes reach the app within a minute — no app update needed." }); void load(); }
  }

  return (
    <div>
      <div className="spread">
        <h1>Restaurant settings</h1>
        <button disabled={badMoney.length > 0} onClick={() => setConfirming(true)}>Save changes</button>
      </div>
      {msg && <div className={"banner " + (msg.ok ? "ok" : "danger")}>{msg.t}</div>}
      <div className="banner warn small">
        Operational switches stay OFF until you have tested ordering in the restaurant (PRD §20 launch-day checklist).
        Only turn on delivery if Swad Sangam actually delivers.
      </div>

      <h2>Ordering switches</h2>
      <div className="grid tight">
        {SWITCHES.map(([k, l]) => (
          <label key={k} className="check card"><input type="checkbox" checked={Boolean(s[k])} onChange={(e) => set(k, e.target.checked)} /> {l}</label>
        ))}
      </div>

      <h2>Opening hours (India time)</h2>
      <table><tbody>
        {DAYS.map((d, i) => {
          const day = i + 1;
          const slots = s.opening_hours[String(day)] ?? [];
          return (
            <tr key={d}>
              <td style={{ width: 120 }}>{d}</td>
              <td>
                {slots.length === 0 && <span className="muted">Closed</span>}
                {slots.map((sl, j) => (
                  <span key={j} className="row" style={{ display: "inline-flex", marginRight: 12 }}>
                    <input type="time" style={{ width: 120 }} value={sl.open} onChange={(e) => setHours(day, j, "open", e.target.value)} />–
                    <input type="time" style={{ width: 120 }} value={sl.close} onChange={(e) => setHours(day, j, "close", e.target.value)} />
                    <button className="ghost icon" aria-label={`Remove ${d} hours ${j + 1}`} onClick={() => { const h = { ...s.opening_hours, [day]: slots.filter((_, x) => x !== j) }; set("opening_hours", h); }}><X size={18} aria-hidden /></button>
                  </span>
                ))}
                <button className="link" onClick={() => set("opening_hours", { ...s.opening_hours, [day]: [...slots, { open: "06:00", close: "23:00" }] })}>+ add hours</button>
              </td>
            </tr>
          );
        })}
      </tbody></table>

      <h2>Charges</h2>
      <div className="form-grid">
        {MONEY.map(([k, l]) => (
          <label key={k}>{l}
            <input inputMode="decimal" value={money[k] ?? fromPaise(s[k] as number)} aria-invalid={money[k] !== undefined && toPaise(money[k] || "0") == null}
              onChange={(e) => {
                const v = e.target.value;
                setMoney({ ...money, [k]: v });
                const p = toPaise(v || "0");
                if (p != null) set(k, p);
              }} />
            {money[k] !== undefined && toPaise(money[k] || "0") == null && <span className="error small">Enter a valid amount, e.g. 20 or 20.50</span>}
          </label>
        ))}
        {NUM.map(([k, l]) => (
          <label key={k}>{l}<input type="number" value={String(s[k] ?? "")} onChange={(e) => set(k, Number(e.target.value))} /></label>
        ))}
      </div>
      <div className="form-grid">
        <label>GST scheme
          <select value={String(s.gst_scheme ?? "REGULAR")} onChange={(e) => set("gst_scheme", e.target.value)}>
            <option value="REGULAR">Regular</option><option value="COMPOSITION">Composition</option>
          </select>
        </label>
        <label>Receipt printer width
          <select value={String(s.receipt_width_mm ?? 80)} onChange={(e) => set("receipt_width_mm", Number(e.target.value))}>
            <option value="58">58 mm</option><option value="80">80 mm</option>
          </select>
        </label>
      </div>
      <p className="small muted">Confirm tax treatment and invoicing with your accountant before setting a tax rate (PRD §10.4, §12.4).</p>

      <Stations />

      <h2>Restaurant information</h2>
      <div className="form-grid">
        {TEXT.map(([k, l]) => (
          <label key={k}>{l}<input value={(s[k] as string | number | null) ?? ""} onChange={(e) => set(k, e.target.value)} /></label>
        ))}
        <div className="field-inline">
          <button type="button" className="secondary" onClick={() => setPickShop(true)}><MapPin size={16} /> {s.latitude != null && s.latitude !== "" ? "Change restaurant location on map" : "Set restaurant location on map"}</button>
          <span className="muted small">Needed for distance-based delivery fees and the rider's map. Easiest: open this page on your phone while at the restaurant and tap "Use my current location".</span>
        </div>
        {pickShop && <Suspense fallback={null}>
          <MapPicker lang="en" title="Restaurant location" hint="Put the pin exactly on Swad Sangam's door, then confirm and Save changes."
            initial={s.latitude != null && s.latitude !== "" ? { lat: Number(s.latitude), lng: Number(s.longitude) } : null} restaurant={null}
            onClose={() => setPickShop(false)} onPick={(p) => { setPickShop(false); setS({ ...s, latitude: String(p.lat), longitude: String(p.lng) }); }} />
        </Suspense>}
      </div>

      {confirming && (
        <Modal title="Confirm it's you" onClose={() => setConfirming(false)}>
          <p className="small">Settings changes are sensitive. Enter your password to continue.</p>
          <label>Password<input type="password" autoFocus value={pw} onChange={(e) => setPw(e.target.value)} onKeyDown={(e) => e.key === "Enter" && void save()} /></label>
          <button onClick={() => void save()}>Confirm and save</button>
        </Modal>
      )}
    </div>
  );
}

function Stations() {
  const [rows, setRows] = useState<{ id: number; name: string; is_active: boolean }[]>([]);
  const [cats, setCats] = useState<{ id: string; name: string; station_id: number | null }[]>([]);
  const [name, setName] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const load = async () => {
    const [a, b] = await Promise.all([supabase.from("kitchen_stations").select("*").order("sort_order"), supabase.from("categories").select("id,name,station_id").order("sort_order")]);
    setErr(a.error ? friendlyError(a.error.message) : b.error ? friendlyError(b.error.message) : null);
    setRows(a.data ?? []); setCats(b.data ?? []);
  };
  const run = async (q: PromiseLike<{ error: { message: string } | null }>) => { const { error } = await q; if (error) setErr(friendlyError(error.message)); else void load(); };
  useEffect(() => { void load(); }, []);
  return (
    <>
      <h2>Kitchen stations (KDS)</h2>
      <p className="small muted">Each menu category is sent to one kitchen station screen.</p>
      <div className="grid tight">
        {cats.map((c) => (
          <label key={c.id}>{c.name}
            <select value={c.station_id ?? ""} onChange={(e) => void run(supabase.from("categories").update({ station_id: e.target.value ? Number(e.target.value) : null }).eq("id", c.id))}>
              <option value="">—</option>{rows.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
            </select>
          </label>
        ))}
      </div>
      <div className="row">
        <input style={{ maxWidth: 220 }} placeholder="New station (e.g. Tandoor)" value={name} onChange={(e) => setName(e.target.value)} />
        <button className="small" disabled={!name.trim()} onClick={() => { void run(supabase.from("kitchen_stations").insert({ name: name.trim(), sort_order: rows.length + 1 })); setName(""); }}>Add station</button>
      </div>
      {err && <p className="error small" role="alert">{err}</p>}
    </>
  );
}
