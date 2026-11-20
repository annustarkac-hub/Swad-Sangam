import OrderCard from "../components/OrderCard";
import { useLiveOrders } from "../components/useOrders";

export default function KitchenPage() {
  const { orders, error, connected, reload } = useLiveOrders(["ACCEPTED", "PREPARING"], { kitchen: true });
  const toStart = orders.filter((o) => o.orderStatus === "ACCEPTED");
  const cooking = orders.filter((o) => o.orderStatus === "PREPARING");
  return (
    <div className="kitchen">
      <h1>Kitchen queue</h1>
      <p className="small muted">{connected ? "● Live" : "○ Reconnecting…"} · Oldest orders first.</p>
      {error && <p className="error">{error}</p>}
      <div className="board">
        <section className="column">
          <h3>To start ({toStart.length})</h3>
          {toStart.map((o) => <OrderCard key={o.id} order={o} onChanged={reload} kitchen />)}
        </section>
        <section className="column">
          <h3>Preparing ({cooking.length})</h3>
          {cooking.map((o) => <OrderCard key={o.id} order={o} onChanged={reload} kitchen />)}
        </section>
      </div>
    </div>
  );
}
