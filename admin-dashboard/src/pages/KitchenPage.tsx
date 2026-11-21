import { BellRing, Flame } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { can, useAuth } from "../auth";
import { printReceipt } from "../components/Receipt";
import { playAlert, unlockAudio } from "../lib/alert";
import { rpc, selectAll } from "../lib/erp";
import { supabase } from "../lib/supabase";
import { ORDER_TYPE_LABEL } from "../lib/types";

interface KItem { id: number; name: string; quantity: number; options: { name: string }[]; foodType: "VEG" | "NON_VEG"; prepStatus: string; stationId: number | null }
interface Ticket { id: string; orderNumber: string; orderType: string; tableNumber: string | null; orderStatus: string; priority: boolean; notes: string; source: string; acceptedAt: string; items: KItem[] }
interface Station { id: number; name: string; is_active: boolean }

const NEXT: Record<string, string> = { PENDING: "PREPARING", PREPARING: "READY", READY: "SERVED", SERVED: "PENDING" };

/** Kitchen display (KDS): station filter, live timers, tap an item to move it Pending → Preparing → Ready. */
export default function KitchenPage() {
  const { role } = useAuth();
  const [stations, setStations] = useState<Station[]>([]);
  const [station, setStation] = useState<number | null>(() => { const v = localStorage.getItem("ss_station"); return v ? Number(v) : null; });
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());
  const [sound, setSound] = useState(false);
  const known = useRef<Set<string> | null>(null);

  const load = useCallback(async () => {
    try {
      const list = await rpc<Ticket[]>("kds_board", { p_station_id: station });
      if (known.current && list.some((t) => !known.current!.has(t.id)) && sound) playAlert();
      known.current = new Set(list.map((t) => t.id));
      setTickets(list); setErr(null);
    } catch (e) { setErr((e as Error).message); }
  }, [station, sound]);

  useEffect(() => { selectAll<Station>("kitchen_stations", "sort_order").then((s) => setStations(s.filter((x) => x.is_active))).catch(() => undefined); }, []);
  useEffect(() => {
    void load();
    const poll = setInterval(() => void load(), 10000);
    const tick = setInterval(() => setNow(Date.now()), 1000);
    const ch = supabase.channel("kds").on("postgres_changes", { event: "*", schema: "public", table: "orders" }, () => void load()).subscribe();
    return () => { clearInterval(poll); clearInterval(tick); void supabase.removeChannel(ch); };
  }, [load]);

  async function bump(item: KItem) {
    setTickets((ts) => ts.map((t) => ({ ...t, items: t.items.map((i) => (i.id === item.id ? { ...i, prepStatus: NEXT[i.prepStatus] } : i)) })));
    try { await rpc("kds_set_item_status", { p_item_id: item.id, p_status: NEXT[item.prepStatus] }); } catch (e) { setErr((e as Error).message); }
    void load();
  }
  async function bumpAll(t: Ticket) {
    for (const i of t.items.filter((x) => x.prepStatus !== "READY" && x.prepStatus !== "SERVED")) {
      try { await rpc("kds_set_item_status", { p_item_id: i.id, p_status: "READY" }); } catch (e) { setErr((e as Error).message); }
    }
    void load();
  }

  const mins = (iso: string) => Math.floor((now - new Date(iso).getTime()) / 60000);
  const secs = (iso: string) => String(Math.floor(((now - new Date(iso).getTime()) / 1000) % 60)).padStart(2, "0");
  const active = tickets.filter((t) => t.items.some((i) => i.prepStatus === "PENDING" || i.prepStatus === "PREPARING"));
  const done = tickets.filter((t) => !active.includes(t));

  return (
    <div>
      <div className="spread">
        <div className="tabs">
          <button className={station == null ? "active" : ""} onClick={() => { setStation(null); localStorage.removeItem("ss_station"); }}>All stations</button>
          {stations.map((s) => <button key={s.id} className={station === s.id ? "active" : ""} onClick={() => { setStation(s.id); localStorage.setItem("ss_station", String(s.id)); }}>{s.name}</button>)}
        </div>
        <button className={sound ? "ok small" : "secondary small"} onClick={() => { unlockAudio(); setSound(!sound); }}><BellRing size={16} /> {sound ? "Sound on" : "Turn sound on"}</button>
      </div>
      {err && <div className="banner danger">{err}</div>}
      <p className="small muted">Tap an item: Pending → <b>Preparing</b> → <b>Ready</b>. When every item is ready the bill turns Ready for the counter. Red = waiting 15+ min.</p>
      {active.length === 0 && <div className="card muted">No tickets in the kitchen. 🎉</div>}
      <div className="kds">
        {active.map((t) => {
          const m = mins(t.acceptedAt);
          return (
            <article key={t.id} className={"ticket" + (t.priority ? " priority" : "") + (m >= 15 ? " late" : m >= 8 ? " warn" : "")}>
              <header>
                <div>
                  <b>{t.tableNumber ? `Table ${t.tableNumber}` : ORDER_TYPE_LABEL[t.orderType]}</b>
                  <div className="small muted">{t.orderNumber} · {t.source === "APP" ? "Online" : "POS"}</div>
                </div>
                <div style={{ textAlign: "right" }}>
                  {t.priority && <span className="pill" style={{ color: "var(--red)" }}><Flame size={12} /> Rush</span>}
                  <div className="timer">{m}:{secs(t.acceptedAt)}</div>
                </div>
              </header>
              {t.items.map((i) => (
                <div key={i.id} className={"ticket-item " + i.prepStatus} onClick={() => void bump(i)}>
                  <span className={i.foodType === "VEG" ? "veg" : "nonveg"} />
                  <span className="q">{i.quantity}×</span>
                  <span style={{ flex: 1 }}>{i.name}{i.options?.length ? <div className="small muted">{i.options.map((o) => o.name).join(", ")}</div> : null}</span>
                  <span className="pill">{i.prepStatus === "PENDING" ? "New" : i.prepStatus === "PREPARING" ? "Cooking" : "Ready"}</span>
                </div>
              ))}
              {t.notes && <div className="small" style={{ padding: ".5rem .8rem", background: "var(--amber-50)" }}>📝 {t.notes}</div>}
              <div className="row" style={{ padding: ".55rem .8rem" }}>
                <button className="ok small" onClick={() => void bumpAll(t)}>All ready</button>
                <button className="secondary small" onClick={() => void printReceipt(t.id, true)}>Reprint KOT</button>
                {can(role, "OWNER", "MANAGER", "STAFF") && (
                  <button className="secondary small" onClick={async () => { await rpc("set_order_priority", { p_order_id: t.id, p_priority: !t.priority }); void load(); }}>
                    {t.priority ? "Normal" : "Rush"}
                  </button>
                )}
              </div>
            </article>
          );
        })}
      </div>
      {done.length > 0 && (
        <>
          <h2>Ready for pickup / serving</h2>
          <div className="row">{done.map((t) => <span key={t.id} className="badge READY">{t.tableNumber ? `Table ${t.tableNumber}` : t.orderNumber}</span>)}</div>
        </>
      )}
    </div>
  );
}
