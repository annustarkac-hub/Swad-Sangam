import { useState } from "react";
import { can, useAuth } from "../auth";
import { dateTime, rupees, toPaise } from "../lib/format";
import { friendlyError, supabase } from "../lib/supabase";
import { ORDER_TYPE_LABEL, PAYMENT_LABEL, STATUS_LABEL, type Order, type PaymentStatus } from "../lib/types";
import Modal from "./Modal";
import { FoodDot } from "./OrderCard";

const PAYMENT_NEXT: Record<PaymentStatus, PaymentStatus[]> = {
  PENDING: ["PAID", "FAILED"],
  FAILED: ["PAID"],
  PAID: ["REFUND_PENDING"],
  REFUND_PENDING: ["REFUNDED"],
  REFUNDED: [],
};

export default function OrderDetail({ order, onClose, onChanged }: { order: Order; onClose: () => void; onChanged: () => void }) {
  const { role } = useAuth();
  const [err, setErr] = useState<string | null>(null);
  const [ref, setRef] = useState("");
  const [adjType, setAdjType] = useState<"REFUND" | "CORRECTION">("REFUND");
  const [adjAmount, setAdjAmount] = useState("");
  const [adjReason, setAdjReason] = useState("");

  async function setPayment(s: PaymentStatus) {
    const { error } = await supabase.rpc("update_payment_status", { p_order_id: order.id, p_status: s, p_reference: ref || null });
    if (error) setErr(friendlyError(error.message));
    else onChanged();
  }

  async function addAdjustment() {
    const p = toPaise(adjAmount);
    if (!p || adjReason.trim().length < 3) return setErr("Enter an amount and a reason.");
    const { error } = await supabase.rpc("record_order_adjustment", {
      p_order_id: order.id, p_type: adjType, p_amount_paise: adjType === "REFUND" ? p : p, p_reason: adjReason,
    });
    if (error) setErr(friendlyError(error.message));
    else { setAdjAmount(""); setAdjReason(""); onChanged(); }
  }

  return (
    <Modal title={`Order ${order.orderNumber}`} onClose={onClose}>
      <p>
        <span className={"badge " + order.orderStatus}>{STATUS_LABEL[order.orderStatus]}</span>{" "}
        {ORDER_TYPE_LABEL[order.orderType]} {order.tableNumber && `· Table ${order.tableNumber}`} · placed {dateTime(order.createdAt)}
      </p>
      <p>
        <b>{order.customerName}</b> {order.customerPhone && <a href={`tel:${order.customerPhone}`}>{order.customerPhone}</a>}
        {order.deliveryAddress && <><br />Address: {order.deliveryAddress} {order.deliveryLandmark}</>}
      </p>
      {(order.notes || order.pickupInstructions) && <p><b>Notes:</b> {order.notes} {order.pickupInstructions}</p>}
      {order.rejectionReason && <p className="error">Rejected: {order.rejectionReason}</p>}
      {order.cancelReason && <p className="error">Cancelled by {order.cancelledBy?.toLowerCase()}: {order.cancelReason}</p>}
      <table>
        <tbody>
          {order.items.map((i, idx) => (
            <tr key={idx}>
              <td><FoodDot type={i.foodType} /> {i.nameSnapshot} {i.options.length > 0 && <span className="muted small">({i.options.map((o) => o.name).join(", ")})</span>}</td>
              <td className="right">{i.quantity} × {rupees(i.unitPricePaise)}</td>
              <td className="right">{rupees(i.lineTotalPaise)}</td>
            </tr>
          ))}
          <tr><td colSpan={2}>Subtotal</td><td className="right">{rupees(order.subtotalPaise)}</td></tr>
          {order.discountPaise > 0 && <tr><td colSpan={2}>Discount {order.couponCode}</td><td className="right">−{rupees(order.discountPaise)}</td></tr>}
          {order.taxPaise > 0 && <tr><td colSpan={2}>Tax</td><td className="right">{rupees(order.taxPaise)}</td></tr>}
          {order.packagingFeePaise > 0 && <tr><td colSpan={2}>Packaging</td><td className="right">{rupees(order.packagingFeePaise)}</td></tr>}
          {order.deliveryFeePaise > 0 && <tr><td colSpan={2}>Delivery</td><td className="right">{rupees(order.deliveryFeePaise)}</td></tr>}
          <tr><th colSpan={2}>Total</th><th className="right">{rupees(order.totalPaise)}</th></tr>
        </tbody>
      </table>

      <h3 style={{ marginTop: 16 }}>Payment — {PAYMENT_LABEL[order.paymentMethod]}: {PAYMENT_LABEL[order.paymentStatus]}</h3>
      {order.paymentReference && <p className="small">Reference: {order.paymentReference}</p>}
      {PAYMENT_NEXT[order.paymentStatus].length > 0 && (
        <div className="row">
          <input style={{ maxWidth: 220 }} placeholder="UPI ref / note (optional)" value={ref} onChange={(e) => setRef(e.target.value)} />
          {PAYMENT_NEXT[order.paymentStatus].map((s) => (
            <button key={s} className="small" onClick={() => void setPayment(s)}>Mark {PAYMENT_LABEL[s].toLowerCase()}</button>
          ))}
        </div>
      )}

      {can(role, "OWNER", "MANAGER") && (
        <>
          <h3 style={{ marginTop: 16 }}>Refund / correction (audited)</h3>
          <p className="small muted">Order amounts are never edited. Record a refund or correction here; it appears in reports.</p>
          <div className="row">
            <select style={{ maxWidth: 150 }} value={adjType} onChange={(e) => setAdjType(e.target.value as "REFUND" | "CORRECTION")}>
              <option value="REFUND">Refund</option>
              <option value="CORRECTION">Correction</option>
            </select>
            <input style={{ maxWidth: 120 }} placeholder="₹ amount" value={adjAmount} onChange={(e) => setAdjAmount(e.target.value)} />
            <input style={{ maxWidth: 260 }} placeholder="Reason" value={adjReason} onChange={(e) => setAdjReason(e.target.value)} />
            <button className="small" onClick={() => void addAdjustment()}>Record</button>
          </div>
        </>
      )}

      <h3 style={{ marginTop: 16 }}>Timeline</h3>
      <ul className="small">
        {order.history.map((h, i) => <li key={i}>{dateTime(h.at)} — {STATUS_LABEL[h.status]}</li>)}
      </ul>
      {err && <p className="error">{err}</p>}
    </Modal>
  );
}
