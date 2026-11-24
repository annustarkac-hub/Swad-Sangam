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

// Forward taps stop at READY (no accidental SERVED → PENDING loop); going back is the explicit Undo button.
const NEXT: Record<string, string | undefined> = { PENDING: "PREPARING", PREPARING: "READY" };
const PREV: Record<string, string | undefined> = { PREPARING: "PENDING", READY: "PREPARING" };
const SOUND_KEY = "ss_kds_sound";

/** Only this tiny component re-renders every second (was the whole board). */
function TicketTimer({ since }: { since: string }) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const t = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(t); }, []);
  const ms = now - new Date(since).getTime();
  return <div className="timer num">{Math.floor(ms / 60000)}:{String(Math.floor((ms / 1000) % 60)).padStart(2, "0")}</div>;
}
/** Minutes since acceptance, refreshed every 30 s (for the 8/15-minute colours). */
function useMinuteClock() {
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const t = setInterval(() => setNow(Date.now()), 30000); return () => clearInterval(t); }, []);
  return now;
}

/** Kitchen display (KDS): station filter, live timers, tap an item to move it Pending → Preparing → Ready. */
export default function KitchenPage() {
  const { role } = useAuth();
  const [stations, setStations] = useState<Station[]>([]);
  const [station, setStation] = useState<number | null>(() => { const v = localStorage.getItem("ss_station"); return v ? Number(v) : null; });
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const now = useMinuteClock();
  // Sound preference is remembered on this device; kept in a ref so toggling it never reconnects Realtime
  const [sound, setSound] = useState(() => { try { return localStorage.getItem(SOUND_KEY) === "1"; } catch { return false; } });
  const soundRef = useRef(sound);
  soundRef.current = sound;
  const known = useRef<Set<string> | null>(null);

  const load = useCallback(async () => {
    try {
      const list = await rpc<Ticket[]>("kds_board", { p_station_id: station });
      if (known.current && list.some((t) => !known.current!.has(t.id)) && soundRef.current) playAlert();
      known.current = new Set(list.map((t) => t.id));
      setTickets(list); setErr(null);
    } catch (e) { setErr((e as Error).message); }
  }, [station]);

  useEffect(() => { selectAll<Station>("kitchen_stations", "sort_order").then((s) => setStations(s.filter((x) => x.is_active))).catch(() => undefined); }, []);
  useEffect(() => {
    void load();
    const poll = setInterval(() => void load(), 10000);
    let pending: ReturnType<typeof setTimeout> | null = null;
    const ch = supabase.channel("kds-" + Math.random().toString(36).slice(2))
      .on("postgres_changes", { event: "*", schema: "public", table: "orders" }, () => { if (pending) clearTimeout(pending); pending = setTimeout(() => void load(), 500); })
      .subscribe();
    return () => { clearInterval(poll); if (pending) clearTimeout(pending); void supabase.removeChannel(ch); };
  }, [load]);

  async function setItem(item: KItem, to: string | undefined) {
    if (!to) return;
    setTickets((ts) => ts.map((t) => ({ ...t, items: t.items.map((i) => (i.id === item.id ? { ...i, prepStatus: to } : i)) })));
    try { await rpc("kds_set_item_status", { p_item_id: item.id, p_status: to }); } catch (e) { setErr((e as Error).message); }
    void load();
  }
  async function bumpAll(t: Ticket) {
    // All items in parallel (was one network round-trip after another)
    const results = await Promise.allSettled(t.items.filter((x) => x.prepStatus === "PENDING" || x.prepStatus === "PREPARING")
      .map((i) => rpc("kds_set_item_status", { p_item_id: i.id, p_status: "READY" })));
    const failed = results.find((r) => r.status === "rejected") as PromiseRejectedResult | undefined;
    if (failed) setErr((failed.reason as Error).message);
    void load();
  }
  function toggleSound() {
    unlockAudio();
    const v = !sound;
    setSound(v);
    try { localStorage.setItem(SOUND_KEY, v ? "1" : "0"); } catch { /* private mode */ }
  }

  const mins = (iso: string) => Math.floor((now - new Date(iso).getTime()) / 60000);
  const active = tickets.filter((t) => t.items.some((i) => i.prepStatus === "PENDING" || i.prepStatus === "PREPARING"));
  const done = tickets.filter((t) => !active.includes(t));

  return (
    <div>
      <div className="spread">
        <div className="tabs">
          <button className={station == null ? "active" : ""} onClick={() => { setStation(null); localStorage.removeItem("ss_station"); }}>All stations</button>
          {stations.map((s) => <button key={s.id} className={station === s.id ? "active" : ""} onClick={() => { setStation(s.id); localStorage.setItem("ss_station", String(s.id)); }}>{s.name}</button>)}
        </div>
        <button className={sound ? "ok small" : "secondary small"} aria-pressed={sound} onClick={toggleSound}><BellRing size={16} /> {sound ? "Sound on" : "Turn sound on"}</button>
      </div>
      {!sound && <div className="banner warn small" role="status">🔇 Sound is off — new tickets will not beep. <button className="link" onClick={toggleSound}>Turn on</button></div>}
      {err && <div className="banner danger">{err}</div>}
      <p className="small muted">Tap an item: New → <b>Cooking</b> → <b>Ready</b> (↶ undoes a tap). When every item is ready the bill turns Ready for the counter. Red = waiting 15+ min.</p>
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
                  <TicketTimer since={t.acceptedAt} />
                </div>
              </header>
              {t.items.map((i) => (
                <div key={i.id} className={"ticket-item " + i.prepStatus}>
                  <button type="button" className="ticket-tap" disabled={!NEXT[i.prepStatus]} onClick={() => void setItem(i, NEXT[i.prepStatus])}
                    aria-label={`${i.quantity} ${i.name}: ${i.prepStatus === "PENDING" ? "new, tap to start cooking" : i.prepStatus === "PREPARING" ? "cooking, tap when ready" : "ready"}`}>
                    <span className={i.foodType === "VEG" ? "veg" : "nonveg"} />
                    <span className="q">{i.quantity}×</span>
                    <span style={{ flex: 1, textAlign: "left" }}>{i.name}{i.options?.length ? <div className="small muted">{i.options.map((o) => o.name).join(", ")}</div> : null}</span>
                    <span className="pill">{i.prepStatus === "PENDING" ? "Tap to cook" : i.prepStatus === "PREPARING" ? "Tap ready" : "Ready ✓"}</span>
                  </button>
                  {PREV[i.prepStatus] && <button type="button" className="link undo" aria-label={`Undo ${i.name}`} title="Undo" onClick={() => void setItem(i, PREV[i.prepStatus])}>↶</button>}
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
