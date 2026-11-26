import { Gift, X } from "lucide-react";
import QRCode from "qrcode";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { playAlert } from "../lib/alert";
import { can, useAuth } from "../auth";
import Modal from "../components/Modal";
import { FoodDot } from "../components/OrderCard";
import { canTakePayment } from "../lib/orderFlow";
import { printReceipt } from "../components/Receipt";
import { rpc, selectAll, TENDER_LABEL, upiLink, uuid, type Tender } from "../lib/erp";
import { fromPaise, rupees, time, toPaise } from "../lib/format";
import { supabase } from "../lib/supabase";
import { ORDER_TYPE_LABEL, STATUS_LABEL, type Category, type MenuItem, type Order } from "../lib/types";

interface CartLine { key: string; item: MenuItem; qty: number; optionIds: string[]; complimentary: boolean }
interface Customer { id: number; name: string; phone: string | null; credit_limit_paise: number; loyalty_points: number; is_active: boolean }
interface Table { id: number; name: string; seats: number; status: string }
interface CashStatus { open: boolean; id?: number; openedAt?: string; openingPaise?: number; expectedPaise?: number; last?: { closed_at: string; difference_paise: number } }

/** Fast touchscreen POS: counter, dine-in, takeaway and delivery bills with split payments. */
export default function PosPage() {
  const { role } = useAuth();
  const manager = can(role, "OWNER", "MANAGER");
  const payer = canTakePayment(role); // waiters take orders but never money
  // One key per bill attempt: a retry after a network timeout re-sends the SAME key, so no duplicate bill/KOT
  const [billKey, setBillKey] = useState(uuid);
  const [items, setItems] = useState<MenuItem[]>([]);
  const [cats, setCats] = useState<Category[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [tables, setTables] = useState<Table[]>([]);
  const [openBills, setOpenBills] = useState<Order[]>([]);
  const [cat, setCat] = useState<string>("");
  const [q, setQ] = useState("");
  const [cart, setCart] = useState<CartLine[]>([]);
  const [type, setType] = useState<"DINE_IN" | "PICKUP" | "DELIVERY">("DINE_IN");
  const [table, setTable] = useState("");
  const [custName, setCustName] = useState("");
  const [custPhone, setCustPhone] = useState("");
  const [discount, setDiscount] = useState("");
  const [selected, setSelected] = useState<Order | null>(null);
  const [payOpen, setPayOpen] = useState(false);
  const [cash, setCash] = useState<CashStatus | null>(null);
  const [upiId, setUpiId] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [mobileTab, setMobileTab] = useState<"bills" | "menu" | "cart">("menu");

  const payerRef = useRef(payer);
  payerRef.current = payer;
  const loadBills = useCallback(async () => {
    try {
      const list = await rpc<Order[]>("admin_list_orders", { p_statuses: ["ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY"], p_limit: 100 });
      setOpenBills(list);
      setSelected((s) => {
        if (!s) return s;
        const still = list.find((o) => o.id === s.id);
        // Closed or paid on another device: say so instead of silently turning the cart into a new bill
        if (!still) setErr(`${s.tableNumber ? "Table " + s.tableNumber : s.orderNumber} was closed on another device. Items still in the cart will start a NEW bill.`);
        return still ?? null;
      });
      if (payerRef.current) setCash(await rpc<CashStatus>("cash_session_status"));
    } catch (e) { setErr((e as Error).message); }
  }, []);

  useEffect(() => {
    void (async () => {
      try {
        setItems((await selectAll<MenuItem>("menu_items", "sort_order")).filter((i) => !i.is_archived));
        setCats(await selectAll<Category>("categories", "sort_order"));
        setTables(await selectAll<Table>("restaurant_tables", "sort_order"));
        setCustomers((await selectAll<Customer>("customers", "name")).filter((c) => c));
        const { data } = await supabase.from("restaurant_settings").select("upi_id").single();
        setUpiId(data?.upi_id ?? null);
      } catch (e) { setErr((e as Error).message); }
      void loadBills();
    })();
    // Live: debounced reload on any order change (keeps the 20 s poll as a fallback for weak connections)
    let pending: ReturnType<typeof setTimeout> | null = null;
    const channel = supabase.channel("pos-bills-" + Math.random().toString(36).slice(2))
      .on("postgres_changes", { event: "*", schema: "public", table: "orders" }, () => {
        if (pending) clearTimeout(pending);
        pending = setTimeout(() => void loadBills(), 700);
      })
      .subscribe();
    const t = setInterval(() => void loadBills(), 20000);
    return () => { clearInterval(t); if (pending) clearTimeout(pending); void supabase.removeChannel(channel); };
  }, [loadBills]);

  // A search looks through ALL categories (the selected category only applies when the search box is empty)
  const shown = useMemo(() => items.filter((i) => (q.trim() ? i.name.toLowerCase().includes(q.trim().toLowerCase()) : !cat || i.category_id === cat)), [items, cat, q]);
  const inCart = useMemo(() => { const m = new Map<string, number>(); cart.forEach((l) => m.set(l.item.id, (m.get(l.item.id) ?? 0) + l.qty)); return m; }, [cart]);
  const cartTotal = cart.reduce((s, l) => s + (l.complimentary ? 0 : (l.item.price_paise + l.item.options.filter((o) => l.optionIds.includes(o.id)).reduce((a, o) => a + o.pricePaise, 0)) * l.qty), 0);

  function add(item: MenuItem, optionIds: string[] = []) {
    if (!item.is_available) return;
    const key = item.id + "|" + optionIds.sort().join(",");
    setCart((c) => {
      const ex = c.find((l) => l.key === key && !l.complimentary);
      return ex ? c.map((l) => (l === ex ? { ...l, qty: l.qty + 1 } : l)) : [...c, { key, item, qty: 1, optionIds, complimentary: false }];
    });
  }
  const payload = () => cart.map((l) => ({ itemId: l.item.id, quantity: l.qty, optionIds: l.optionIds, complimentary: l.complimentary }));

  async function createBill() {
    setBusy(true); setErr(null);
    try {
      const o = await rpc<Order>("pos_create_order", { p: {
        idempotencyKey: billKey, orderType: type, tableNumber: type === "DINE_IN" ? table || null : null,
        customerName: custName || "Walk-in", customerPhone: custPhone, items: payload(),
        discountPaise: discount ? toPaise(discount) : undefined,
      } });
      setCart([]); setDiscount(""); setCustName(""); setCustPhone(""); setBillKey(uuid());
      await loadBills(); setSelected(o);
      void printReceipt(o.id, true); // KOT for the kitchen
    } catch (e) { setErr((e as Error).message); } finally { setBusy(false); }
  }

  async function addToBill() {
    if (!selected) return;
    setBusy(true); setErr(null);
    try {
      const o = await rpc<Order>("pos_add_items", { p_order_id: selected.id, p_items: payload() });
      setCart([]); setSelected(o); await loadBills(); void printReceipt(o.id, true);
    } catch (e) { setErr((e as Error).message); } finally { setBusy(false); }
  }

  async function settle(o: Order) {
    try { await rpc("pos_settle", { p_order_id: o.id }); setSelected(null); await loadBills(); }
    catch (e) { setErr((e as Error).message); }
  }

  return (
    <div>
      {payer && <CashBar cash={cash} onChange={loadBills} />}
      {err && <div className="banner danger" role="alert">{err} <button className="ghost icon" aria-label="Dismiss" onClick={() => setErr(null)}><X size={18} aria-hidden /></button></div>}
      <ServiceRequests />
      <div className="tabs pos-tabs">
        {(["bills", "menu", "cart"] as const).map((t) => (
          <button key={t} className={mobileTab === t ? "active" : ""} onClick={() => setMobileTab(t)}>
            {t === "bills" ? `Bills (${openBills.length})` : t === "menu" ? "Menu" : `${selected ? "Bill" : "Cart"}${cart.length ? ` (${cart.reduce((s, l) => s + l.qty, 0)})` : ""}`}
          </button>
        ))}
      </div>
      <div className="pos">
        {/* Open bills (hold / resume) */}
        <section className={"column" + (mobileTab === "bills" ? " show" : "")}>
          <div className="spread"><h3>Open bills ({openBills.length})</h3><button className="small" onClick={() => setSelected(null)}>+ New</button></div>
          {openBills.map((o) => (
            <div key={o.id} className="order-card" style={{ cursor: "pointer", borderColor: selected?.id === o.id ? "var(--red)" : undefined }} onClick={() => { setSelected(o); setMobileTab("cart"); }}>
              <div className="spread"><b>{o.tableNumber ? `Table ${o.tableNumber}` : o.orderNumber} <span className={"src-badge " + (o.source === "APP" ? "app" : "pos")}>{o.source === "APP" ? "APP" : "POS"}</span></b><span className={"badge " + o.orderStatus}>{STATUS_LABEL[o.orderStatus]}</span></div>
              <div className="small muted">{ORDER_TYPE_LABEL[o.orderType]} · {time(o.createdAt)} · {o.customerName}</div>
              <div className="small"><b>{rupees(o.totalPaise)}</b> · {o.paymentStatus === "PAID" ? "Paid" : "Unpaid"}</div>
            </div>
          ))}
          <h3 style={{ marginTop: 12 }}>Tables</h3>
          <div className="row">
            {tables.map((t) => (
              <button key={t.id} className={"small " + (t.status === "FREE" ? "ok" : t.status === "OCCUPIED" ? "danger" : "secondary")}
                onClick={async () => {
                  if (t.status === "CLEANING") { await supabase.from("restaurant_tables").update({ status: "FREE" }).eq("id", t.id); setTables(await selectAll<Table>("restaurant_tables", "sort_order")); }
                  else { setSelected(null); setType("DINE_IN"); setTable(t.name); }
                }}
                title={t.status === "CLEANING" ? "Tap when cleaned" : t.status}>
                {t.name}{t.status === "CLEANING" ? " · clean" : ""}
              </button>
            ))}
            {tables.length === 0 && <span className="small muted">Add tables on the Tables page.</span>}
          </div>
        </section>

        {/* Menu */}
        <section className={mobileTab === "menu" ? "show" : ""}>
          <input placeholder="Search dish…" value={q} onChange={(e) => setQ(e.target.value)} style={{ marginBottom: 8 }} />
          <div className="tabs">
            <button className={cat === "" ? "active" : ""} onClick={() => setCat("")}>All</button>
            {cats.map((c) => <button key={c.id} className={cat === c.id ? "active" : ""} onClick={() => setCat(c.id)}>{c.name}</button>)}
          </div>
          <div className="grid tight">
            {shown.map((i) => (
              <button key={i.id} className="menu-tile" disabled={!i.is_available}
                onClick={() => add(i)} aria-label={`${i.name}${inCart.get(i.id) ? `, ${inCart.get(i.id)} in cart` : ""}`}>
                {inCart.get(i.id) ? <span className="tile-qty" aria-hidden>{inCart.get(i.id)}</span> : null}
                <span><FoodDot type={i.food_type} /> {i.name}</span><b>{rupees(i.price_paise)}{i.options.some((o) => o.group === "size") ? <span className="small muted"> · {i.options.filter((o) => o.group === "size").map((o) => `${o.name} ${rupees(i.price_paise + o.pricePaise)}`).join(" ")}</span> : null}</b>
                {!i.is_available && <span className="small"> · unavailable</span>}
              </button>
            ))}
          </div>
        </section>

        {/* Cart / bill */}
        <section className={"card sticky-panel" + (mobileTab === "cart" ? " show" : "")}>
          {selected ? (
            <BillPanel bill={selected} manager={manager} payer={payer} openBills={openBills} tables={tables} customers={customers}
              onPay={() => setPayOpen(true)} onSettle={() => void settle(selected)}
              onChanged={async (o) => { setSelected(o); await loadBills(); }} />
          ) : (
            <>
              <h3>New bill</h3>
              <div className="row">
                {(["DINE_IN", "PICKUP", "DELIVERY"] as const).map((t) => (
                  <button key={t} className={"small " + (type === t ? "" : "secondary")} onClick={() => setType(t)}>{t === "PICKUP" ? "Takeaway" : ORDER_TYPE_LABEL[t]}</button>
                ))}
              </div>
              {type === "DINE_IN" && (
                <label>Table<select value={table} onChange={(e) => setTable(e.target.value)}>
                  <option value="">Counter (no table)</option>
                  {tables.map((t) => <option key={t.id} value={t.name}>{t.name} ({t.status.toLowerCase()})</option>)}
                </select></label>
              )}
              <div className="form-grid">
                <label>Customer name<input value={custName} onChange={(e) => setCustName(e.target.value)} placeholder="Walk-in" /></label>
                <label>Phone<input value={custPhone} onChange={(e) => setCustPhone(e.target.value)} /></label>
              </div>
            </>
          )}
          <h3 style={{ marginTop: 8 }}>{selected ? "Add items" : "Items"}</h3>
          {cart.length === 0 && <p className="small muted">Tap dishes to add.</p>}
          {cart.map((l) => (
            <div key={l.key + l.complimentary} className="spread" style={{ padding: "4px 0", borderBottom: "1px solid var(--line)" }}>
              <div style={{ flex: 1 }}>
                {l.item.name}{l.optionIds.length > 0 && <span className="small muted"> ({l.item.options.filter((o) => l.optionIds.includes(o.id)).map((o) => o.name).join(", ")})</span>}
                {l.complimentary && <span className="badge">Free</span>}
                {[...new Set(l.item.options.filter((o) => o.group).map((o) => o.group!))].map((g) => {
                  const cur = l.optionIds.find((id) => l.item.options.find((o) => o.id === id)?.group === g) ?? "";
                  return (
                    <select key={g} className="small" style={{ width: "auto", marginLeft: 6, padding: 2, minHeight: 30 }} value={cur}
                      onChange={(e) => setCart((c) => c.map((x) => {
                        if (x !== l) return x;
                        const ids = [...x.optionIds.filter((id) => x.item.options.find((o) => o.id === id)?.group !== g), ...(e.target.value ? [e.target.value] : [])].sort();
                        return { ...x, optionIds: ids, key: x.item.id + "|" + ids.join(",") };
                      }))}>
                      <option value="">{l.item.variant_base_label ?? "Regular"}</option>
                      {l.item.options.filter((o) => o.group === g).map((o) => <option key={o.id} value={o.id}>{o.name} (+{rupees(o.pricePaise)})</option>)}
                    </select>
                  );
                })}
                {/* Add-ons (no group): any number can be ticked */}
                {l.item.options.filter((o) => !o.group && o.isAvailable !== false).map((o) => (
                  <label key={o.id} className="addon-chip">
                    <input type="checkbox" checked={l.optionIds.includes(o.id)}
                      onChange={(e) => setCart((c) => c.map((x) => {
                        if (x !== l) return x;
                        const ids = (e.target.checked ? [...x.optionIds, o.id] : x.optionIds.filter((id) => id !== o.id)).sort();
                        return { ...x, optionIds: ids, key: x.item.id + "|" + ids.join(",") };
                      }))} />
                    {o.name} +{rupees(o.pricePaise)}
                  </label>
                ))}
              </div>
              <div className="row">
                <button className="secondary small" onClick={() => setCart((c) => c.flatMap((x) => (x === l ? (x.qty > 1 ? [{ ...x, qty: x.qty - 1 }] : []) : [x])))}>−</button>
                <b>{l.qty}</b>
                <button className="secondary small" onClick={() => setCart((c) => c.map((x) => (x === l ? { ...x, qty: x.qty + 1 } : x)))}>+</button>
                {manager && <button className="ghost icon" title="Complimentary" aria-label={`Make ${l.item.name} complimentary`} aria-pressed={l.complimentary} onClick={() => setCart((c) => c.map((x) => (x === l ? { ...x, complimentary: !x.complimentary } : x)))}><Gift size={18} aria-hidden /></button>}
              </div>
            </div>
          ))}
          {cart.length > 0 && (
            <>
              <p><b>Items total: {rupees(cartTotal)}</b> <span className="small muted">(final total calculated by the server)</span></p>
              {!selected && manager && <label>Bill discount (₹, manager)<input value={discount} onChange={(e) => setDiscount(e.target.value)} /></label>}
              <div className="row">
                {selected
                  ? <button disabled={busy} onClick={() => void addToBill()}>Add to {selected.tableNumber ? "table " + selected.tableNumber : selected.orderNumber} + KOT</button>
                  : <button disabled={busy} onClick={() => void createBill()}>Create bill + print KOT</button>}
                <button className="secondary" onClick={() => { setCart([]); setBillKey(uuid()); }}>Clear</button>
              </div>
            </>
          )}
        </section>
      </div>
      {mobileTab === "menu" && cart.length > 0 && (
        <button className="pos-cart-bar" onClick={() => setMobileTab("cart")}>
          <span>{cart.reduce((s, l) => s + l.qty, 0)} items · <b className="num">{rupees(cartTotal)}</b></span><span>View {selected ? "bill" : "cart"} →</span>
        </button>
      )}
      {payOpen && selected && (
        <PaymentModal bill={selected} customers={customers} upiId={upiId} onClose={() => setPayOpen(false)}
          onPaid={async () => { setPayOpen(false); await loadBills(); }} />
      )}
    </div>
  );
}

function BillPanel({ bill, manager, payer, openBills, tables, customers, onPay, onSettle, onChanged }: {
  bill: Order; manager: boolean; payer: boolean; openBills: Order[]; tables: Table[]; customers: Customer[];
  onPay: () => void; onSettle: () => void; onChanged: (o: Order) => void;
}) {
  const [disc, setDisc] = useState(fromPaise(bill.discountPaise));
  const [err, setErr] = useState<string | null>(null);
  const [tool, setTool] = useState<"" | "split" | "merge" | "move" | "customer">("");
  const [lines, setLines] = useState<{ id: number; name_snapshot: string; quantity: number; line_total_paise: number }[]>([]);
  const [pick, setPick] = useState<number[]>([]);
  const [target, setTarget] = useState("");
  const [points, setPoints] = useState("");
  const paid = bill.paymentStatus === "PAID";
  const editable = !paid && bill.paymentStatus === "PENDING";
  // Match by id, never by name (two "Rahul"s must not share loyalty points)
  const cust = bill.customerId != null ? customers.find((c) => c.id === bill.customerId) : undefined;
  const appDelivery = bill.source === "APP" && bill.orderType === "DELIVERY";
  const run = async (fn: () => Promise<Order>) => { try { setErr(null); onChanged(await fn()); setTool(""); } catch (e) { setErr((e as Error).message); } };

  useEffect(() => {
    setDisc(fromPaise(bill.discountPaise)); setTool("");
    supabase.from("order_items").select("id,name_snapshot,quantity,line_total_paise").eq("order_id", bill.id).order("id")
      .then(({ data }) => setLines(data ?? []));
  }, [bill]);

  return (
    <div>
      <div className="spread"><h3 style={{ margin: 0 }}>{bill.tableNumber ? `Table ${bill.tableNumber}` : bill.orderNumber}</h3><span className={"badge " + bill.orderStatus}>{STATUS_LABEL[bill.orderStatus]}</span></div>
      <div className="small muted">{bill.orderNumber} · {ORDER_TYPE_LABEL[bill.orderType]} · {bill.customerName}</div>
      <table style={{ marginTop: 8 }}><tbody>
        {bill.items.map((i, k) => <tr key={k}><td>{i.quantity} × {i.nameSnapshot}</td><td className="right">{rupees(i.lineTotalPaise)}</td></tr>)}
        {bill.discountPaise > 0 && <tr><td>Discount</td><td className="right">−{rupees(bill.discountPaise)}</td></tr>}
        {bill.taxPaise > 0 && <tr><td>Tax</td><td className="right">{rupees(bill.taxPaise)}</td></tr>}
        {bill.packagingFeePaise > 0 && <tr><td>Packaging</td><td className="right">{rupees(bill.packagingFeePaise)}</td></tr>}
        <tr><th>Total</th><th className="right" style={{ fontSize: "1.05rem", color: "var(--text)" }}>{rupees(bill.totalPaise)}</th></tr>
      </tbody></table>

      <div className="row" style={{ marginTop: 10 }}>
        {payer && !paid && !appDelivery && <button className="ok" onClick={onPay}>Take payment</button>}
        {payer && paid && !appDelivery && <button className="ok" onClick={onSettle}>Close bill ✓</button>}
        {appDelivery && <span className="small muted">App delivery — rider collects payment with the customer's code (Orders page).</span>}
        {!payer && <span className="small muted">Payment is taken by the cashier.</span>}
        <button className="secondary small" onClick={() => void printReceipt(bill.id)}>Print bill</button>
        <button className="secondary small" onClick={() => void printReceipt(bill.id, true)}>KOT</button>
      </div>

      {editable && bill.orderType !== "DELIVERY" && (
        <div className="tabs" style={{ marginTop: 10, marginBottom: 6 }}>
          <button className={tool === "customer" ? "active" : ""} onClick={() => setTool(tool === "customer" ? "" : "customer")}>Customer</button>
          <button className={tool === "move" ? "active" : ""} onClick={() => setTool(tool === "move" ? "" : "move")}>Move table</button>
          <button className={tool === "split" ? "active" : ""} onClick={() => { setPick([]); setTool(tool === "split" ? "" : "split"); }}>Split</button>
          <button className={tool === "merge" ? "active" : ""} onClick={() => setTool(tool === "merge" ? "" : "merge")}>Merge</button>
        </div>
      )}
      {tool === "customer" && (
        <div className="card" style={{ padding: ".7rem" }}>
          <select value={target} onChange={(e) => setTarget(e.target.value)}>
            <option value="">Choose customer…</option>
            {customers.filter((c) => c.is_active).map((c) => <option key={c.id} value={c.id}>{c.name}{c.phone ? ` · ${c.phone}` : ""} · {c.loyalty_points} pts</option>)}
          </select>
          <button className="small" style={{ marginTop: 6 }} disabled={!target} onClick={() => void run(() => rpc<Order>("pos_set_customer", { p_order_id: bill.id, p_customer_id: Number(target) }))}>Attach to bill</button>
          {cust && cust.loyalty_points > 0 && (
            <div className="row" style={{ marginTop: 8 }}>
              <span className="small">{cust.loyalty_points} points available</span>
              <input style={{ maxWidth: 90 }} placeholder="Points" value={points} onChange={(e) => setPoints(e.target.value)} />
              <button className="small" onClick={() => void run(() => rpc<Order>("pos_redeem_points", { p_order_id: bill.id, p_points: Number(points) }))}>Redeem</button>
            </div>
          )}
          <p className="small muted" style={{ margin: "6px 0 0" }}>Add customers on the Customers & khata page. Points are earned when the bill closes.</p>
        </div>
      )}
      {tool === "move" && (
        <div className="row card" style={{ padding: ".7rem" }}>
          <select style={{ maxWidth: 200 }} value={target} onChange={(e) => setTarget(e.target.value)}>
            <option value="">Move to table…</option>
            {tables.filter((t) => t.name !== bill.tableNumber).map((t) => <option key={t.id} value={t.name}>{t.name} ({t.status.toLowerCase()})</option>)}
          </select>
          <button className="small" disabled={!target} onClick={() => void run(() => rpc<Order>("pos_transfer_table", { p_order_id: bill.id, p_table: target }))}>Move</button>
        </div>
      )}
      {tool === "split" && (
        <div className="card" style={{ padding: ".7rem" }}>
          <p className="small muted" style={{ marginTop: 0 }}>Tick the items to move to a new separate bill:</p>
          {lines.map((l) => (
            <label key={l.id} className="check" style={{ marginBottom: 4 }}>
              <input type="checkbox" checked={pick.includes(l.id)} onChange={(e) => setPick(e.target.checked ? [...pick, l.id] : pick.filter((x) => x !== l.id))} />
              {l.quantity} × {l.name_snapshot} — {rupees(l.line_total_paise)}
            </label>
          ))}
          <button className="small" disabled={!pick.length || pick.length === lines.length}
            onClick={() => void run(async () => (await rpc<{ original: Order }>("pos_split_bill", { p_order_id: bill.id, p_item_ids: pick })).original)}>Split into new bill</button>
        </div>
      )}
      {tool === "merge" && (
        <div className="row card" style={{ padding: ".7rem" }}>
          <select style={{ maxWidth: 240 }} value={target} onChange={(e) => setTarget(e.target.value)}>
            <option value="">Bring items from bill…</option>
            {openBills.filter((o) => o.id !== bill.id && o.paymentStatus === "PENDING").map((o) => (
              <option key={o.id} value={o.id}>{o.tableNumber ? `Table ${o.tableNumber}` : o.orderNumber} · {rupees(o.totalPaise)}</option>
            ))}
          </select>
          <button className="small" disabled={!target} onClick={() => void run(() => rpc<Order>("pos_merge_bills", { p_target: bill.id, p_source: target }))}>Merge into this bill</button>
        </div>
      )}
      {manager && editable && (
        <div className="row" style={{ marginTop: 8 }}>
          <input style={{ maxWidth: 110 }} value={disc} onChange={(e) => setDisc(e.target.value)} placeholder="Discount ₹" />
          <button className="secondary small" onClick={() => void run(() => rpc<Order>("pos_set_discount", { p_order_id: bill.id, p_discount_paise: toPaise(disc || "0") ?? 0, p_reason: "POS discount" }))}>Apply discount</button>
        </div>
      )}
      {err && <p className="error small">{err}</p>}
    </div>
  );
}

/** Waiter calls / bill requests from QR tables — shown on POS with a sound. */
function ServiceRequests() {
  const [rows, setRows] = useState<{ id: number; table_number: string; type: string; created_at: string }[]>([]);
  const count = useRef(0);
  const load = useCallback(async () => {
    const { data } = await supabase.from("service_requests").select("*").eq("status", "OPEN").order("created_at");
    const list = data ?? [];
    if (list.length > count.current) playAlert();
    count.current = list.length;
    setRows(list);
  }, []);
  useEffect(() => {
    void load();
    const t = setInterval(() => void load(), 15000);
    const ch = supabase.channel("svc").on("postgres_changes", { event: "*", schema: "public", table: "service_requests" }, () => void load()).subscribe();
    return () => { clearInterval(t); void supabase.removeChannel(ch); };
  }, [load]);
  if (!rows.length) return null;
  const label: Record<string, string> = { CALL_WAITER: "Calling waiter", REQUEST_BILL: "Wants the bill", WATER: "Needs water" };
  return (
    <div className="banner warn row">
      {rows.map((r) => (
        <span key={r.id} className="row" style={{ marginRight: 12 }}>
          <b>Table {r.table_number}</b> {label[r.type]} · {time(r.created_at)}
          <button className="small ok" onClick={async () => { await rpc("complete_service_request", { p_id: r.id }); void load(); }}>Done</button>
        </span>
      ))}
    </div>
  );
}

function PaymentModal({ bill, customers, upiId, onClose, onPaid }: { bill: Order; customers: Customer[]; upiId: string | null; onClose: () => void; onPaid: () => void }) {
  const [paidSoFar, setPaidSoFar] = useState(0);
  const [rows, setRows] = useState<{ tender: Tender; amount: string; reference: string; customerId: string }[]>([]);
  const [tendered, setTendered] = useState("");
  const [qr, setQr] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [clientRef] = useState(uuid());
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    supabase.from("order_payments").select("amount_paise").eq("order_id", bill.id).then(({ data }) => {
      const p = (data ?? []).reduce((s, r) => s + (r.amount_paise as number), 0);
      setPaidSoFar(p);
      setRows([{ tender: "CASH", amount: fromPaise(bill.totalPaise - p), reference: "", customerId: "" }]);
    });
  }, [bill]);

  const due = bill.totalPaise - paidSoFar;
  const entered = rows.reduce((s, r) => s + (toPaise(r.amount || "0") ?? 0), 0);
  const cashRow = rows.find((r) => r.tender === "CASH");
  const change = cashRow && tendered ? (toPaise(tendered) ?? 0) - (toPaise(cashRow.amount || "0") ?? 0) : 0;
  const upiRow = rows.find((r) => r.tender === "UPI");
  // QR for the total of ALL UPI rows (a split can have more than one)
  const upiTotal = rows.filter((r) => r.tender === "UPI").reduce((s, r) => s + (toPaise(r.amount || "0") ?? 0), 0);

  useEffect(() => {
    if (upiId && upiTotal > 0) void QRCode.toDataURL(upiLink(upiId, "Swad Sangam", upiTotal, bill.orderNumber), { width: 220, margin: 1 }).then(setQr);
    else setQr(null);
  }, [upiTotal, upiId, bill.orderNumber]);

  async function submit() {
    setBusy(true); setErr(null);
    try {
      await rpc("record_order_payment", { p_order_id: bill.id, p_client_ref: clientRef,
        p_payments: rows.filter((r) => (toPaise(r.amount || "0") ?? 0) > 0).map((r) => ({
          tender: r.tender, amountPaise: toPaise(r.amount), reference: r.reference || null, customerId: r.customerId ? Number(r.customerId) : null })) });
      onPaid();
    } catch (e) { setErr((e as Error).message); } finally { setBusy(false); }
  }

  return (
    <Modal title={`Payment — ${bill.orderNumber}`} onClose={onClose}>
      <p>Total {rupees(bill.totalPaise)} · Already paid {rupees(paidSoFar)} · <b>Due {rupees(due)}</b></p>
      {rows.map((r, i) => (
        <div key={i} className="row" style={{ marginBottom: 6 }}>
          <select style={{ maxWidth: 150 }} value={r.tender} onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, tender: e.target.value as Tender } : x)))}>
            {(["CASH", "UPI", "CARD", "CREDIT"] as Tender[]).map((t) => <option key={t} value={t}>{TENDER_LABEL[t]}</option>)}
          </select>
          <input style={{ maxWidth: 110 }} value={r.amount} onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, amount: e.target.value } : x)))} />
          {r.tender === "CREDIT" ? (
            <select style={{ maxWidth: 200 }} value={r.customerId} onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, customerId: e.target.value } : x)))}>
              <option value="">Choose khata customer…</option>
              {customers.filter((c) => c.credit_limit_paise > 0).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          ) : r.tender !== "CASH" && (
            <input style={{ maxWidth: 160 }} placeholder="UPI / card ref" value={r.reference} onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, reference: e.target.value } : x)))} />
          )}
          {rows.length > 1 && <button className="link" aria-label="Remove this payment row" onClick={() => setRows(rows.filter((_, j) => j !== i))}><X size={18} aria-hidden /></button>}
        </div>
      ))}
      <button className="secondary small" onClick={() => setRows([...rows, { tender: "UPI", amount: fromPaise(Math.max(0, due - entered)), reference: "", customerId: "" }])}>+ Split payment</button>
      {cashRow && (
        <div className="row" style={{ marginTop: 8 }}>
          <label style={{ margin: 0 }}>Cash given by customer<input style={{ maxWidth: 120 }} value={tendered} onChange={(e) => setTendered(e.target.value)} /></label>
          {change > 0 && <b className="success">Return change: {rupees(change)}</b>}
        </div>
      )}
      {qr && <div style={{ textAlign: "center" }}><img src={qr} alt="UPI QR" /><div className="small">Customer scans to pay. Check the money arrived in your UPI app <b>before</b> confirming.</div></div>}
      {upiRow && !upiId && <p className="small error">Add the restaurant UPI ID in Settings to show a payment QR.</p>}
      <p className="small">Entering {rupees(entered)} {entered < due ? `(partial — ${rupees(due - entered)} will remain due)` : ""}</p>
      {err && <p className="error">{err}</p>}
      <button disabled={busy || entered <= 0 || entered > due} onClick={() => void submit()}>Confirm payment received</button>
    </Modal>
  );
}

function CashBar({ cash, onChange }: { cash: CashStatus | null; onChange: () => void }) {
  const [amt, setAmt] = useState("");
  const [result, setResult] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  if (!cash) return null;
  return (
    <div className={"banner " + (cash.open ? "ok" : "warn")}>
      <div className="spread">
        <div>
          {cash.open
            ? <>Cash register open since {time(cash.openedAt)} · opening {rupees(cash.openingPaise)} · <b>expected in drawer {rupees(cash.expectedPaise)}</b></>
            : <>Cash register is closed. Open it with today's opening cash.</>}
          {result && <div className="small">{result}</div>}
          {err && <div className="small error">{err}</div>}
        </div>
        <div className="row">
          <input style={{ maxWidth: 120 }} placeholder={cash.open ? "Counted ₹" : "Opening ₹"} value={amt} onChange={(e) => setAmt(e.target.value)} />
          <button className="small" onClick={async () => {
            try {
              setErr(null);
              if (cash.open) {
                const r = await rpc<{ expectedPaise: number; countedPaise: number; differencePaise: number }>("close_cash_session", { p_counted_paise: toPaise(amt || "0") ?? 0, p_notes: null });
                setResult(`Closed. Expected ${rupees(r.expectedPaise)}, counted ${rupees(r.countedPaise)} → ${r.differencePaise === 0 ? "matched ✓" : r.differencePaise < 0 ? "SHORT " + rupees(-r.differencePaise) : "EXCESS " + rupees(r.differencePaise)}`);
              } else {
                await rpc("open_cash_session", { p_opening_paise: toPaise(amt || "0") ?? 0 });
              }
              setAmt(""); onChange();
            } catch (e) { setErr((e as Error).message); }
          }}>{cash.open ? "Close register" : "Open register"}</button>
        </div>
      </div>
    </div>
  );
}
