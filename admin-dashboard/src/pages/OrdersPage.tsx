import { useEffect, useState } from "react";
import OrderCard from "../components/OrderCard";
import OrderDetail from "../components/OrderDetail";
import { useLiveOrders } from "../components/useOrders";
import { requestNotifyPermission, unlockAudio } from "../lib/alert";
import { dateTime, rupees } from "../lib/format";
import { ACTIVE_STATUSES } from "../lib/orderFlow";
import { supabase } from "../lib/supabase";
import { ORDER_TYPE_LABEL, PAYMENT_LABEL, STATUS_LABEL, type Order, type OrderStatus } from "../lib/types";

const COLUMNS: { title: string; statuses: OrderStatus[] }[] = [
  { title: "New", statuses: ["PLACED"] },
  { title: "Accepted", statuses: ["ACCEPTED"] },
  { title: "Preparing", statuses: ["PREPARING"] },
  { title: "Ready / out", statuses: ["READY", "OUT_FOR_DELIVERY"] },
];

export default function OrdersPage() {
  const [tab, setTab] = useState<"live" | "history">("live");
  return (
    <div>
      <div className="spread">
        <h1>Orders</h1>
        <div className="tabs">
          <button className={tab === "live" ? "active" : ""} onClick={() => setTab("live")}>Live</button>
          <button className={tab === "history" ? "active" : ""} onClick={() => setTab("history")}>History</button>
        </div>
      </div>
      {tab === "live" ? <LiveBoard /> : <History />}
    </div>
  );
}

function LiveBoard() {
  // New-order sound + notification come from the app shell (works on every page, incl. POS)
  const { orders, error, connected, reload } = useLiveOrders(ACTIVE_STATUSES);
  const [open, setOpen] = useState<Order | null>(null);
  const [col, setCol] = useState(0);
  useEffect(() => { unlockAudio(); void requestNotifyPermission(); }, []);
  const current = open ? orders.find((o) => o.id === open.id) ?? open : null;

  return (
    <>
      <p className="small muted">
        {connected ? "● Live" : "○ Reconnecting… (refreshes every 30 s)"} · Keep this page open to hear new-order alerts.
      </p>
      {error && <p className="error">{error}</p>}
      {/* Phones: one column at a time with counts, instead of scrolling past empty columns */}
      <div className="seg board-tabs" role="tablist" aria-label="Order status">
        {COLUMNS.map((c, i) => (
          <button key={c.title} role="tab" aria-selected={col === i} className={col === i ? "on" : ""} onClick={() => setCol(i)}>
            {c.title === "Ready / out" ? "Ready" : c.title} · {orders.filter((o) => c.statuses.includes(o.orderStatus)).length}
          </button>
        ))}
      </div>
      <div className="board">
        {COLUMNS.map((c, i) => {
          const list = orders.filter((o) => c.statuses.includes(o.orderStatus)).sort((a, b) => a.createdAt.localeCompare(b.createdAt));
          return (
            <section key={c.title} className={"column" + (col === i ? " current" : "")}>
              <h3>{c.title} ({list.length})</h3>
              {list.map((o) => <OrderCard key={o.id} order={o} onChanged={reload} onOpen={() => setOpen(o)} />)}
              {list.length === 0 && <p className="small muted empty-col">Nothing here.</p>}
            </section>
          );
        })}
      </div>
      {current && <OrderDetail order={current} onClose={() => setOpen(null)} onChanged={() => void reload()} />}
    </>
  );
}

const PAGE = 50;

function History() {
  const [rows, setRows] = useState<Order[]>([]);
  const [page, setPage] = useState(0);
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<string>("");
  const [open, setOpen] = useState<Order | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function load(p = page) {
    const { data, error } = await supabase.rpc("admin_list_orders", {
      p_statuses: status ? [status] : null, p_search: q.trim() || null, p_limit: PAGE, p_offset: p * PAGE,
    });
    if (error) setErr(error.message); else { setErr(null); setRows(data as Order[]); }
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load(page); }, [page, status]);

  return (
    <>
      <div className="row" style={{ marginBottom: 12 }}>
        <input style={{ maxWidth: 260 }} placeholder="Search order no., name or phone" value={q}
          onChange={(e) => setQ(e.target.value)} onKeyDown={(e) => e.key === "Enter" && (setPage(0), void load(0))} />
        <select style={{ maxWidth: 180 }} value={status} onChange={(e) => { setPage(0); setStatus(e.target.value); }}>
          <option value="">All statuses</option>
          {Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <button className="small" onClick={() => { setPage(0); void load(0); }}>Search</button>
      </div>
      {err && <p className="error">{err}</p>}
      <div className="table-wrap">
        <table>
          <thead><tr><th>Order</th><th>Placed</th><th>Customer</th><th>Type</th><th>Status</th><th>Payment</th><th className="right">Total</th></tr></thead>
          <tbody>
            {rows.map((o) => (
              <tr key={o.id} onClick={() => setOpen(o)} style={{ cursor: "pointer" }}>
                <td>{o.orderNumber}</td><td>{dateTime(o.createdAt)}</td><td>{o.customerName}<br /><span className="small muted">{o.customerPhone}</span></td>
                <td>{ORDER_TYPE_LABEL[o.orderType]}</td>
                <td><span className={"badge " + o.orderStatus}>{STATUS_LABEL[o.orderStatus]}</span></td>
                <td className="small">{PAYMENT_LABEL[o.paymentMethod]}<br />{PAYMENT_LABEL[o.paymentStatus]}</td>
                <td className="right">{rupees(o.totalPaise)}</td>
              </tr>
            ))}
            {rows.length === 0 && <tr><td colSpan={7} className="muted">No orders found.</td></tr>}
          </tbody>
        </table>
      </div>
      <div className="row" style={{ marginTop: 8 }}>
        <button className="secondary small" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
        <span className="small">Page {page + 1}</span>
        <button className="secondary small" disabled={rows.length < PAGE} onClick={() => setPage(page + 1)}>Next</button>
      </div>
      {open && <OrderDetail order={open} onClose={() => setOpen(null)} onChanged={() => { setOpen(null); void load(); }} />}
    </>
  );
}
