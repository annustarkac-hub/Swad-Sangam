import { useCallback, useEffect, useRef, useState } from "react";
import { browserNotify, playAlert } from "../lib/alert";
import { supabase } from "../lib/supabase";
import type { Order, OrderStatus } from "../lib/types";

/**
 * Live list of orders with the given statuses. Uses Supabase Realtime (only while the
 * screen is open — PRD §9.4 cost control) plus a 30 s safety refresh in case the
 * websocket drops on a weak connection.
 */
export function useLiveOrders(statuses: OrderStatus[], opts: { alertOnNew?: boolean; kitchen?: boolean } = {}) {
  const [orders, setOrders] = useState<Order[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [connected, setConnected] = useState(false);
  const known = useRef<Set<string> | null>(null);
  const key = statuses.join(",");

  const load = useCallback(async () => {
    const res = opts.kitchen
      ? await supabase.rpc("kitchen_queue")
      : await supabase.rpc("admin_list_orders", { p_statuses: statuses, p_limit: 200 });
    if (res.error) {
      setError(res.error.message);
      return;
    }
    setError(null);
    const list = (res.data as Order[]) ?? [];
    if (opts.alertOnNew) {
      const fresh = list.filter((o) => o.orderStatus === "PLACED" && known.current && !known.current.has(o.id));
      if (fresh.length) {
        playAlert();
        browserNotify("New order", fresh.map((o) => `${o.orderNumber} — ${o.customerName}`).join("\n"));
      }
      known.current = new Set(list.map((o) => o.id));
    }
    setOrders(list);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, opts.kitchen, opts.alertOnNew]);

  useEffect(() => {
    void load();
    const channel = supabase
      .channel("orders-live-" + key)
      .on("postgres_changes", { event: "*", schema: "public", table: "orders" }, () => void load())
      .subscribe((status) => setConnected(status === "SUBSCRIBED"));
    // Kitchen role cannot receive order rows over Realtime (RLS hides customer data), so it polls faster.
    const timer = setInterval(() => void load(), opts.kitchen ? 15000 : 30000);
    return () => {
      clearInterval(timer);
      void supabase.removeChannel(channel);
    };
  }, [load, key]);

  return { orders, error, connected, reload: load };
}
