import { useEffect, useState } from "react";
import { printPage } from "../lib/native";
import { can, useAuth } from "../auth";
import DateRange, { monthStart } from "../components/DateRange";
import Modal from "../components/Modal";
import { downloadCsv } from "../lib/csv";
import { rpc, selectAll, useRpc } from "../lib/erp";
import { rupees, todayIST, toPaise } from "../lib/format";
import { supabase } from "../lib/supabase";
import { askText } from "../components/Dialogs";

interface Account { id: number; code: string; name: string; type: string; is_system: boolean; is_active: boolean }
interface Row { code: string; name: string; amountPaise: number }
const TABS = ["Overview", "Profit & Loss", "Balance sheet", "Trial balance", "Cash flow", "Ledger", "Journal", "GST", "Accounts"] as const;

export default function AccountingPage() {
  const [tab, setTab] = useState<(typeof TABS)[number]>("Overview");
  const [from, setFrom] = useState(monthStart());
  const [to, setTo] = useState(todayIST());
  return (
    <div>
      <div className="spread"><h1>Accounting</h1><button className="secondary small" onClick={() => printPage()}>Print / save PDF</button></div>
      <p className="small muted">Double-entry books. Every sale, payment, purchase, expense, salary and stock movement is posted automatically.
        Posted entries cannot be edited — use a reversal. Have your CA review before filing returns.</p>
      <div className="tabs">{TABS.map((t) => <button key={t} className={tab === t ? "active" : ""} onClick={() => setTab(t)}>{t}</button>)}</div>
      {!["Overview", "Balance sheet", "Trial balance", "Accounts"].includes(tab) && <DateRange from={from} to={to} onChange={(a, b) => { setFrom(a); setTo(b); }} />}
      {tab === "Overview" && <Overview />}
      {tab === "Profit & Loss" && <PnL from={from} to={to} />}
      {tab === "Balance sheet" && <BalanceSheet asOf={to} />}
      {tab === "Trial balance" && <TrialBalance asOf={to} />}
      {tab === "Cash flow" && <CashFlow from={from} to={to} />}
      {tab === "Ledger" && <Ledger from={from} to={to} />}
      {tab === "Journal" && <Journal from={from} to={to} />}
      {tab === "GST" && <Gst from={from} to={to} />}
      {tab === "Accounts" && <Accounts />}
    </div>
  );
}

function Stat({ l, v, hint }: { l: string; v: string | number; hint?: string }) {
  return <div className="stat" title={hint}><div className="v">{v}</div><div className="l">{l}</div></div>;
}

interface Overview {
  todaySalesPaise: number; todayOrders: number; averageOrderValuePaise: number; cashCollectedPaise: number; upiCollectedPaise: number;
  cardCollectedPaise: number; receivablesPaise: number; payablesPaise: number; todayExpensesPaise: number;
  estimatedGrossProfitPaise: number; foodCostPercent: number | null; topItems: { name: string; qty: number }[];
  lowStock: { name: string; qty: number; unit: string; minLevel: number }[]; pendingKots: number; pendingOrders: number;
  staffPresent: number; staffAbsent: number; pendingExpenses: number; recurringDue: { name: string; amountPaise: number }[]; note: string;
}

export function Overview() {
  const { data, error } = useRpc<Overview>("erp_overview", undefined);
  if (error) return <p className="error">{error}</p>;
  if (!data) return <p>Loading…</p>;
  return (
    <>
      <div className="grid tight">
        <Stat l="Today's food sales" v={rupees(data.todaySalesPaise)} />
        <Stat l="Orders today" v={data.todayOrders} />
        <Stat l="Average bill" v={rupees(data.averageOrderValuePaise)} />
        <Stat l="Cash collected" v={rupees(data.cashCollectedPaise)} />
        <Stat l="UPI collected" v={rupees(data.upiCollectedPaise)} />
        <Stat l="Card collected" v={rupees(data.cardCollectedPaise)} />
        <Stat l="Customers owe (khata)" v={rupees(data.receivablesPaise)} />
        <Stat l="We owe suppliers" v={rupees(data.payablesPaise)} />
        <Stat l="Expenses today" v={rupees(data.todayExpensesPaise)} />
        <Stat l="Estimated gross profit*" v={rupees(data.estimatedGrossProfitPaise)} hint={data.note} />
        <Stat l="Food cost %*" v={data.foodCostPercent != null ? data.foodCostPercent + "%" : "—"} />
        <Stat l="Kitchen queue (KOTs)" v={data.pendingKots} />
        <Stat l="New app orders" v={data.pendingOrders} />
        <Stat l="Staff present / absent" v={`${data.staffPresent} / ${data.staffAbsent}`} />
      </div>
      <p className="small muted">* {data.note}</p>
      <div className="grid">
        <div className="card"><h3>Top dishes (7 days)</h3><ol>{data.topItems.map((t) => <li key={t.name}>{t.name} — {t.qty}</li>)}</ol></div>
        <div className="card"><h3>Low stock</h3>{data.lowStock.length === 0 ? <p className="small muted">All good.</p> :
          <ul>{data.lowStock.map((s) => <li key={s.name} className="error">{s.name}: {s.qty} {s.unit} (min {s.minLevel})</li>)}</ul>}</div>
        <div className="card"><h3>To do</h3><ul>
          {data.pendingExpenses > 0 && <li>{data.pendingExpenses} expense(s) waiting for approval</li>}
          {data.recurringDue.map((r) => <li key={r.name}>Recurring due: {r.name} {rupees(r.amountPaise)}</li>)}
          {data.pendingExpenses === 0 && data.recurringDue.length === 0 && <li className="muted">Nothing pending.</li>}
        </ul></div>
      </div>
    </>
  );
}

function AmountTable({ rows, title }: { rows: Row[]; title: string }) {
  return (
    <table style={{ marginBottom: 12 }}>
      <thead><tr><th>{title}</th><th className="right">₹</th></tr></thead>
      <tbody>{rows.map((r) => <tr key={r.code + r.name}><td>{r.code} {r.name}</td><td className="right">{rupees(r.amountPaise)}</td></tr>)}</tbody>
    </table>
  );
}

function PnL({ from, to }: { from: string; to: string }) {
  const { data, error } = useRpc<{ income: Row[]; expenses: Row[]; totalIncomePaise: number; totalExpensePaise: number; grossProfitPaise: number; netProfitPaise: number }>(
    "profit_and_loss", { p_from: from, p_to: to });
  if (error) return <p className="error">{error}</p>;
  if (!data) return null;
  return (
    <div className="grid">
      <div>
        <AmountTable title="Income" rows={data.income} />
        <AmountTable title="Expenses" rows={data.expenses} />
        <button className="secondary small" onClick={() => downloadCsv(`pnl-${from}-${to}.csv`, [["Section", "Code", "Account", "Amount (INR)"],
          ...data.income.map((r) => ["Income", r.code, r.name, r.amountPaise / 100]), ...data.expenses.map((r) => ["Expense", r.code, r.name, r.amountPaise / 100])])}>Export CSV / Excel</button>
      </div>
      <div className="grid tight" style={{ alignContent: "start" }}>
        <Stat l="Total income" v={rupees(data.totalIncomePaise)} />
        <Stat l="Gross profit (after food cost)" v={rupees(data.grossProfitPaise)} />
        <Stat l="Total expenses" v={rupees(data.totalExpensePaise)} />
        <Stat l={data.netProfitPaise >= 0 ? "Net profit" : "Net loss"} v={rupees(Math.abs(data.netProfitPaise))} />
      </div>
    </div>
  );
}

function BalanceSheet({ asOf }: { asOf: string }) {
  const [d, setD] = useState(asOf);
  const { data, error } = useRpc<{ assets: Row[]; liabilities: Row[]; equity: Row[]; totalAssetsPaise: number; totalLiabilitiesPaise: number; totalEquityPaise: number; balanced: boolean }>(
    "balance_sheet", { p_as_of: d });
  return (
    <>
      <label style={{ maxWidth: 200 }}>As of<input type="date" value={d} onChange={(e) => setD(e.target.value)} /></label>
      {error && <p className="error">{error}</p>}
      {data && (
        <div className="grid">
          <div><AmountTable title="Assets" rows={data.assets} /><b>Total assets {rupees(data.totalAssetsPaise)}</b></div>
          <div>
            <AmountTable title="Liabilities" rows={data.liabilities} />
            <AmountTable title="Owner's equity" rows={data.equity} />
            <b>Total liabilities + equity {rupees(data.totalLiabilitiesPaise + data.totalEquityPaise)}</b>
            <p className={data.balanced ? "success" : "error"}>{data.balanced ? "✓ Balanced" : "Not balanced — contact support"}</p>
          </div>
        </div>
      )}
    </>
  );
}

function TrialBalance({ asOf }: { asOf: string }) {
  const [d, setD] = useState(asOf);
  const { data, error } = useRpc<{ rows: { code: string; name: string; debitPaise: number; creditPaise: number }[]; totalDebitPaise: number; totalCreditPaise: number }>(
    "trial_balance", { p_as_of: d });
  return (
    <>
      <label style={{ maxWidth: 200 }}>As of<input type="date" value={d} onChange={(e) => setD(e.target.value)} /></label>
      {error && <p className="error">{error}</p>}
      {data && (
        <>
          <table>
            <thead><tr><th>Account</th><th className="right">Debit</th><th className="right">Credit</th></tr></thead>
            <tbody>
              {data.rows.map((r) => <tr key={r.code}><td>{r.code} {r.name}</td><td className="right">{r.debitPaise ? rupees(r.debitPaise) : ""}</td><td className="right">{r.creditPaise ? rupees(r.creditPaise) : ""}</td></tr>)}
              <tr><th>Total</th><th className="right">{rupees(data.totalDebitPaise)}</th><th className="right">{rupees(data.totalCreditPaise)}</th></tr>
            </tbody>
          </table>
          <button className="secondary small" style={{ marginTop: 8 }} onClick={() => downloadCsv(`trial-balance-${d}.csv`, [["Code", "Account", "Debit", "Credit"], ...data.rows.map((r) => [r.code, r.name, r.debitPaise / 100, r.creditPaise / 100])])}>Export CSV / Excel</button>
        </>
      )}
    </>
  );
}

function CashFlow({ from, to }: { from: string; to: string }) {
  const { data, error } = useRpc<{ opening: Record<string, number>; closing: Record<string, number>; bySource: { source: string; inPaise: number; outPaise: number }[] }>(
    "cash_flow", { p_from: from, p_to: to });
  if (error) return <p className="error">{error}</p>;
  if (!data) return null;
  const sum = (o: Record<string, number>) => Object.values(o ?? {}).reduce((a, b) => a + b, 0);
  return (
    <>
      <p>Cash + bank + UPI — opening <b>{rupees(sum(data.opening))}</b> → closing <b>{rupees(sum(data.closing))}</b>
        <span className="small muted"> (cash {rupees(data.closing?.["1000"])}, bank {rupees(data.closing?.["1010"])}, UPI {rupees(data.closing?.["1020"])})</span></p>
      <table><thead><tr><th>Activity</th><th className="right">Money in</th><th className="right">Money out</th></tr></thead>
        <tbody>{data.bySource.map((s) => <tr key={s.source}><td>{s.source.replace(/_/g, " ").toLowerCase()}</td><td className="right">{rupees(s.inPaise)}</td><td className="right">{rupees(s.outPaise)}</td></tr>)}</tbody>
      </table>
    </>
  );
}

function Ledger({ from, to }: { from: string; to: string }) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [code, setCode] = useState("1000");
  useEffect(() => { selectAll<Account>("accounts", "code").then(setAccounts); }, []);
  const { data, error } = useRpc<{ openingPaise: number; closingPaise: number; lines: { date: string; entryNo: string; narration: string; debitPaise: number; creditPaise: number; balancePaise: number }[] }>(
    "account_ledger", { p_code: code, p_from: from, p_to: to });
  return (
    <>
      <label style={{ maxWidth: 360 }}>Account<select value={code} onChange={(e) => setCode(e.target.value)}>
        {accounts.map((a) => <option key={a.code} value={a.code}>{a.code} {a.name}</option>)}
      </select></label>
      {error && <p className="error">{error}</p>}
      {data && <LedgerTable data={data} file={`ledger-${code}-${from}-${to}.csv`} />}
    </>
  );
}

export function LedgerTable({ data, file }: { data: { openingPaise: number; closingPaise: number; lines: { date: string; entryNo: string; narration: string; debitPaise: number; creditPaise: number; balancePaise: number }[] }; file: string }) {
  const fmt = (b: number) => `${rupees(Math.abs(b))} ${b >= 0 ? "Dr" : "Cr"}`;
  return (
    <>
      <div className="table-wrap"><table>
        <thead><tr><th>Date</th><th>Voucher</th><th>Particulars</th><th className="right">Debit</th><th className="right">Credit</th><th className="right">Balance</th></tr></thead>
        <tbody>
          <tr><td colSpan={5}>Opening balance</td><td className="right">{fmt(data.openingPaise)}</td></tr>
          {data.lines.map((l, i) => <tr key={i}><td>{l.date}</td><td className="small">{l.entryNo}</td><td>{l.narration}</td>
            <td className="right">{l.debitPaise ? rupees(l.debitPaise) : ""}</td><td className="right">{l.creditPaise ? rupees(l.creditPaise) : ""}</td><td className="right">{fmt(l.balancePaise)}</td></tr>)}
          <tr><th colSpan={5}>Closing balance</th><th className="right">{fmt(data.closingPaise)}</th></tr>
        </tbody>
      </table></div>
      <button className="secondary small" style={{ marginTop: 8 }} onClick={() => downloadCsv(file, [["Date", "Voucher", "Particulars", "Debit", "Credit", "Balance"],
        ["", "", "Opening", "", "", data.openingPaise / 100], ...data.lines.map((l) => [l.date, l.entryNo, l.narration, l.debitPaise / 100, l.creditPaise / 100, l.balancePaise / 100])])}>Export CSV / Excel</button>
    </>
  );
}

interface Entry { id: number; entry_no: string; entry_date: string; narration: string; source: string; status: string }

function Journal({ from, to }: { from: string; to: string }) {
  const { role } = useAuth();
  const [rows, setRows] = useState<Entry[]>([]);
  const [open, setOpen] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const load = async () => {
    const { data, error } = await supabase.from("journal_entries").select("*").gte("entry_date", from).lte("entry_date", to).order("id", { ascending: false }).limit(300);
    if (error) setErr(error.message); else setRows(data as Entry[]);
  };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [from, to]);
  return (
    <>
      {can(role, "OWNER", "ACCOUNTANT") && <button onClick={() => setOpen(true)}>New journal entry (opening balances, capital, loans…)</button>}
      {err && <p className="error">{err}</p>}
      <div className="table-wrap" style={{ marginTop: 8 }}><table>
        <thead><tr><th>Date</th><th>No.</th><th>Narration</th><th>Type</th><th>Status</th><th /></tr></thead>
        <tbody>{rows.map((e) => (
          <tr key={e.id}><td>{e.entry_date}</td><td className="small">{e.entry_no}</td><td>{e.narration}</td><td className="small">{e.source}</td>
            <td>{e.status === "REVERSED" ? <span className="badge CANCELLED">Reversed</span> : "Posted"}</td>
            <td>{can(role, "OWNER", "ACCOUNTANT") && e.status === "POSTED" && e.source !== "REVERSAL" &&
              <button className="link" onClick={async () => {
                const reason = await askText("Reason for reversal?", { ok: "Reverse entry", placeholder: "e.g. wrong amount entered" });
                if (!reason) return;
                try { await rpc("reverse_journal", { p_entry_id: e.id, p_reason: reason }); void load(); } catch (x) { setErr((x as Error).message); }
              }}>Reverse</button>}</td></tr>
        ))}</tbody>
      </table></div>
      {open && <ManualJournal onClose={() => setOpen(false)} onSaved={() => { setOpen(false); void load(); }} />}
    </>
  );
}

function ManualJournal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [date, setDate] = useState(todayIST());
  const [narration, setNarration] = useState("");
  const [lines, setLines] = useState([{ code: "1000", dr: "", cr: "" }, { code: "3000", dr: "", cr: "" }]);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => { selectAll<Account>("accounts", "code").then((a) => setAccounts(a.filter((x) => x.is_active))); }, []);
  const dr = lines.reduce((s, l) => s + (toPaise(l.dr || "0") ?? 0), 0);
  const cr = lines.reduce((s, l) => s + (toPaise(l.cr || "0") ?? 0), 0);
  return (
    <Modal title="New journal entry" onClose={onClose}>
      <p className="small muted">Example — opening cash: Debit 1000 Cash, Credit 3000 Owner capital.</p>
      <div className="form-grid">
        <label>Date<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
        <label>Narration<input value={narration} onChange={(e) => setNarration(e.target.value)} /></label>
      </div>
      {lines.map((l, i) => (
        <div className="row" key={i} style={{ marginBottom: 6 }}>
          <select style={{ maxWidth: 280 }} value={l.code} onChange={(e) => setLines(lines.map((x, j) => (j === i ? { ...x, code: e.target.value } : x)))}>
            {accounts.map((a) => <option key={a.code} value={a.code}>{a.code} {a.name}</option>)}
          </select>
          <input style={{ maxWidth: 110 }} placeholder="Debit ₹" value={l.dr} onChange={(e) => setLines(lines.map((x, j) => (j === i ? { ...x, dr: e.target.value, cr: "" } : x)))} />
          <input style={{ maxWidth: 110 }} placeholder="Credit ₹" value={l.cr} onChange={(e) => setLines(lines.map((x, j) => (j === i ? { ...x, cr: e.target.value, dr: "" } : x)))} />
        </div>
      ))}
      <button className="secondary small" onClick={() => setLines([...lines, { code: "1000", dr: "", cr: "" }])}>+ Line</button>
      <p className={dr === cr && dr > 0 ? "success" : "error"}>Debit {rupees(dr)} · Credit {rupees(cr)} {dr === cr && dr > 0 ? "✓ balanced" : "— must be equal"}</p>
      {err && <p className="error">{err}</p>}
      <button disabled={dr !== cr || dr === 0} onClick={async () => {
        try {
          await rpc("create_manual_journal", { p: { date, narration, lines: lines.map((l) => ({ accountCode: l.code, debitPaise: toPaise(l.dr || "0"), creditPaise: toPaise(l.cr || "0") })) } });
          onSaved();
        } catch (e) { setErr((e as Error).message); }
      }}>Post entry</button>
    </Modal>
  );
}

function Gst({ from, to }: { from: string; to: string }) {
  const { data, error } = useRpc<{ outward: { ratePercent: number; invoices: number; taxablePaise: number; cgstPaise: number; sgstPaise: number; totalPaise: number }[];
    inward: { invoices: number; taxablePaise: number; taxPaise: number }; outputTaxLedgerPaise: number; inputTaxLedgerPaise: number }>("gst_report", { p_from: from, p_to: to });
  if (error) return <p className="error">{error}</p>;
  if (!data) return null;
  return (
    <>
      <div className="banner warn small">Summary to help your CA prepare GSTR-1 / GSTR-3B (or CMP-08 under composition). This app does not file returns.
        Tax rates and registration are set in Settings and must be verified by your accountant.</div>
      <h3>Outward supplies (sales)</h3>
      <table><thead><tr><th>Rate</th><th>Bills</th><th className="right">Taxable</th><th className="right">CGST</th><th className="right">SGST</th><th className="right">Total</th></tr></thead>
        <tbody>{data.outward.map((r) => <tr key={r.ratePercent}><td>{r.ratePercent}%</td><td>{r.invoices}</td><td className="right">{rupees(r.taxablePaise)}</td>
          <td className="right">{rupees(r.cgstPaise)}</td><td className="right">{rupees(r.sgstPaise)}</td><td className="right">{rupees(r.totalPaise)}</td></tr>)}</tbody></table>
      <h3>Inward supplies (purchases)</h3>
      <p>{data.inward.invoices} invoices · taxable {rupees(data.inward.taxablePaise)} · GST paid {rupees(data.inward.taxPaise)}</p>
      <p>Output tax in ledger: <b>{rupees(data.outputTaxLedgerPaise)}</b> · Input tax credit in ledger: <b>{rupees(data.inputTaxLedgerPaise)}</b></p>
      <button className="secondary small" onClick={() => downloadCsv(`gst-${from}-${to}.csv`, [["Rate %", "Bills", "Taxable", "CGST", "SGST", "Total"],
        ...data.outward.map((r) => [r.ratePercent, r.invoices, r.taxablePaise / 100, r.cgstPaise / 100, r.sgstPaise / 100, r.totalPaise / 100])])}>Export CSV / Excel</button>
    </>
  );
}

function Accounts() {
  const { role } = useAuth();
  const [rows, setRows] = useState<Account[]>([]);
  const [code, setCode] = useState(""); const [name, setName] = useState(""); const [type, setType] = useState("EXPENSE");
  const [err, setErr] = useState<string | null>(null);
  const load = () => selectAll<Account>("accounts", "code").then(setRows).catch((e) => setErr(e.message));
  useEffect(() => { void load(); }, []);
  return (
    <>
      {can(role, "OWNER", "ACCOUNTANT") && (
        <div className="row" style={{ marginBottom: 8 }}>
          <input style={{ maxWidth: 90 }} placeholder="Code" value={code} onChange={(e) => setCode(e.target.value)} />
          <input style={{ maxWidth: 240 }} placeholder="Account name" value={name} onChange={(e) => setName(e.target.value)} />
          <select style={{ maxWidth: 140 }} value={type} onChange={(e) => setType(e.target.value)}>
            {["ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE"].map((t) => <option key={t}>{t}</option>)}
          </select>
          <button className="small" onClick={async () => {
            const { error } = await supabase.from("accounts").insert({ code, name, type });
            if (error) setErr(error.message); else { setCode(""); setName(""); void load(); }
          }}>Add account</button>
        </div>
      )}
      {err && <p className="error">{err}</p>}
      <table><tbody>{rows.map((a) => <tr key={a.id}><td>{a.code}</td><td>{a.name}</td><td>{a.type}</td><td>{a.is_system ? "system" : a.is_active ? "active" : "inactive"}</td></tr>)}</tbody></table>
    </>
  );
}
