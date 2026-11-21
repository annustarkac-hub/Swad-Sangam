import { useEffect, useState } from "react";
import { downloadCsv } from "../lib/csv";
import { rupees, todayIST } from "../lib/format";
import { friendlyError, supabase } from "../lib/supabase";
import { ORDER_TYPE_LABEL, PAYMENT_LABEL } from "../lib/types";

interface Report {
  orderCount: number; completedCount: number; cancelledCount: number; rejectedCount: number; cancellationRate: number;
  grossOrderValuePaise: number; discountsPaise: number; taxesPaise: number; feesPaise: number; refundsPaise: number;
  correctionsPaise: number; paidReceiptsPaise: number; netReceiptsPaise: number; averageOrderValuePaise: number;
  byDay: { date: string; orders: number; completed: number; grossPaise: number }[];
  byItem: { itemId: string; name: string; quantity: number; revenuePaise: number }[];
  byCategory: { category: string; quantity: number; revenuePaise: number }[];
  byPaymentMethod: { method: string; orders: number; totalPaise: number }[];
  byOrderType: { type: string; orders: number }[];
  customers: { unique: number; new: number; returning: number };
  funnel: Record<string, number> | null;
}

const RANGES = [
  { label: "Today", from: 0 },
  { label: "Last 7 days", from: -6 },
  { label: "Last 30 days", from: -29 },
];
/** First and last day (YYYY-MM-DD, India time) of this month (offset 0) or an earlier month (offset -1, -2 …). */
function monthRange(offset: number): [string, string] {
  const [y, m] = todayIST().split("-").map(Number);
  const d = new Date(Date.UTC(y, m - 1 + offset, 1));
  const last = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0));
  const f = (x: Date) => x.toISOString().slice(0, 10);
  return [f(d), offset === 0 ? todayIST() : f(last)];
}
const FUNNEL = ["app_opened", "menu_opened", "item_added_to_cart", "checkout_started", "order_submitted", "order_completed", "call_clicked", "directions_clicked"];

export default function ReportsPage() {
  const [from, setFrom] = useState(todayIST(-6));
  const [to, setTo] = useState(todayIST());
  const [r, setR] = useState<Report | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function load() {
    const { data, error } = await supabase.rpc("sales_report", { p_from: from, p_to: to });
    if (error) setErr(friendlyError(error.message)); else { setErr(null); setR(data as Report); }
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [from, to]);

  const maxDay = Math.max(1, ...(r?.byDay.map((d) => d.grossPaise) ?? [1]));
  const maxFunnel = Math.max(1, r?.funnel?.app_opened ?? 1);

  return (
    <div>
      <h1>Reports</h1>
      <div className="row" style={{ marginBottom: 12 }}>
        {RANGES.map((x) => <button key={x.label} className="secondary small" onClick={() => { setFrom(todayIST(x.from)); setTo(todayIST()); }}>{x.label}</button>)}
        <button className="secondary small" onClick={() => { const [a, b] = monthRange(0); setFrom(a); setTo(b); }}>This month</button>
        <button className="secondary small" onClick={() => { const [a, b] = monthRange(-1); setFrom(a); setTo(b); }}>Last month</button>
        <button className="secondary small" onClick={() => { setFrom(monthRange(-11)[0]); setTo(todayIST()); }}>Last 12 months</button>
        <input type="date" style={{ maxWidth: 170 }} value={from} onChange={(e) => setFrom(e.target.value)} />
        <span>to</span>
        <input type="date" style={{ maxWidth: 170 }} value={to} onChange={(e) => setTo(e.target.value)} />
        {r && <button className="small" onClick={() => downloadCsv(`swad-sangam-items-${from}-${to}.csv`, [["Item", "Quantity", "Revenue (INR)"], ...r.byItem.map((i) => [i.name, i.quantity, i.revenuePaise / 100])])}>Export items CSV</button>}
        {r && <button className="small" onClick={() => downloadCsv(`swad-sangam-daily-${from}-${to}.csv`, [["Date", "Orders", "Completed", "Gross (INR)"], ...r.byDay.map((d) => [d.date, d.orders, d.completed, d.grossPaise / 100])])}>Export daily CSV</button>}
      </div>
      {err && <p className="error">{err}</p>}
      {r && (
        <>
          <h2>Money (completed orders)</h2>
          <p className="small muted">These figures are kept separate on purpose. Gross order value is food value before discounts, taxes and fees. Net receipts = payments received − refunds ± corrections.</p>
          <div className="grid tight">
            <Stat l="Gross order value" v={rupees(r.grossOrderValuePaise)} />
            <Stat l="Discounts" v={"−" + rupees(r.discountsPaise)} />
            <Stat l="Taxes collected" v={rupees(r.taxesPaise)} />
            <Stat l="Fees (packaging/delivery)" v={rupees(r.feesPaise)} />
            <Stat l="Refunds" v={"−" + rupees(r.refundsPaise)} />
            <Stat l="Corrections" v={rupees(r.correctionsPaise)} />
            <Stat l="Payments received" v={rupees(r.paidReceiptsPaise)} />
            <Stat l="Net receipts" v={rupees(r.netReceiptsPaise)} />
          </div>
          <h2>Orders</h2>
          <div className="grid tight">
            <Stat l="Order volume" v={r.orderCount} />
            <Stat l="Completed" v={r.completedCount} />
            <Stat l="Average order value" v={rupees(r.averageOrderValuePaise)} />
            <Stat l="Cancelled" v={r.cancelledCount} />
            <Stat l="Rejected" v={r.rejectedCount} />
            <Stat l="Cancellation rate" v={r.cancellationRate + "%"} />
            <Stat l="Unique customers" v={r.customers.unique} />
            <Stat l="New customers" v={r.customers.new} />
            <Stat l="Returning customers" v={r.customers.returning} />
          </div>

          {(() => {
            const months = Object.values(r.byDay.reduce<Record<string, { month: string; orders: number; completed: number; grossPaise: number }>>((acc, d) => {
              const k = d.date.slice(0, 7); const x = acc[k] ?? (acc[k] = { month: k, orders: 0, completed: 0, grossPaise: 0 });
              x.orders += d.orders; x.completed += d.completed; x.grossPaise += d.grossPaise; return acc;
            }, {})).sort((a, b) => a.month.localeCompare(b.month));
            return months.length > 0 && (
              <>
                <h2>Monthly sales</h2>
                <table><thead><tr><th>Month</th><th className="right">Orders</th><th className="right">Completed</th><th className="right">Gross</th></tr></thead><tbody>
                  {months.map((m) => <tr key={m.month}><td>{new Date(m.month + "-01T00:00:00").toLocaleDateString("en-IN", { month: "long", year: "numeric" })}</td>
                    <td className="right">{m.orders}</td><td className="right">{m.completed}</td><td className="right">{rupees(m.grossPaise)}</td></tr>)}
                </tbody></table>
              </>
            );
          })()}
          <h2>Daily sales</h2>
          <table><tbody>
            {r.byDay.map((d) => (
              <tr key={d.date}>
                <td style={{ width: 110 }}>{d.date}</td>
                <td><div className="bar" style={{ width: `${(d.grossPaise / maxDay) * 100}%` }} /></td>
                <td className="right" style={{ width: 160 }}>{rupees(d.grossPaise)} · {d.completed}/{d.orders}</td>
              </tr>
            ))}
            {r.byDay.length === 0 && <tr><td className="muted">No orders in this period.</td></tr>}
          </tbody></table>

          <div className="grid" style={{ marginTop: 12 }}>
            <div>
              <h2>Item-wise sales</h2>
              <table><thead><tr><th>Item</th><th className="right">Qty</th><th className="right">Revenue</th></tr></thead><tbody>
                {r.byItem.map((i) => <tr key={i.itemId}><td>{i.name}</td><td className="right">{i.quantity}</td><td className="right">{rupees(i.revenuePaise)}</td></tr>)}
              </tbody></table>
            </div>
            <div>
              <h2>Category-wise revenue</h2>
              <table><tbody>
                {r.byCategory.map((c) => <tr key={c.category}><td>{c.category}</td><td className="right">{c.quantity}</td><td className="right">{rupees(c.revenuePaise)}</td></tr>)}
              </tbody></table>
              <h2>Payment methods</h2>
              <table><tbody>
                {r.byPaymentMethod.map((p) => <tr key={p.method}><td>{PAYMENT_LABEL[p.method] ?? p.method}</td><td className="right">{p.orders}</td><td className="right">{rupees(p.totalPaise)}</td></tr>)}
              </tbody></table>
              <h2>Order types</h2>
              <table><tbody>
                {r.byOrderType.map((p) => <tr key={p.type}><td>{ORDER_TYPE_LABEL[p.type]}</td><td className="right">{p.orders}</td></tr>)}
              </tbody></table>
            </div>
          </div>

          <h2>App funnel (anonymous events)</h2>
          <p className="small muted">Measured counts, not targets. Establish a baseline in the first month before setting goals.</p>
          <table><tbody>
            {FUNNEL.map((k) => (
              <tr key={k}>
                <td style={{ width: 200 }}>{k.replace(/_/g, " ")}</td>
                <td><div className="bar" style={{ width: `${((r.funnel?.[k] ?? 0) / maxFunnel) * 100}%`, background: "#9E3528" }} /></td>
                <td className="right" style={{ width: 80 }}>{r.funnel?.[k] ?? 0}</td>
              </tr>
            ))}
          </tbody></table>
        </>
      )}
    </div>
  );
}

function Stat({ l, v }: { l: string; v: string | number }) {
  return <div className="stat"><div className="v">{v}</div><div className="l">{l}</div></div>;
}
