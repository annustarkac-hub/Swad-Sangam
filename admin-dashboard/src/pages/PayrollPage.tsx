import { useEffect, useState } from "react";
import { can, useAuth } from "../auth";
import Modal from "../components/Modal";
import { esc, printHtml, rpc, selectAll, type Tender } from "../lib/erp";
import { fromPaise, rupees, todayIST, toPaise } from "../lib/format";
import { supabase } from "../lib/supabase";

interface Employee { id: number; name: string; phone: string | null; designation: string; monthly_salary_paise: number; joined_on: string | null; weekly_off: number | null; is_active: boolean }
interface Att { employee_id: number; day: string; status: string; overtime_hours: number }
interface Preview { employeeId: number; name: string; designation: string; monthlyPaise: number; daysInMonth: number; payableDays: number; grossPaise: number;
  overtimePaise: number; bonusPaise: number; advanceOutstandingPaise: number; advanceRecoveredPaise: number; otherDeductionPaise: number; netPaise: number; alreadyPaid: boolean }

const STATUSES = ["PRESENT", "ABSENT", "HALF_DAY", "PAID_LEAVE", "LEAVE", "WEEK_OFF"];
const LABEL: Record<string, string> = { PRESENT: "P", ABSENT: "A", HALF_DAY: "½", PAID_LEAVE: "PL", LEAVE: "L", WEEK_OFF: "WO" };

/** Staff records, daily attendance, salary advances and monthly payroll with payslips. */
export default function PayrollPage() {
  const { role } = useAuth();
  const [tab, setTab] = useState<"Attendance" | "Employees" | "Payroll">("Attendance");
  return (
    <div>
      <h1>Employees & payroll</h1>
      <div className="tabs">{(["Attendance", "Employees", "Payroll"] as const).map((t) => <button key={t} className={tab === t ? "active" : ""} onClick={() => setTab(t)}>{t}</button>)}</div>
      {tab === "Attendance" && <Attendance />}
      {tab === "Employees" && <Employees editable={can(role, "OWNER", "ACCOUNTANT")} />}
      {tab === "Payroll" && <Payroll canRun={can(role, "OWNER", "ACCOUNTANT")} />}
    </div>
  );
}

function Attendance() {
  const [emps, setEmps] = useState<Employee[]>([]);
  const [month, setMonth] = useState(todayIST().slice(0, 7));
  const [att, setAtt] = useState<Att[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const days = new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0).getDate();
  const load = async () => {
    setEmps((await selectAll<Employee>("employees", "name")).filter((e) => e.is_active));
    const { data } = await supabase.from("attendance").select("*").gte("day", month + "-01").lte("day", `${month}-${days}`);
    setAtt((data as Att[]) ?? []);
  };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load().catch((e) => setErr(e.message)); }, [month]);
  const status = (e: number, d: string) => att.find((a) => a.employee_id === e && a.day === d)?.status;
  async function cycle(e: Employee, d: string) {
    const cur = status(e.id, d) ?? "PRESENT";
    const next = STATUSES[(STATUSES.indexOf(cur) + 1) % STATUSES.length];
    try { await rpc("mark_attendance", { p_employee_id: e.id, p_day: d, p_status: next }); void load(); } catch (x) { setErr((x as Error).message); }
  }
  return (
    <>
      <div className="row"><input type="month" style={{ maxWidth: 180 }} value={month} onChange={(e) => setMonth(e.target.value)} />
        <span className="small muted">Tap a cell to change: P present · A absent · ½ half day · PL paid leave · L unpaid leave · WO week off. Unmarked days count as present.</span></div>
      {err && <p className="error">{err}</p>}
      <div className="table-wrap"><table>
        <thead><tr><th>Name</th>{Array.from({ length: days }, (_, i) => <th key={i} className="small">{i + 1}</th>)}</tr></thead>
        <tbody>{emps.map((e) => (
          <tr key={e.id}><td>{e.name}</td>{Array.from({ length: days }, (_, i) => {
            const d = `${month}-${String(i + 1).padStart(2, "0")}`; const s = status(e.id, d);
            return <td key={i} onClick={() => d <= todayIST() && void cycle(e, d)} style={{ cursor: "pointer", textAlign: "center", padding: 2,
              background: s === "ABSENT" || s === "LEAVE" ? "#f8d3d0" : s === "HALF_DAY" ? "#fde2b8" : s ? "#d6f2df" : undefined }} className="small">{s ? LABEL[s] : "·"}</td>;
          })}</tr>
        ))}</tbody>
      </table></div>
    </>
  );
}

function Employees({ editable }: { editable: boolean }) {
  const [rows, setRows] = useState<Employee[]>([]);
  const [edit, setEdit] = useState<Employee | null>(null);
  const [adv, setAdv] = useState<Employee | null>(null);
  const load = () => selectAll<Employee>("employees", "name").then(setRows);
  useEffect(() => { void load(); }, []);
  return (
    <>
      {editable && <button onClick={() => setEdit({ id: 0, name: "", phone: "", designation: "Cook", monthly_salary_paise: 0, joined_on: todayIST(), weekly_off: null, is_active: true })}>Add employee</button>}
      <table style={{ marginTop: 8 }}><thead><tr><th>Name</th><th>Role</th><th>Phone</th><th className="right">Monthly salary</th><th>Joined</th><th /></tr></thead>
        <tbody>{rows.map((e) => <tr key={e.id} style={{ opacity: e.is_active ? 1 : 0.5 }}><td>{e.name}</td><td>{e.designation}</td><td>{e.phone}</td>
          <td className="right">{rupees(e.monthly_salary_paise)}</td><td>{e.joined_on}</td>
          <td>{editable && <><button className="link" onClick={() => setEdit(e)}>Edit</button><button className="link" onClick={() => setAdv(e)}>Give advance</button></>}</td></tr>)}</tbody></table>
      <p className="small muted">Employees here are for payroll. Dashboard logins are managed separately on the Staff page.</p>
      {edit && <EmpEditor emp={edit} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); void load(); }} />}
      {adv && <AdvanceDialog emp={adv} onClose={() => setAdv(null)} />}
    </>
  );
}

function EmpEditor({ emp, onClose, onSaved }: { emp: Employee; onClose: () => void; onSaved: () => void }) {
  const [e, setE] = useState(emp);
  const [sal, setSal] = useState(fromPaise(emp.monthly_salary_paise));
  const [err, setErr] = useState<string | null>(null);
  return (
    <Modal title={e.id ? `Edit ${e.name}` : "Add employee"} onClose={onClose}>
      <div className="form-grid">
        <label>Name<input value={e.name} onChange={(x) => setE({ ...e, name: x.target.value })} /></label>
        <label>Designation<select value={e.designation} onChange={(x) => setE({ ...e, designation: x.target.value })}>
          {["Cook", "Helper", "Waiter", "Cashier", "Manager", "Cleaner", "Delivery", "Accountant", "Other"].map((d) => <option key={d}>{d}</option>)}</select></label>
        <label>Phone<input value={e.phone ?? ""} onChange={(x) => setE({ ...e, phone: x.target.value })} /></label>
        <label>Monthly salary ₹<input value={sal} onChange={(x) => setSal(x.target.value)} /></label>
        <label>Joined on<input type="date" value={e.joined_on ?? ""} onChange={(x) => setE({ ...e, joined_on: x.target.value })} /></label>
      </div>
      <label className="check"><input type="checkbox" checked={e.is_active} onChange={(x) => setE({ ...e, is_active: x.target.checked })} /> Currently working</label>
      {err && <p className="error">{err}</p>}
      <button onClick={async () => {
        const row = { name: e.name, designation: e.designation, phone: e.phone || null, monthly_salary_paise: toPaise(sal || "0") ?? 0, joined_on: e.joined_on || null, is_active: e.is_active };
        const { error } = e.id ? await supabase.from("employees").update(row).eq("id", e.id) : await supabase.from("employees").insert(row);
        if (error) setErr(error.message); else onSaved();
      }}>Save</button>
    </Modal>
  );
}

function AdvanceDialog({ emp, onClose }: { emp: Employee; onClose: () => void }) {
  const [amt, setAmt] = useState(""); const [t, setT] = useState<Tender>("CASH"); const [note, setNote] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  return (
    <Modal title={`Salary advance — ${emp.name}`} onClose={onClose}>
      <p className="small muted">Advances are recovered automatically from the next payroll (you can change the recovery amount there).</p>
      <div className="form-grid">
        <label>Amount ₹<input value={amt} onChange={(e) => setAmt(e.target.value)} /></label>
        <label>Paid by<select value={t} onChange={(e) => setT(e.target.value as Tender)}>{["CASH", "UPI", "BANK"].map((x) => <option key={x}>{x}</option>)}</select></label>
        <label>Note<input value={note} onChange={(e) => setNote(e.target.value)} /></label>
      </div>
      {msg && <p>{msg}</p>}
      <button onClick={async () => {
        try { await rpc("give_salary_advance", { p_employee_id: emp.id, p_amount_paise: toPaise(amt), p_tender: t, p_note: note || null }); setMsg("Saved ✓"); }
        catch (e) { setMsg((e as Error).message); }
      }}>Save advance</button>
    </Modal>
  );
}

function Payroll({ canRun }: { canRun: boolean }) {
  const [month, setMonth] = useState(todayIST().slice(0, 7));
  const [rows, setRows] = useState<Preview[]>([]);
  const [adjust, setAdjust] = useState<Record<string, { bonusPaise?: number; otherDeductionPaise?: number; advanceRecoveryPaise?: number }>>({});
  const [tender, setTender] = useState<Tender>("BANK");
  const [msg, setMsg] = useState<string | null>(null);
  const load = async () => {
    try { setRows(await rpc<Preview[]>("payroll_preview", { p_month: month + "-01", p_adjust: adjust })); } catch (e) { setMsg((e as Error).message); }
  };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [month, JSON.stringify(adjust)]);
  const setAdj = (id: number, k: string, v: string) => setAdjust({ ...adjust, [id]: { ...adjust[id], [k]: v === "" ? undefined : toPaise(v) ?? 0 } });
  function payslip(p: Preview) {
    printHtml(`Payslip ${p.name}`, `<div class="doc"><h2>Swad Sangam — Payslip</h2><h3>${esc(p.name)} · ${esc(p.designation)} · ${month}</h3>
      <table><tr><td>Monthly salary</td><td class="r">${rupees(p.monthlyPaise)}</td></tr>
      <tr><td>Payable days</td><td class="r">${p.payableDays} / ${p.daysInMonth}</td></tr>
      <tr><td>Earned salary</td><td class="r">${rupees(p.grossPaise)}</td></tr><tr><td>Overtime</td><td class="r">${rupees(p.overtimePaise)}</td></tr>
      <tr><td>Bonus</td><td class="r">${rupees(p.bonusPaise)}</td></tr><tr><td>Advance recovered</td><td class="r">−${rupees(p.advanceRecoveredPaise)}</td></tr>
      <tr><td>Other deductions</td><td class="r">−${rupees(p.otherDeductionPaise)}</td></tr><tr><th>Net pay</th><th class="r">${rupees(p.netPaise)}</th></tr></table>
      <p><br/>Employee signature: ____________________ &nbsp; Employer: ____________________</p></div>`);
  }
  return (
    <>
      <div className="row"><input type="month" style={{ maxWidth: 180 }} value={month} onChange={(e) => setMonth(e.target.value)} /></div>
      <div className="table-wrap"><table>
        <thead><tr><th>Name</th><th className="right">Days</th><th className="right">Earned</th><th className="right">OT</th><th>Bonus ₹</th><th>Advance recovery ₹</th><th>Other deduction ₹</th><th className="right">Net</th><th /></tr></thead>
        <tbody>{rows.map((p) => (
          <tr key={p.employeeId}><td>{p.name}<div className="small muted">{p.designation}</div></td><td className="right">{p.payableDays}/{p.daysInMonth}</td>
            <td className="right">{rupees(p.grossPaise)}</td><td className="right">{rupees(p.overtimePaise)}</td>
            <td><input style={{ width: 80 }} disabled={p.alreadyPaid} defaultValue={fromPaise(p.bonusPaise) || ""} onBlur={(e) => setAdj(p.employeeId, "bonusPaise", e.target.value)} /></td>
            <td><input style={{ width: 90 }} disabled={p.alreadyPaid} placeholder={`of ${rupees(p.advanceOutstandingPaise)}`} onBlur={(e) => setAdj(p.employeeId, "advanceRecoveryPaise", e.target.value)} /></td>
            <td><input style={{ width: 80 }} disabled={p.alreadyPaid} onBlur={(e) => setAdj(p.employeeId, "otherDeductionPaise", e.target.value)} /></td>
            <td className="right"><b>{rupees(p.netPaise)}</b></td>
            <td>{p.alreadyPaid ? <span className="badge READY">Paid</span> : null} <button className="link" onClick={() => payslip(p)}>Payslip</button></td></tr>
        ))}</tbody>
      </table></div>
      {canRun && (
        <div className="row" style={{ marginTop: 8 }}>
          <select style={{ maxWidth: 120 }} value={tender} onChange={(e) => setTender(e.target.value as Tender)}>{["BANK", "UPI", "CASH"].map((t) => <option key={t}>{t}</option>)}</select>
          <button onClick={async () => {
            if (!confirm(`Pay salaries for ${month} by ${tender}? This posts to the accounts and cannot be edited.`)) return;
            try { const r = await rpc<{ employeesPaid: number; totalNetPaise: number }>("run_payroll", { p_month: month + "-01", p_tender: tender, p_adjust: adjust });
              setMsg(`Paid ${r.employeesPaid} employee(s), total ${rupees(r.totalNetPaise)}.`); void load(); }
            catch (e) { setMsg((e as Error).message); }
          }}>Run payroll for {month}</button>
        </div>
      )}
      {msg && <p>{msg}</p>}
    </>
  );
}
