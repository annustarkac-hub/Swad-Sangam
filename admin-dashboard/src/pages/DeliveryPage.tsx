import { MapPin, Phone, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import HandoverModal from "../components/HandoverModal";
import { notifyNative, playAlert } from "../lib/alert";
import { rupees, time } from "../lib/format";
import { friendlyError, supabase } from "../lib/supabase";
import { mapsLink, STATUS_LABEL, type OrderStatus } from "../lib/types";

interface RiderOrder {
  id: string; orderNumber: string; orderStatus: OrderStatus; customerName: string; customerPhone: string;
  deliveryAddress: string | null; deliveryLandmark: string | null; notes: string | null;
  deliveryLat?: number | null; deliveryLng?: number | null; deliveryDistanceKm?: number | null;
  totalPaise: number; paidPaise: number; duePaise: number; paymentStatus: string; paymentMethod: string;
  assignedAt: string | null; readyAt: string | null; completedAt: string | null; otpLocked: boolean;
  items: { name: string; quantity: number }[];
}

/**
 * Rider screen (DELIVERY role): only orders assigned to this rider. The customer's delivery code is never
 * sent here — the rider must get it from the customer at the door. Polls every 15 s (riders cannot use
 * Realtime on the orders table, by design).
 */
export default function DeliveryPage() {
  const [list, setList] = useState<RiderOrder[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [deliver, setDeliver] = useState<RiderOrder | null>(null);
  const known = useRef<Set<string> | null>(null);

  const load = useCallback(async () => {
    const { data, error } = await supabase.rpc("delivery_my_orders");
    if (error) return setErr(friendlyError(error.message));
    setErr(null);
    const l = (data as RiderOrder[]) ?? [];
    const fresh = l.filter((o) => o.orderStatus !== "COMPLETED" && known.current && !known.current.has(o.id));
    if (fresh.length) {
      playAlert();
      notifyNative("New delivery assigned", fresh.map((o) => `${o.orderNumber} — ${o.customerName}`).join("\n"));
    }
    known.current = new Set(l.map((o) => o.id));
    setList(l);
  }, []);

  useEffect(() => {
    void load();
    const t = setInterval(() => void load(), 15000);
    return () => clearInterval(t);
  }, [load]);

  async function start(o: RiderOrder) {
    setBusy(o.id);
    const { error } = await supabase.rpc("delivery_start", { p_order_id: o.id });
    setBusy(null);
    if (error) setErr(friendlyError(error.message)); else void load();
  }

  const active = list?.filter((o) => o.orderStatus !== "COMPLETED") ?? [];
  const done = list?.filter((o) => o.orderStatus === "COMPLETED") ?? [];
  const collected = done.reduce((s, o) => s + o.totalPaise, 0);

  return (
    <div className="rider">
      {/* The title is already in the app bar; this row says what the list holds */}
      <div className="spread">
        <span className="small muted">{list ? `${active.length} to deliver · ${done.length} delivered today` : " "}</span>
        <button className="secondary small" onClick={() => void load()} aria-label="Refresh"><RefreshCw size={16} /> Refresh</button>
      </div>
      {err && <p className="error" role="alert">{err}</p>}
      {list === null && <div className="skeleton-card" aria-label="Loading" />}
      {list && active.length === 0 && (
        <div className="empty-state"><p><b>No deliveries right now.</b></p><p className="small muted">New orders assigned to you will appear here with a sound.</p></div>
      )}
      {active.map((o) => (
        <article key={o.id} className="rider-card">
          <div className="spread">
            <b className="num">{o.orderNumber}</b>
            <span className={"badge " + o.orderStatus}>{STATUS_LABEL[o.orderStatus]}</span>
          </div>
          {o.duePaise > 0
            ? <div className="cod-banner"><span>COD · COLLECT</span><b className="num">{rupees(o.duePaise)}</b></div>
            : <div className="paid-banner">✓ PAID — nothing to collect</div>}
          <div className="rider-cust"><b>{o.customerName}</b></div>
          {o.deliveryAddress && <p className="rider-addr">{o.deliveryAddress}{o.deliveryLandmark ? ` · ${o.deliveryLandmark}` : ""}{o.deliveryDistanceKm != null ? ` · ~${o.deliveryDistanceKm} km` : ""}</p>}
          <div className="rider-actions">
            <a className="btn secondary" href={`tel:${o.customerPhone}`}><Phone size={18} /> Call</a>
            {o.deliveryAddress && <a className="btn secondary" href={mapsLink(o.deliveryAddress, o.deliveryLandmark, o.deliveryLat, o.deliveryLng)} target="_blank" rel="noreferrer"><MapPin size={18} /> {o.deliveryLat != null ? "Directions (pin)" : "Directions"}</a>}
          </div>
          <ul className="small">{o.items.map((i, k) => <li key={k}><b>{i.quantity} ×</b> {i.name}</li>)}</ul>
          {o.notes && <p className="small"><b>Note:</b> {o.notes}</p>}
          {o.orderStatus === "READY" && <button className="big-btn" disabled={busy === o.id} onClick={() => void start(o)}>Start delivery</button>}
          {o.orderStatus === "OUT_FOR_DELIVERY" && (o.otpLocked
            ? <p className="error small">Code locked after wrong tries — call the restaurant manager.</p>
            : <button className="ok big-btn" onClick={() => setDeliver(o)}>Deliver & collect {o.duePaise > 0 ? rupees(o.duePaise) : ""}</button>)}
          {["ACCEPTED", "PREPARING"].includes(o.orderStatus) && <p className="small muted">Being prepared — wait for “Ready”.</p>}
        </article>
      ))}
      {done.length > 0 && (
        <section className="card" style={{ marginTop: 16 }}>
          <h3 style={{ marginTop: 0 }}>Delivered today · {done.length} · {rupees(collected)}</h3>
          <ul className="small">{done.map((o) => <li key={o.id}>{o.orderNumber} — {o.customerName} — {rupees(o.totalPaise)} {o.completedAt && `· ${time(o.completedAt)}`}</li>)}</ul>
          <p className="small muted">Hand the cash you collected to the cashier.</p>
        </section>
      )}
      {deliver && (
        <HandoverModal
          order={{ id: deliver.id, orderNumber: deliver.orderNumber, dueNow: deliver.duePaise, customerName: deliver.customerName, appDelivery: true }}
          onClose={() => setDeliver(null)}
          onDone={() => { setDeliver(null); void load(); }}
        />
      )}
    </div>
  );
}
