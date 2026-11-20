import { useEffect, useState } from "react";
import { dateTime } from "../lib/format";
import { friendlyError, supabase } from "../lib/supabase";

interface Feedback { id: number; order_id: string | null; food_quality: number | null; order_accuracy: number | null; packaging: number | null; staff_behaviour: number | null; overall: number; comments: string | null; consent_to_publish: boolean; created_at: string }

const stars = (n: number | null) => (n ? "★".repeat(n) + "☆".repeat(5 - n) : "—");

export default function FeedbackPage() {
  const [rows, setRows] = useState<Feedback[]>([]);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => {
    supabase.from("feedback").select("*").order("created_at", { ascending: false }).limit(200)
      .then(({ data, error }) => (error ? setErr(friendlyError(error.message)) : setRows(data as Feedback[])));
  }, []);
  const avg = (k: keyof Feedback) => {
    const v = rows.map((r) => r[k] as number | null).filter((x): x is number => x != null);
    return v.length ? (v.reduce((a, b) => a + b, 0) / v.length).toFixed(1) : "—";
  };
  return (
    <div>
      <h1>Customer feedback</h1>
      <p className="small muted">Private — visible only to owner and managers. Publish a comment only if the customer consented.</p>
      {err && <p className="error">{err}</p>}
      <div className="grid tight">
        {(["overall", "food_quality", "order_accuracy", "packaging", "staff_behaviour"] as const).map((k) => (
          <div className="stat" key={k}><div className="v">{avg(k)}</div><div className="l">{k.replace("_", " ")}</div></div>
        ))}
      </div>
      <div className="table-wrap" style={{ marginTop: 12 }}>
        <table>
          <thead><tr><th>Date</th><th>Overall</th><th>Food</th><th>Accuracy</th><th>Packaging</th><th>Staff</th><th>Comment</th><th>Consent</th></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td className="small">{dateTime(r.created_at)}</td><td>{stars(r.overall)}</td><td>{stars(r.food_quality)}</td>
                <td>{stars(r.order_accuracy)}</td><td>{stars(r.packaging)}</td><td>{stars(r.staff_behaviour)}</td>
                <td>{r.comments}</td><td>{r.consent_to_publish ? "Yes" : "No"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
