import { useEffect, useState } from "react";
import { can, useAuth } from "../auth";
import Modal from "../components/Modal";
import { dateTime } from "../lib/format";
import { friendlyError, supabase } from "../lib/supabase";
import type { Role, StaffProfile } from "../lib/types";

const ROLES: { r: Role; d: string }[] = [
  { r: "OWNER", d: "Full access" },
  { r: "MANAGER", d: "Menu, orders, reports" },
  { r: "STAFF", d: "View and update orders" },
  { r: "CASHIER", d: "POS billing, payments, cash register, orders" },
  { r: "WAITER", d: "Take table orders on POS (no payments)" },
  { r: "KITCHEN", d: "Preparation queue, stock wastage" },
  { r: "ACCOUNTANT", d: "Accounts, expenses, suppliers, khata, payroll, reports" },
];

export default function StaffPage() {
  const { role, reauthenticate } = useAuth();
  const owner = can(role, "OWNER");
  const [rows, setRows] = useState<StaffProfile[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [pending, setPending] = useState<{ s: StaffProfile; role: Role | null; active: boolean } | null>(null);
  const [pw, setPw] = useState("");

  async function load() {
    const { data, error } = await supabase.from("staff").select("*").order("created_at");
    if (error) setErr(friendlyError(error.message)); else setRows(data as StaffProfile[]);
  }
  useEffect(() => { void load(); }, []);

  async function apply() {
    if (!pending) return;
    if (!(await reauthenticate(pw))) return setErr("Password incorrect.");
    setPw("");
    const { error } = await supabase.rpc("set_staff_member", {
      p_user_id: pending.s.user_id, p_role: pending.role, p_active: pending.active, p_display_name: null,
    });
    setPending(null);
    if (error) setErr(friendlyError(error.message)); else { setErr(null); void load(); }
  }

  return (
    <div>
      <h1>Staff accounts</h1>
      <p className="small">
        New staff create an account on the sign-in page (“New staff? Create account”). They appear here as
        <b> inactive</b>. {owner ? "Choose a role and activate them." : "Only the owner can change roles."} Deactivate
        accounts immediately when someone leaves.
      </p>
      <ul className="small">{ROLES.map((x) => <li key={x.r}><b>{x.r}</b> — {x.d}</li>)}</ul>
      {err && <p className="error">{err}</p>}
      <div className="table-wrap"><table>
        <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Joined</th>{owner && <th />}</tr></thead>
        <tbody>
          {rows.map((s) => (
            <tr key={s.user_id}>
              <td>{s.display_name}</td><td>{s.email}</td><td>{s.role ?? "—"}</td>
              <td>{s.is_active ? <span className="badge READY">Active</span> : <span className="badge">Inactive</span>}</td>
              <td className="small">{dateTime(s.created_at)}</td>
              {owner && (
                <td>
                  <select style={{ maxWidth: 140 }} value={s.role ?? ""} onChange={(e) => setPending({ s, role: (e.target.value || null) as Role | null, active: e.target.value ? true : false })}>
                    <option value="">No role</option>
                    {ROLES.map((x) => <option key={x.r} value={x.r}>{x.r}</option>)}
                  </select>{" "}
                  {s.is_active
                    ? <button className="danger small" onClick={() => setPending({ s, role: s.role, active: false })}>Deactivate</button>
                    : s.role && <button className="ok small" onClick={() => setPending({ s, role: s.role, active: true })}>Activate</button>}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table></div>
      {pending && (
        <Modal title="Confirm staff change" onClose={() => setPending(null)}>
          <p>{pending.s.email} → role <b>{pending.role ?? "none"}</b>, {pending.active ? "active" : "inactive"}</p>
          <label>Your password<input type="password" autoFocus value={pw} onChange={(e) => setPw(e.target.value)} /></label>
          <button onClick={() => void apply()}>Confirm</button>
        </Modal>
      )}
    </div>
  );
}
