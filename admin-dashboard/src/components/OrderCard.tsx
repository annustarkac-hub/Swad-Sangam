import { useEffect, useState } from "react";
import { can, useAuth } from "../auth";
import { minutesSince, rupees, time } from "../lib/format";
import { nextActions, type NextAction } from "../lib/orderFlow";
import { friendlyError, supabase } from "../lib/supabase";
import { duePaise, mapsLink, ORDER_TYPE_LABEL, PAYMENT_LABEL, STATUS_LABEL, type Order } from "../lib/types";
import HandoverModal from "./HandoverModal";
import Modal from "./Modal";

type Rider = { userId: string; name: string; activeOrders: number };
let ridersCache: { at: number; list: Rider[] } | null = null;
/** Active delivery riders (cached for 60 s across cards). */
function useRiders(enabled: boolean) {
  const [list, setList] = useState<Rider[]>(ridersCache?.list ?? []);
  useEffect(() => {
    if (!enabled) return;
    if (ridersCache && Date.now() - ridersCache.at < 60000) { setList(ridersCache.list); return; }
    void supabase.rpc("list_delivery_staff").then(({ data }) => {
      const l = (data as Rider[]) ?? [];
      ridersCache = { at: Date.now(), list: l };
      setList(l);
    });
  }, [enabled]);
  return list;
}

/** Bold payment pill: what staff must collect, or that it is paid. */
export function PaymentPill({ order }: { order: Order }) {
  const due = duePaise(order);
  if (order.paymentStatus === "PAID") return <span className="pay-pill paid">✓ PAID</span>;
  if (order.orderStatus === "REJECTED" || order.orderStatus === "CANCELLED") return <span className="pay-pill">{PAYMENT_LABEL[order.paymentStatus]}</span>;
  return <span className="pay-pill due">{order.paymentMethod === "UPI_AT_RESTAURANT" ? "UPI" : order.orderType === "DELIVERY" ? "COD" : "CASH"} · COLLECT {rupees(due)}</span>;
}

export function FoodDot({ type }: { type: "VEG" | "NON_VEG" }) {
  return <span className={type === "VEG" ? "veg" : "nonveg"} aria-label={type === "VEG" ? "Vegetarian" : "Non-vegetarian"} title={type === "VEG" ? "Veg" : "Non-veg"} />;
}

export default function OrderCard({ order, onChanged, onOpen, kitchen }: {
  order: Order; onChanged: () => void; onOpen?: () => void; kitchen?: boolean;
}) {
  const { role } = useAuth();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [reasonFor, setReasonFor] = useState<NextAction | null>(null);
  const [reason, setReason] = useState("");
  const [eta, setEta] = useState("");
  const [handover, setHandover] = useState(false);
  const isDelivery = order.orderType === "DELIVERY";
  const canAssign = !kitchen && isDelivery && can(role, "OWNER", "MANAGER", "CASHIER", "STAFF") && role !== "WAITER"
    && ["ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY"].includes(order.orderStatus);
  const riders = useRiders(canAssign);

  async function assign(userId: string) {
    setBusy(true);
    setErr(null);
    const { error } = await supabase.rpc("assign_delivery_staff", { p_order_id: order.id, p_staff_user_id: userId || null });
    setBusy(false);
    if (error) setErr(friendlyError(error.message)); else { ridersCache = null; onChanged(); }
  }
  const age = minutesSince(order.createdAt);
  const late = order.orderStatus === "PLACED" && age >= 2; // PRD: acknowledge within 2 minutes

  async function act(a: NextAction, r?: string) {
    setBusy(true);
    setErr(null);
    const { error } = await supabase.rpc("update_order_status", {
      p_order_id: order.id,
      p_status: a.to,
      p_reason: r ?? null,
      p_eta_minutes: a.to === "ACCEPTED" && eta ? Number(eta) : null,
    });
    setBusy(false);
    if (error) setErr(friendlyError(error.message));
    else onChanged();
  }

  return (
    <div className={"order-card" + (late ? " late" : "")}>
      <div className="spread">
        <b>{order.orderNumber}</b>
        <span className={"badge " + order.orderStatus}>{STATUS_LABEL[order.orderStatus]}</span>
      </div>
      <div className="small muted">
        {ORDER_TYPE_LABEL[order.orderType]}
        {order.tableNumber ? ` · Table ${order.tableNumber}` : ""} · {time(order.createdAt)} ({age} min ago)
      </div>
      {!kitchen && (
        <div className="small">
          {order.customerName}
          {order.customerPhone && (
            <> · <a href={`tel:${order.customerPhone}`}>{order.customerPhone}</a></>
          )}
        </div>
      )}
      <ul>
        {order.items.map((i, idx) => (
          <li key={idx}>
            <FoodDot type={i.foodType} /> <b>{i.quantity} ×</b> {i.nameSnapshot}
            {i.options.length > 0 && <span className="muted"> ({i.options.map((o) => o.name).join(", ")})</span>}
          </li>
        ))}
      </ul>
      {(order.notes || order.pickupInstructions) && (
        <div className="small"><b>Note:</b> {[order.notes, order.pickupInstructions].filter(Boolean).join(" · ")}</div>
      )}
      {isDelivery && order.deliveryAddress && !kitchen && (
        <div className="small"><b>Address:</b> {order.deliveryAddress} {order.deliveryLandmark ? `(${order.deliveryLandmark})` : ""}{" "}
          <a href={mapsLink(order.deliveryAddress, order.deliveryLandmark)} target="_blank" rel="noreferrer">Directions ↗</a></div>
      )}
      {!kitchen && (
        <div className="pay-row">
          <b className="num">{rupees(order.totalPaise)}</b>
          <PaymentPill order={order} />
        </div>
      )}
      {canAssign && (
        <label className="small rider-pick">Rider
          <select value={order.assignedStaffId ?? ""} disabled={busy} onChange={(e) => void assign(e.target.value)}>
            <option value="">{riders.length ? "— Not assigned —" : "No riders yet (Staff logins → role Delivery)"}</option>
            {riders.map((r) => <option key={r.userId} value={r.userId}>{r.name}{r.activeOrders ? ` (${r.activeOrders} active)` : ""}</option>)}
          </select>
        </label>
      )}
      {!canAssign && isDelivery && order.assignedStaffName && !kitchen && <div className="small">Rider: <b>{order.assignedStaffName}</b></div>}
      {isDelivery && order.otpLocked && can(role, "OWNER", "MANAGER") && order.orderStatus === "OUT_FOR_DELIVERY" && (
        <div className="small error">Delivery code locked after 5 wrong tries.{" "}
          <button className="link small" disabled={busy} onClick={async () => {
            setBusy(true); setErr(null);
            const { error } = await supabase.rpc("unlock_delivery_code", { p_order_id: order.id });
            setBusy(false);
            if (error) setErr(friendlyError(error.message)); else onChanged();
          }}>Unlock code</button>
        </div>
      )}
      {isDelivery && order.deliveryOtp && !kitchen && ["ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY"].includes(order.orderStatus) && (
        <div className="small muted">Customer's delivery code: <b className="num">{order.deliveryOtp}</b> (owner/manager only — don't tell the rider)</div>
      )}
      {late && <div className="small error">Waiting {age} min for confirmation — accept or call the customer.</div>}
      {order.orderStatus === "PLACED" && role !== "KITCHEN" && (
        <div className="eta-row" role="group" aria-label="Ready in (minutes, optional)">
          <span className="small muted">Ready in (min)</span>
          {["10", "15", "20", "30"].map((m) => (
            <button key={m} type="button" className={"chip" + (eta === m ? " on" : "")} aria-pressed={eta === m} aria-label={`${m} minutes`} onClick={() => setEta(eta === m ? "" : m)}>{m}</button>
          ))}
        </div>
      )}
      <div className="actions">
        {role && nextActions(order, role).map((a) => (
          <button key={a.to} className={a.danger ? "danger small" : "ok small"} disabled={busy}
            onClick={() => (a.handover ? setHandover(true) : a.needsReason ? setReasonFor(a) : void act(a))}>
            {a.label}
          </button>
        ))}
        {onOpen && <button className="secondary small" onClick={onOpen}>Details</button>}
      </div>
      {err && <div className="error small">{err}</div>}
      {handover && (
        <HandoverModal
          order={{ id: order.id, orderNumber: order.orderNumber, dueNow: duePaise(order), customerName: order.customerName,
                   appDelivery: isDelivery && (order.source ?? "APP") === "APP" }}
          onClose={() => setHandover(false)}
          onDone={() => { setHandover(false); onChanged(); }}
        />
      )}
      {reasonFor && (
        <Modal title={`${reasonFor.label} order ${order.orderNumber}`} onClose={() => setReasonFor(null)}>
          <p className="small muted">The customer will see this reason.</p>
          <div className="row">
            {["Item not available", "Kitchen too busy", "Restaurant closing", "Customer unreachable"].map((r) => (
              <button key={r} className="secondary small" onClick={() => setReason(r)}>{r}</button>
            ))}
          </div>
          <label>Reason<textarea value={reason} onChange={(e) => setReason(e.target.value)} /></label>
          <button className="danger" disabled={reason.trim().length < 3 || busy}
            onClick={() => { void act(reasonFor, reason); setReasonFor(null); setReason(""); }}>
            Confirm
          </button>
        </Modal>
      )}
    </div>
  );
}
