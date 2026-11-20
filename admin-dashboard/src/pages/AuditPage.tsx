import { useEffect, useState } from "react";
import { dateTime } from "../lib/format";
import { friendlyError, supabase } from "../lib/supabase";

interface Log { id: number; actor_email: string | null; action: string; entity: string; entity_id: string | null; old_value: Record<string, unknown> | null; new_value: Record<string, unknown> | null; created_at: string }

function diff(o: Record<string, unknown> | null, n: Record<string, unknown> | null): string {
  if (!o) return "created";
  if (!n) return "deleted";
  return Object.keys(n)
    .filter((k) => k !== "updated_at" && JSON.stringify(o[k]) !== JSON.stringify(n[k]))
    .map((k) => `${k}: ${JSON.stringify(o[k])} → ${JSON.stringify(n[k])}`)
    .join("; ");
}

export default function AuditPage() {
  const [rows, setRows] = useState<Log[]>([]);
  const [page, setPage] = useState(0);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => {
    supabase.from("audit_logs").select("*").order("created_at", { ascending: false }).range(page * 100, page * 100 + 99)
      .then(({ data, error }) => (error ? setErr(friendlyError(error.message)) : setRows(data as Log[])));
  }, [page]);
  return (
    <div>
      <h1>Audit log</h1>
      <p className="small muted">Every change to menu, prices, settings, staff, coupons, payments and refunds. Entries cannot be edited or deleted.</p>
      {err && <p className="error">{err}</p>}
      <div className="table-wrap"><table>
        <thead><tr><th>When</th><th>Who</th><th>Action</th><th>What</th><th>Change</th></tr></thead>
        <tbody>{rows.map((r) => (
          <tr key={r.id}><td className="small">{dateTime(r.created_at)}</td><td className="small">{r.actor_email ?? "system"}</td><td>{r.action}</td>
            <td className="small">{r.entity} {r.entity_id}</td><td className="small" style={{ wordBreak: "break-word" }}>{diff(r.old_value, r.new_value)}</td></tr>
        ))}</tbody>
      </table></div>
      <div className="row" style={{ marginTop: 8 }}>
        <button className="secondary small" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
        <button className="secondary small" disabled={rows.length < 100} onClick={() => setPage(page + 1)}>Older</button>
      </div>
    </div>
  );
}
