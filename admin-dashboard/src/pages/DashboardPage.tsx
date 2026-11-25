import { appBridge } from "../lib/native";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { can, useAuth } from "../auth";
import { requestNotifyPermission, unlockAudio } from "../lib/alert";
import { dateTime, minutesSince, rupees } from "../lib/format";
import { friendlyError, supabase } from "../lib/supabase";
import { Overview } from "./AccountingPage";

interface Summary {
  todayOrders: number; pending: number; accepted: number; preparing: number; ready: number;
  completed: number; cancelled: number; grossSalesPaise: number; collectedPaise: number;
  oldestPendingAt: string | null; bestSellers: { name: string; qty: number }[];
}
interface Status { isOpenNow: boolean; acceptingOrders: boolean; pickupEnabled: boolean; deliveryEnabled: boolean; dineInEnabled: boolean; temporarilyClosed: boolean }

export default function DashboardPage() {
  const { role } = useAuth();
  const [s, setS] = useState<Summary | null>(null);
  const [st, setSt] = useState<Status | null>(null);
  const [accepting, setAccepting] = useState<boolean | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [a, b, c] = await Promise.all([
      supabase.rpc("dashboard_summary"),
      supabase.rpc("restaurant_status"),
      supabase.from("restaurant_settings").select("accepting_orders").single(),
    ]);
    if (a.error) setErr(friendlyError(a.error.message)); else setS(a.data as Summary);
    if (!b.error) setSt(b.data as Status);
    if (!c.error) setAccepting(c.data.accepting_orders);
  }, []);

  useEffect(() => {
    void load();
    const t = setInterval(() => void load(), 20000);
    return () => clearInterval(t);
  }, [load]);

  async function toggleAccepting() {
    const { error } = await supabase.rpc("set_accepting_orders", { p_accepting: !accepting });
    if (error) setErr(friendlyError(error.message)); else void load();
  }

  const noTypes = st && !st.pickupEnabled && !st.dineInEnabled && !st.deliveryEnabled;

  return (
    <div>
      <div className="spread">
        <h1>Today</h1>
        {/* In the Android app notifications are native; the browser permission button would do nothing there */}
        {!appBridge() && (
          <button className="secondary small" onClick={() => { unlockAudio(); void requestNotifyPermission(); }}>
            Enable sound &amp; notifications
          </button>
        )}
      </div>
      {st && (
        <div className={"banner " + (st.acceptingOrders ? "ok" : "warn")}>
          <div className="spread">
            <div>
              <b>{st.acceptingOrders ? "App ordering is ON" : "App ordering is OFF"}</b>
              <div className="small">
                {st.temporarilyClosed ? "Restaurant marked temporarily closed. " : ""}
                {!st.isOpenNow ? "Outside opening hours. " : ""}
                {noTypes ? "No order type (pickup / dine-in / delivery) is enabled in Settings. " : ""}
                {!st.acceptingOrders && "Customers can still browse the menu and call to order."}
              </div>
            </div>
            {can(role, "OWNER", "MANAGER") && accepting != null && (
              <button className={accepting ? "danger" : "ok"} onClick={() => void toggleAccepting()}>
                {accepting ? "Pause app orders" : "Start accepting app orders"}
              </button>
            )}
          </div>
        </div>
      )}
      {s?.oldestPendingAt && minutesSince(s.oldestPendingAt) >= 2 && (
        <div className="banner danger">
          An order has been waiting since {dateTime(s.oldestPendingAt)}. <Link to="/orders">Open orders</Link>
        </div>
      )}
      {err && <p className="error">{err}</p>}
      {s && (
        <>
          <div className="grid tight">
            <Stat l="Today's orders" v={s.todayOrders} />
            <Stat l="New (pending)" v={s.pending} />
            <Stat l="Accepted" v={s.accepted} />
            <Stat l="Preparing" v={s.preparing} />
            <Stat l="Ready" v={s.ready} />
            <Stat l="Completed" v={s.completed} />
            <Stat l="Cancelled / rejected" v={s.cancelled} />
            <Stat l="Gross sales (completed)" v={rupees(s.grossSalesPaise)} />
            <Stat l="Payments collected" v={rupees(s.collectedPaise)} />
          </div>
          {can(role, "OWNER", "MANAGER") && <><h2>Business today</h2><Overview /></>}
          <h2>Best-selling dishes (30 days)</h2>
          {s.bestSellers.length === 0 ? (
            <p className="muted small">Shown once there are at least 20 completed orders in 30 days.</p>
          ) : (
            <ol>{s.bestSellers.map((b) => <li key={b.name}>{b.name} — {b.qty}</li>)}</ol>
          )}
        </>
      )}
    </div>
  );
}

function Stat({ l, v }: { l: string; v: string | number }) {
  return <div className="stat"><div className="v">{v}</div><div className="l">{l}</div></div>;
}
