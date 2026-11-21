import { useEffect, useState } from "react";
import { rpc, selectAll } from "../lib/erp";
import { dateTime, todayIST } from "../lib/format";
import { supabase } from "../lib/supabase";

interface Table { id: number; name: string; seats: number; status: string; sort_order: number; session_code: string | null }
interface Reservation { id: number; customer_name: string; phone: string | null; party_size: number; reserved_for: string; table_id: number | null; status: string; notes: string | null }

/** Floor tables (status, cleaning) and reservations / waiting list. */
export default function TablesPage() {
  const [tables, setTables] = useState<Table[]>([]);
  const [resv, setResv] = useState<Reservation[]>([]);
  const [t, setT] = useState({ name: "", seats: "4" });
  const [r, setR] = useState({ name: "", phone: "", size: "2", when: todayIST() + "T19:00", table: "", notes: "" });
  const [err, setErr] = useState<string | null>(null);
  const load = async () => {
    setTables(await selectAll<Table>("restaurant_tables", "sort_order"));
    const { data } = await supabase.from("reservations").select("*").gte("reserved_for", todayIST(-1)).order("reserved_for");
    setResv((data as Reservation[]) ?? []);
  };
  useEffect(() => { void load().catch((e) => setErr(e.message)); }, []);
  const setStatus = async (id: number, status: string, table = "restaurant_tables") => { await supabase.from(table).update({ status }).eq("id", id); void load(); };
  const color = (s: string) => ({ FREE: "#d6f2df", OCCUPIED: "#f8d3d0", CLEANING: "#fde2b8", RESERVED: "#dbe8ff" })[s];
  return (
    <div>
      <h1>Tables & reservations</h1>
      <p className="small muted">When guests sit down, tap <b>Seat guests</b> and tell them the 4-digit code. If “Require table code” is on in Settings,
        QR orders and waiter calls from that table need this code, so nobody can order for a table they are not sitting at.</p>
      {err && <p className="error">{err}</p>}
      <div className="grid tight">
        {tables.map((x) => (
          <div key={x.id} className="card" style={{ background: color(x.status) }}>
            <b>{x.name}</b> <span className="small">({x.seats} seats)</span><div className="small">{x.status}</div>
            <select className="small" value={x.status} onChange={(e) => void setStatus(x.id, e.target.value)}>{["FREE", "OCCUPIED", "CLEANING", "RESERVED"].map((s) => <option key={s}>{s}</option>)}</select>
            <div className="row" style={{ marginTop: 6 }}>
              {x.session_code
                ? <><span className="small">QR code: <b style={{ fontSize: "1.2rem", letterSpacing: 2 }}>{x.session_code}</b></span>
                    <button className="link small" onClick={async () => { await rpc("close_table_session", { p_table_id: x.id }); void load(); }}>End</button></>
                : <button className="secondary small" onClick={async () => { await rpc("open_table_session", { p_table_id: x.id }); void load(); }}>Seat guests · get code</button>}
            </div>
          </div>
        ))}
      </div>
      <div className="row" style={{ marginTop: 8 }}>
        <input style={{ maxWidth: 120 }} placeholder="Table name (T5)" value={t.name} onChange={(e) => setT({ ...t, name: e.target.value })} />
        <input style={{ maxWidth: 80 }} type="number" value={t.seats} onChange={(e) => setT({ ...t, seats: e.target.value })} />
        <button className="small" onClick={async () => {
          const { error } = await supabase.from("restaurant_tables").insert({ name: t.name, seats: Number(t.seats), sort_order: tables.length + 1 });
          if (error) setErr(error.message); else { setT({ name: "", seats: "4" }); void load(); }
        }}>Add table</button>
      </div>

      <h2>Reservations & waiting list</h2>
      <div className="row">
        <input style={{ maxWidth: 160 }} placeholder="Name" value={r.name} onChange={(e) => setR({ ...r, name: e.target.value })} />
        <input style={{ maxWidth: 130 }} placeholder="Phone" value={r.phone} onChange={(e) => setR({ ...r, phone: e.target.value })} />
        <input style={{ maxWidth: 70 }} type="number" value={r.size} onChange={(e) => setR({ ...r, size: e.target.value })} title="People" />
        <input style={{ maxWidth: 210 }} type="datetime-local" value={r.when} onChange={(e) => setR({ ...r, when: e.target.value })} />
        <select style={{ maxWidth: 120 }} value={r.table} onChange={(e) => setR({ ...r, table: e.target.value })}><option value="">Any table</option>{tables.map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}</select>
        <input style={{ maxWidth: 160 }} placeholder="Notes" value={r.notes} onChange={(e) => setR({ ...r, notes: e.target.value })} />
        <button className="small" onClick={async () => {
          const { error } = await supabase.from("reservations").insert({ customer_name: r.name, phone: r.phone || null, party_size: Number(r.size),
            reserved_for: new Date(r.when).toISOString(), table_id: r.table ? Number(r.table) : null, notes: r.notes || null });
          if (error) setErr(error.message); else { setR({ ...r, name: "", phone: "", notes: "" }); void load(); }
        }}>Book</button>
        <button className="secondary small" onClick={async () => {
          await supabase.from("reservations").insert({ customer_name: r.name || "Walk-in", phone: r.phone || null, party_size: Number(r.size), reserved_for: new Date().toISOString(), status: "WAITING" });
          void load();
        }}>Add to waiting list</button>
      </div>
      <table style={{ marginTop: 8 }}><tbody>{resv.map((x) => (
        <tr key={x.id}><td>{dateTime(x.reserved_for)}</td><td>{x.customer_name} {x.phone && <a href={`tel:${x.phone}`}>{x.phone}</a>}</td><td>{x.party_size} ppl</td>
          <td>{tables.find((tb) => tb.id === x.table_id)?.name ?? "—"}</td><td className="small">{x.notes}</td><td><span className="badge">{x.status}</span></td>
          <td className="row">{["SEATED", "COMPLETED", "NO_SHOW", "CANCELLED"].map((s) => <button key={s} className="link small" onClick={() => void setStatus(x.id, s, "reservations")}>{s.toLowerCase().replace("_", "-")}</button>)}</td></tr>
      ))}</tbody></table>
    </div>
  );
}
