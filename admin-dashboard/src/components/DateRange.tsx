import { todayIST } from "../lib/format";

export function monthStart(): string {
  return todayIST().slice(0, 8) + "01";
}

export default function DateRange({ from, to, onChange }: { from: string; to: string; onChange: (from: string, to: string) => void }) {
  return (
    <div className="row" style={{ marginBottom: 12 }}>
      <button className="secondary small" onClick={() => onChange(todayIST(), todayIST())}>Today</button>
      <button className="secondary small" onClick={() => onChange(todayIST(-6), todayIST())}>7 days</button>
      <button className="secondary small" onClick={() => onChange(monthStart(), todayIST())}>This month</button>
      <button className="secondary small" onClick={() => {
        const y = Number(todayIST().slice(0, 4)); const m = Number(todayIST().slice(5, 7));
        const fy = m >= 4 ? y : y - 1; onChange(`${fy}-04-01`, todayIST());
      }}>This FY</button>
      <input type="date" style={{ maxWidth: 160 }} value={from} onChange={(e) => onChange(e.target.value, to)} />
      <span>to</span>
      <input type="date" style={{ maxWidth: 160 }} value={to} onChange={(e) => onChange(from, e.target.value)} />
    </div>
  );
}
