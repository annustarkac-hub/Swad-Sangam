import { useEffect, useState } from "react";
import { can, useAuth } from "../auth";
import DateRange, { monthStart } from "../components/DateRange";
import { downloadCsv } from "../lib/csv";
import { rpc, selectAll, TENDER_LABEL, type Tender } from "../lib/erp";
import { rupees, todayIST, toPaise } from "../lib/format";
import { toWebp } from "../lib/image";
import { supabase } from "../lib/supabase";

interface Account { id: number; code: string; name: string; type: string; is_active: boolean }
interface Expense { id: number; expense_no: string; expense_date: string; account_id: number; amount_paise: number; tender: Tender; vendor_name: string | null; notes: string | null; status: string; receipt_path: string | null }
interface Recurring { id: number; name: string; account_id: number; amount_paise: number; day_of_month: number; tender: Tender; is_active: boolean; last_posted_month: string | null }
interface Supplier { id: number; name: string }

/** Rent, electricity, gas, salaries-in-cash, repairs… with bill photo, approval and automatic journal entry. */
export default function ExpensesPage() {
  const { role } = useAuth();
  const approver = can(role, "OWNER", "MANAGER", "ACCOUNTANT");
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [rows, setRows] = useState<Expense[]>([]);
  const [recurring, setRecurring] = useState<Recurring[]>([]);
  const [from, setFrom] = useState(monthStart());
  const [to, setTo] = useState(todayIST());
  const [f, setF] = useState({ account: "5400", amount: "", tender: "CASH" as Tender, supplier: "", vendor: "", notes: "", date: todayIST() });
  const [file, setFile] = useState<File | null>(null);
  const [msg, setMsg] = useState<{ ok: boolean; t: string } | null>(null);

  const load = async () => {
    setAccounts((await selectAll<Account>("accounts", "code")).filter((a) => a.type === "EXPENSE" && a.is_active));
    const { data } = await supabase.from("expenses").select("*").gte("expense_date", from).lte("expense_date", to).order("expense_date", { ascending: false });
    setRows((data as Expense[]) ?? []);
    if (approver) {
      setRecurring(await selectAll<Recurring>("recurring_expenses", "day_of_month"));
      setSuppliers(await selectAll<Supplier>("suppliers", "name"));
    }
  };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load().catch((e) => setMsg({ ok: false, t: e.message })); }, [from, to]);
  const accName = (id: number) => accounts.find((a) => a.id === id)?.name ?? "";

  async function save(recurringId?: number, r?: Recurring) {
    try {
      let receiptPath: string | null = null;
      if (file && !r) {
        const blob = file.type === "application/pdf" ? file : await toWebp(file, 1600, 0.75);
        receiptPath = `${todayIST().slice(0, 7)}/${Date.now()}.${file.type === "application/pdf" ? "pdf" : "webp"}`;
        const { error } = await supabase.storage.from("receipts").upload(receiptPath, blob, { contentType: file.type === "application/pdf" ? "application/pdf" : "image/webp" });
        if (error) throw error;
      }
      const acc = r ? accounts.find((a) => a.id === r.account_id)?.code : f.account;
      const res = await rpc<{ status: string }>("record_expense", { p: r
        ? { accountCode: acc, amountPaise: r.amount_paise, tender: r.tender, notes: r.name, recurringId }
        : { accountCode: acc, amountPaise: toPaise(f.amount), tender: f.tender, supplierId: f.supplier ? Number(f.supplier) : null,
            vendorName: f.vendor || null, notes: f.notes || null, date: f.date, receiptPath } });
      setMsg({ ok: true, t: res.status === "APPROVED" ? "Expense saved and posted to accounts." : "Expense submitted for approval." });
      setF({ ...f, amount: "", vendor: "", notes: "" }); setFile(null); void load();
    } catch (e) { setMsg({ ok: false, t: (e as Error).message }); }
  }

  async function openReceipt(path: string) {
    const { data } = await supabase.storage.from("receipts").createSignedUrl(path, 300);
    if (data?.signedUrl) window.open(data.signedUrl, "_blank");
  }

  const total = rows.filter((r) => r.status === "APPROVED").reduce((s, r) => s + r.amount_paise, 0);
  const due = recurring.filter((r) => r.is_active && Number(todayIST().slice(8)) >= r.day_of_month && (!r.last_posted_month || r.last_posted_month < monthStart()));

  return (
    <div>
      <h1>Expenses</h1>
      {due.length > 0 && <div className="banner warn">Due this month: {due.map((r) => (
        <span key={r.id} style={{ marginRight: 12 }}>{r.name} {rupees(r.amount_paise)} <button className="small" onClick={() => void save(r.id, r)}>Record now</button></span>))}</div>}
      <div className="card">
        <h3>Add expense</h3>
        <div className="form-grid">
          <label>Type<select value={f.account} onChange={(e) => setF({ ...f, account: e.target.value })}>
            {accounts.map((a) => <option key={a.code} value={a.code}>{a.name}</option>)}</select></label>
          <label>Amount ₹<input value={f.amount} onChange={(e) => setF({ ...f, amount: e.target.value })} /></label>
          <label>Paid by<select value={f.tender} onChange={(e) => setF({ ...f, tender: e.target.value as Tender })}>
            {(["CASH", "UPI", "BANK", "CARD", ...(approver ? ["CREDIT"] : [])] as Tender[]).map((t) => <option key={t} value={t}>{t === "CREDIT" ? "Not paid yet (owe supplier)" : TENDER_LABEL[t]}</option>)}</select></label>
          {f.tender === "CREDIT" && <label>Supplier<select value={f.supplier} onChange={(e) => setF({ ...f, supplier: e.target.value })}>
            <option value="">Choose…</option>{suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label>}
          <label>Paid to / vendor<input value={f.vendor} onChange={(e) => setF({ ...f, vendor: e.target.value })} /></label>
          <label>Date<input type="date" value={f.date} onChange={(e) => setF({ ...f, date: e.target.value })} /></label>
          <label>Notes<input value={f.notes} onChange={(e) => setF({ ...f, notes: e.target.value })} /></label>
          <label>Bill photo / PDF<input type="file" accept="image/*,application/pdf" capture="environment" onChange={(e) => setFile(e.target.files?.[0] ?? null)} /></label>
        </div>
        <button disabled={!toPaise(f.amount)} onClick={() => void save()}>Save expense</button>
        {!approver && <span className="small muted"> — will need manager approval</span>}
        {msg && <p className={msg.ok ? "success" : "error"}>{msg.t}</p>}
      </div>

      <h2>Expense list</h2>
      <DateRange from={from} to={to} onChange={(a, b) => { setFrom(a); setTo(b); }} />
      <p>Approved total: <b>{rupees(total)}</b> <button className="secondary small" onClick={() => downloadCsv(`expenses-${from}-${to}.csv`,
        [["No", "Date", "Type", "Amount", "Paid by", "Vendor", "Notes", "Status"], ...rows.map((r) => [r.expense_no, r.expense_date, accName(r.account_id), r.amount_paise / 100, r.tender, r.vendor_name, r.notes, r.status])])}>Export</button></p>
      <div className="table-wrap"><table>
        <thead><tr><th>Date</th><th>No.</th><th>Type</th><th>Vendor / notes</th><th>Paid by</th><th className="right">Amount</th><th>Status</th><th /></tr></thead>
        <tbody>{rows.map((r) => (
          <tr key={r.id}><td>{r.expense_date}</td><td className="small">{r.expense_no}</td><td>{accName(r.account_id)}</td><td className="small">{r.vendor_name} {r.notes}</td>
            <td className="small">{r.tender}</td><td className="right">{rupees(r.amount_paise)}</td>
            <td>{r.status === "PENDING" ? <span className="badge PLACED">Pending</span> : r.status === "REJECTED" ? <span className="badge CANCELLED">Rejected</span> : "✓"}</td>
            <td className="row">
              {r.receipt_path && <button className="link" onClick={() => void openReceipt(r.receipt_path!)}>Bill</button>}
              {approver && r.status === "PENDING" && <>
                <button className="ok small" onClick={async () => { await rpc("decide_expense", { p_id: r.id, p_approve: true }); void load(); }}>Approve</button>
                <button className="danger small" onClick={async () => { await rpc("decide_expense", { p_id: r.id, p_approve: false }); void load(); }}>Reject</button></>}
            </td></tr>
        ))}</tbody>
      </table></div>

      {approver && <RecurringEditor accounts={accounts} rows={recurring} onChanged={() => void load()} />}
    </div>
  );
}

function RecurringEditor({ accounts, rows, onChanged }: { accounts: Account[]; rows: Recurring[]; onChanged: () => void }) {
  const [r, setR] = useState({ name: "", account: "5300", amount: "", day: "1", tender: "BANK" });
  const [err, setErr] = useState<string | null>(null);
  return (
    <>
      <h2>Recurring expenses (reminders)</h2>
      <p className="small muted">e.g. rent on the 5th, internet on the 10th. They appear as “due” on this page and the Overview; you confirm each month.</p>
      <table><tbody>{rows.map((x) => (
        <tr key={x.id}><td>{x.name}</td><td>{accounts.find((a) => a.id === x.account_id)?.name}</td><td className="right">{rupees(x.amount_paise)}</td>
          <td>day {x.day_of_month}</td><td>{x.last_posted_month ? `last: ${x.last_posted_month.slice(0, 7)}` : "never"}</td>
          <td><button className="link" onClick={async () => { await supabase.from("recurring_expenses").update({ is_active: !x.is_active }).eq("id", x.id); onChanged(); }}>{x.is_active ? "Pause" : "Resume"}</button></td></tr>
      ))}</tbody></table>
      <div className="row" style={{ marginTop: 8 }}>
        <input style={{ maxWidth: 160 }} placeholder="Name (Rent)" value={r.name} onChange={(e) => setR({ ...r, name: e.target.value })} />
        <select style={{ maxWidth: 180 }} value={r.account} onChange={(e) => setR({ ...r, account: e.target.value })}>{accounts.map((a) => <option key={a.code} value={a.code}>{a.name}</option>)}</select>
        <input style={{ maxWidth: 100 }} placeholder="₹" value={r.amount} onChange={(e) => setR({ ...r, amount: e.target.value })} />
        <input style={{ maxWidth: 70 }} type="number" min={1} max={28} value={r.day} onChange={(e) => setR({ ...r, day: e.target.value })} />
        <select style={{ maxWidth: 100 }} value={r.tender} onChange={(e) => setR({ ...r, tender: e.target.value })}>{["CASH", "UPI", "BANK"].map((t) => <option key={t}>{t}</option>)}</select>
        <button className="small" onClick={async () => {
          const acc = accounts.find((a) => a.code === r.account);
          const { error } = await supabase.from("recurring_expenses").insert({ name: r.name, account_id: acc?.id, amount_paise: toPaise(r.amount), day_of_month: Number(r.day), tender: r.tender });
          if (error) setErr(error.message); else { setR({ ...r, name: "", amount: "" }); onChanged(); }
        }}>Add</button>
      </div>
      {err && <p className="error">{err}</p>}
    </>
  );
}
