import { useState } from "react";
import { useAuth } from "../auth";
import { minutesSince, rupees, time } from "../lib/format";
import { nextActions, type NextAction } from "../lib/orderFlow";
import { friendlyError, supabase } from "../lib/supabase";
import { ORDER_TYPE_LABEL, PAYMENT_LABEL, STATUS_LABEL, type Order } from "../lib/types";
import Modal from "./Modal";

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
      {order.orderType === "DELIVERY" && order.deliveryAddress && !kitchen && (
        <div className="small"><b>Address:</b> {order.deliveryAddress} {order.deliveryLandmark ? `(${order.deliveryLandmark})` : ""}</div>
      )}
      {!kitchen && (
        <div className="small">
          <b>{rupees(order.totalPaise)}</b> · {PAYMENT_LABEL[order.paymentMethod]} · {PAYMENT_LABEL[order.paymentStatus]}
        </div>
      )}
      {late && <div className="small error">Waiting {age} min for confirmation — accept or call the customer.</div>}
      {order.orderStatus === "PLACED" && role !== "KITCHEN" && (
        <label className="small" style={{ marginTop: 6 }}>
          Ready in (minutes, optional)
          <input type="number" min={1} max={240} value={eta} onChange={(e) => setEta(e.target.value)} />
        </label>
      )}
      <div className="actions">
        {role && nextActions(order, role).map((a) => (
          <button key={a.to} className={a.danger ? "danger small" : "ok small"} disabled={busy}
            onClick={() => (a.needsReason ? setReasonFor(a) : void act(a))}>
            {a.label}
          </button>
        ))}
        {onOpen && <button className="secondary small" onClick={onOpen}>Details</button>}
      </div>
      {err && <div className="error small">{err}</div>}
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
