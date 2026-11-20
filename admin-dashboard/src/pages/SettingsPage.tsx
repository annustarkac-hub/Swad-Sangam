import { useEffect, useState } from "react";
import { useAuth } from "../auth";
import Modal from "../components/Modal";
import { fromPaise, toPaise } from "../lib/format";
import { friendlyError, supabase } from "../lib/supabase";

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
  ["dine_in_info", "Dine-in information"], ["delivery_area_note", "Delivery area description"], ["delivery_radius_km", "Delivery radius (km)"],
  ["tax_label", "Tax label (e.g. GST)"], ["rating", "Public rating (e.g. 4.5)"], ["review_count", "Public review count"],
  ["reviews_url", "Link to public reviews (Google)"], ["privacy_policy_url", "Privacy policy URL"], ["terms_url", "Order terms URL"],
];
const MONEY: [string, string][] = [
  ["min_order_paise", "Minimum order (₹)"], ["delivery_min_order_paise", "Minimum order for delivery (₹)"],
  ["delivery_fee_paise", "Delivery fee (₹)"], ["packaging_fee_paise", "Packaging fee for pickup/delivery (₹)"],
  ["budget_threshold_paise", "“Budget meals” price limit (₹)"],
];
const NUM: [string, string][] = [
  ["tax_rate_bps", "Tax rate in basis points (500 = 5%)"], ["default_prep_minutes", "Default preparation time (min)"],
  ["max_open_orders_per_phone", "Max open orders per phone"], ["max_item_quantity", "Max quantity per item"],
  ["pii_retention_days", "Delete customer details after (days)"],
];
const SWITCHES: [string, string][] = [
  ["accepting_orders", "Accepting app orders (master switch)"], ["pickup_enabled", "Pickup orders"],
  ["dine_in_enabled", "Dine-in / table orders"], ["delivery_enabled", "Delivery orders (only if you deliver)"],
  ["cash_enabled", "Cash on pickup/delivery"], ["upi_at_restaurant_enabled", "UPI at restaurant"],
  ["temporarily_closed", "Temporarily closed"], ["allow_cancel_after_accept", "Customers may cancel after acceptance"],
  ["prices_include_tax", "Menu prices include tax"], ["hours_verified", "Opening hours confirmed by owner"],
];

export default function SettingsPage() {
  const { reauthenticate } = useAuth();
  const [s, setS] = useState<Settings | null>(null);
  const [orig, setOrig] = useState<Settings | null>(null);
  const [msg, setMsg] = useState<{ ok: boolean; t: string } | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [pw, setPw] = useState("");

  async function load() {
    const { data, error } = await supabase.from("restaurant_settings").select("*").single();
    if (error) setMsg({ ok: false, t: friendlyError(error.message) });
    else { setS(data as Settings); setOrig(data as Settings); }
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
        <button onClick={() => setConfirming(true)}>Save changes</button>
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
                    <button className="link" onClick={() => { const h = { ...s.opening_hours, [day]: slots.filter((_, x) => x !== j) }; set("opening_hours", h); }}>✕</button>
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
          <label key={k + String(orig?.updated_at)}>{l}<input inputMode="decimal" defaultValue={fromPaise(s[k] as number)} onBlur={(e) => set(k, toPaise(e.target.value || "0") ?? 0)} /></label>
        ))}
        {NUM.map(([k, l]) => (
          <label key={k}>{l}<input type="number" value={String(s[k] ?? "")} onChange={(e) => set(k, Number(e.target.value))} /></label>
        ))}
      </div>
      <p className="small muted">Confirm tax treatment and invoicing with your accountant before setting a tax rate (PRD §10.4, §12.4).</p>

      <h2>Restaurant information</h2>
      <div className="form-grid">
        {TEXT.map(([k, l]) => (
          <label key={k}>{l}<input value={(s[k] as string | number | null) ?? ""} onChange={(e) => set(k, e.target.value)} /></label>
        ))}
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
