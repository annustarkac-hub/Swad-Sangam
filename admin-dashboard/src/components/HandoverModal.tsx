import QRCode from "qrcode";
import { useEffect, useState } from "react";
import { can, useAuth } from "../auth";
import { upiLink } from "../lib/erp";
import { rupees } from "../lib/format";
import { friendlyError, supabase } from "../lib/supabase";
import Modal from "./Modal";

type Tender = "CASH" | "UPI" | "CARD" | "BANK";

/**
 * Hand-over / delivery confirmation. Nothing is marked completed until the money is recorded,
 * and app delivery orders also need the customer's 4-digit delivery code (shown in their app).
 * Calls complete_order_with_payment, which does payment + completion in one database transaction.
 */
export default function HandoverModal({ order, onClose, onDone }: {
  order: { id: string; orderNumber: string; dueNow: number; appDelivery: boolean; customerName?: string };
  onClose: () => void;
  onDone: () => void;
}) {
  const { role } = useAuth();
  const manager = can(role, "OWNER", "MANAGER");
  const [tender, setTender] = useState<Tender>("CASH");
  const [ref, setRef] = useState("");
  const [otp, setOtp] = useState("");
  const [override, setOverride] = useState(false);
  const [reason, setReason] = useState("");
  const [received, setReceived] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [upi, setUpi] = useState<{ id: string; name: string } | null>(null);
  const [qr, setQr] = useState<string | null>(null);

  useEffect(() => {
    void supabase.from("restaurant_settings").select("upi_id, restaurant_name").single().then(({ data }) => {
      if (data?.upi_id) setUpi({ id: data.upi_id, name: data.restaurant_name });
    });
  }, []);
  useEffect(() => {
    if (tender === "UPI" && upi && order.dueNow > 0) {
      void QRCode.toDataURL(upiLink(upi.id, upi.name, order.dueNow, order.orderNumber), { width: 200, margin: 1 }).then(setQr);
    } else setQr(null);
  }, [tender, upi, order.dueNow, order.orderNumber]);

  const needsOtp = order.appDelivery && !override;
  const ready = (order.dueNow === 0 || received) && (!needsOtp || /^\d{4}$/.test(otp)) && (!override || reason.trim().length >= 3);

  async function submit() {
    setBusy(true);
    setErr(null);
    const { data, error } = await supabase.rpc("complete_order_with_payment", {
      p_order_id: order.id, p_tender: tender, p_reference: ref.trim() || null,
      p_delivery_otp: order.appDelivery && !override ? otp : null,
      p_override_reason: override ? reason.trim() : null,
    });
    setBusy(false);
    if (error) return setErr(friendlyError(error.message));
    const res = data as { ok?: boolean; error?: string; message?: string; details?: { attemptsLeft?: number } };
    if (res?.error) {
      setErr(res.message + (res.details?.attemptsLeft != null ? ` (${res.details.attemptsLeft} tries left)` : ""));
      if (res.error === "WRONG_OTP") setOtp("");
      return;
    }
    onDone();
  }

  return (
    <Modal title={(order.appDelivery ? "Deliver " : "Hand over ") + order.orderNumber} onClose={onClose}>
      {order.dueNow > 0 ? (
        <div className="cod-banner" role="status">
          <span>COLLECT</span><b className="num">{rupees(order.dueNow)}</b>
          {order.customerName && <span className="small">from {order.customerName}</span>}
        </div>
      ) : (
        <div className="paid-banner" role="status">✓ Already paid — just hand over</div>
      )}

      {order.dueNow > 0 && (
        <>
          <div className="seg" role="radiogroup" aria-label="Payment received by">
            {(["CASH", "UPI", "CARD", "BANK"] as Tender[]).map((t) => (
              <button key={t} type="button" role="radio" aria-checked={tender === t} className={tender === t ? "on" : ""} onClick={() => setTender(t)}>
                {t === "CASH" ? "Cash" : t === "UPI" ? "UPI" : t === "CARD" ? "Card" : "Bank"}
              </button>
            ))}
          </div>
          {qr && <div className="center"><img src={qr} alt={`UPI QR for ${rupees(order.dueNow)}`} width={200} height={200} /><p className="small muted">Customer scans to pay {rupees(order.dueNow)}</p></div>}
          {tender !== "CASH" && (
            <label>UPI / transaction ref (optional)<input value={ref} onChange={(e) => setRef(e.target.value)} placeholder="e.g. last 4–6 digits of UTR" /></label>
          )}
          <label className="check-row">
            <input type="checkbox" checked={received} onChange={(e) => setReceived(e.target.checked)} />
            I have received {rupees(order.dueNow)} by {tender === "CASH" ? "cash" : tender}
          </label>
        </>
      )}

      {order.appDelivery && (
        <>
          {!override && (
            <label className="otp-field">Customer's delivery code
              <input className="otp-input" inputMode="numeric" autoComplete="one-time-code" maxLength={4} value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 4))} placeholder="••••" aria-describedby="otp-help" />
              <span id="otp-help" className="small muted field-help">Ask the customer — it is shown on their order screen.</span>
            </label>
          )}
          {manager && (
            <label className="check-row small">
              <input type="checkbox" checked={override} onChange={(e) => setOverride(e.target.checked)} />
              Customer can't show the code (owner/manager override, saved in audit log)
            </label>
          )}
          {override && <label>Reason<input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. phone switched off, verified by call" /></label>}
        </>
      )}

      {err && <p className="error" role="alert">{err}</p>}
      <button className="ok big-btn" disabled={!ready || busy} onClick={() => void submit()}>
        {busy ? "Saving…" : order.appDelivery ? "Confirm delivery" : "Confirm hand over"}
      </button>
    </Modal>
  );
}
