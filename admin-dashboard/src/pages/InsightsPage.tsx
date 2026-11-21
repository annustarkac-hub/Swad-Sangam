import { useState } from "react";
import DateRange, { monthStart } from "../components/DateRange";
import { useRpc } from "../lib/erp";
import { rupees, todayIST } from "../lib/format";

interface Insights {
  hourly: { hour: number; orders: number; salesPaise: number }[];
  dishes: { name: string; qty: number; revenuePaise: number; estimatedCostPaise: number; estimatedProfitPaise: number }[];
  wastage: { name: string; qty: number; unit: string; valuePaise: number }[];
  supplierRates: { item: string; unit: string; supplier: string; avgRatePaise: number; lastPurchase: string }[];
  salesPaise: number; foodCostPaise: number; operatingExpensesPaise: number; foodCostPercent: number | null; breakEvenSalesPaise: number | null; note: string;
}

/** Answers: most profitable dishes, busiest hours, wastage, expensive suppliers, food cost, break-even. */
export default function InsightsPage() {
  const [from, setFrom] = useState(monthStart());
  const [to, setTo] = useState(todayIST());
  const { data, error } = useRpc<Insights>("business_insights", { p_from: from, p_to: to });
  const maxH = Math.max(1, ...(data?.hourly.map((h) => h.salesPaise) ?? [1]));
  return (
    <div>
      <h1>Business insights</h1>
      <DateRange from={from} to={to} onChange={(a, b) => { setFrom(a); setTo(b); }} />
      {error && <p className="error">{error}</p>}
      {data && (
        <>
          <div className="grid tight">
            <div className="stat"><div className="v">{rupees(data.salesPaise)}</div><div className="l">Food sales</div></div>
            <div className="stat"><div className="v">{data.foodCostPercent != null ? data.foodCostPercent + "%" : "—"}</div><div className="l">Actual food cost % (from recipes)</div></div>
            <div className="stat"><div className="v">{rupees(data.operatingExpensesPaise)}</div><div className="l">Operating expenses</div></div>
            <div className="stat"><div className="v">{data.breakEvenSalesPaise != null ? rupees(data.breakEvenSalesPaise) : "—"}</div><div className="l">Break-even sales for this period</div></div>
          </div>
          <p className="small muted">{data.note}</p>
          <h2>Busiest hours</h2>
          <table><tbody>{data.hourly.map((h) => <tr key={h.hour}><td style={{ width: 90 }}>{h.hour}:00–{h.hour + 1}:00</td>
            <td><div className="bar" style={{ width: `${(h.salesPaise / maxH) * 100}%` }} /></td><td className="right" style={{ width: 150 }}>{rupees(h.salesPaise)} · {h.orders}</td></tr>)}</tbody></table>
          <h2>Dish profitability (estimated)</h2>
          <table><thead><tr><th>Dish</th><th className="right">Sold</th><th className="right">Revenue</th><th className="right">Recipe cost</th><th className="right">Profit</th></tr></thead>
            <tbody>{data.dishes.map((d) => <tr key={d.name}><td>{d.name}</td><td className="right">{d.qty}</td><td className="right">{rupees(d.revenuePaise)}</td>
              <td className="right">{d.estimatedCostPaise ? rupees(d.estimatedCostPaise) : <span className="muted">add recipe</span>}</td><td className="right">{rupees(d.estimatedProfitPaise)}</td></tr>)}</tbody></table>
          <div className="grid" style={{ marginTop: 12 }}>
            <div><h2>Wastage</h2><table><tbody>{data.wastage.map((w) => <tr key={w.name}><td>{w.name}</td><td className="right">{w.qty} {w.unit}</td><td className="right">{rupees(w.valuePaise)}</td></tr>)}</tbody></table>
              {data.wastage.length === 0 && <p className="small muted">No wastage recorded.</p>}</div>
            <div><h2>Supplier rates</h2><table><tbody>{data.supplierRates.map((s, i) => <tr key={i}><td>{s.item}</td><td>{s.supplier}</td><td className="right">{rupees(s.avgRatePaise)}/{s.unit}</td></tr>)}</tbody></table>
              {data.supplierRates.length === 0 && <p className="small muted">Record purchases to compare suppliers.</p>}</div>
          </div>
        </>
      )}
    </div>
  );
}
