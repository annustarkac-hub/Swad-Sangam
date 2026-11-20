import { useEffect, useState } from "react";
import { can, useAuth } from "../auth";
import Modal from "../components/Modal";
import { downloadCsv } from "../lib/csv";
import { rpc, selectAll } from "../lib/erp";
import { dateTime, rupees, todayIST, toPaise } from "../lib/format";
import { supabase } from "../lib/supabase";
import type { MenuItem } from "../lib/types";

interface StockItem { id: number; name: string; category: string; unit: string; min_level: number; qty: number; avg_cost: number; is_active: boolean }
interface Movement { id: number; item_id: number; type: string; qty: number; value_paise: number; note: string | null; ref_type: string | null; batch_no: string | null; expiry_date: string | null; created_at: string }
interface RecipeLine { id: number; menu_item_id: string; stock_item_id: number; qty: number; unit: string }
interface Costing { itemId: string; name: string; pricePaise: number; hasRecipe: boolean; recipeCostPaise: number; foodCostPercent: number | null; marginPaise: number }
interface Supplier { id: number; name: string }

const UNITS = ["kg", "g", "l", "ml", "pc"];
const TABS = ["Stock", "Recipes & food cost", "Purchase entry", "Purchase history", "Movements"] as const;

export default function InventoryPage() {
  const [tab, setTab] = useState<(typeof TABS)[number]>("Stock");
  return (
    <div>
      <h1>Inventory & purchases</h1>
      <div className="tabs">{TABS.map((t) => <button key={t} className={tab === t ? "active" : ""} onClick={() => setTab(t)}>{t}</button>)}</div>
      {tab === "Stock" && <Stock />}
      {tab === "Recipes & food cost" && <Recipes />}
      {tab === "Purchase entry" && <PurchaseEntry />}
      {tab === "Purchase history" && <PurchaseHistory />}
      {tab === "Movements" && <Movements />}
    </div>
  );
}

function Stock() {
  const { role } = useAuth();
  const [items, setItems] = useState<StockItem[]>([]);
  const [edit, setEdit] = useState<StockItem | null>(null);
  const [adjust, setAdjust] = useState<{ item: StockItem; type: "OPENING" | "WASTAGE" | "COUNT" } | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const load = () => selectAll<StockItem>("stock_items", "name").then(setItems).catch((e) => setErr(e.message));
  useEffect(() => { void load(); }, []);
  const value = items.reduce((s, i) => s + Math.round(Math.max(i.qty, 0) * i.avg_cost), 0);
  return (
    <>
      <div className="spread">
        <p>Stock value (weighted average): <b>{rupees(value)}</b></p>
        <div className="row">
          {can(role, "OWNER", "MANAGER") && <button onClick={() => setEdit({ id: 0, name: "", category: "INGREDIENT", unit: "kg", min_level: 0, qty: 0, avg_cost: 0, is_active: true })}>Add stock item</button>}
          <button className="secondary small" onClick={() => downloadCsv(`stock-${todayIST()}.csv`, [["Item", "Category", "Qty", "Unit", "Avg cost/unit", "Value", "Min level"],
            ...items.map((i) => [i.name, i.category, i.qty, i.unit, (i.avg_cost / 100).toFixed(2), (i.qty * i.avg_cost / 100).toFixed(2), i.min_level])])}>Export</button>
        </div>
      </div>
      {err && <p className="error">{err}</p>}
      <div className="table-wrap"><table>
        <thead><tr><th>Item</th><th>Category</th><th className="right">In stock</th><th className="right">Min</th><th className="right">Avg cost</th><th className="right">Value</th><th /></tr></thead>
        <tbody>{items.map((i) => (
          <tr key={i.id} style={{ opacity: i.is_active ? 1 : 0.5 }}>
            <td>{i.name}</td><td className="small">{i.category}</td>
            <td className={"right" + (i.min_level > 0 && i.qty <= i.min_level ? " error" : "")}><b>{Number(i.qty)} {i.unit}</b>{i.min_level > 0 && i.qty <= i.min_level ? " ⚠ low" : ""}</td>
            <td className="right">{Number(i.min_level)}</td><td className="right">{rupees(Math.round(i.avg_cost))}/{i.unit}</td>
            <td className="right">{rupees(Math.round(i.qty * i.avg_cost))}</td>
            <td className="row">
              {can(role, "OWNER", "MANAGER", "ACCOUNTANT") && <button className="link" onClick={() => setAdjust({ item: i, type: "OPENING" })}>Opening</button>}
              <button className="link" onClick={() => setAdjust({ item: i, type: "WASTAGE" })}>Wastage</button>
              <button className="link" onClick={() => setAdjust({ item: i, type: "COUNT" })}>Physical count</button>
              {can(role, "OWNER", "MANAGER") && <button className="link" onClick={() => setEdit(i)}>Edit</button>}
            </td>
          </tr>
        ))}</tbody>
      </table></div>
      {edit && <StockEditor item={edit} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); void load(); }} />}
      {adjust && <AdjustDialog {...adjust} onClose={() => setAdjust(null)} onSaved={() => { setAdjust(null); void load(); }} />}
    </>
  );
}

function StockEditor({ item, onClose, onSaved }: { item: StockItem; onClose: () => void; onSaved: () => void }) {
  const [s, setS] = useState(item);
  const [err, setErr] = useState<string | null>(null);
  async function save() {
    const row = { name: s.name, category: s.category, unit: s.unit, min_level: s.min_level, is_active: s.is_active };
    const { error } = s.id ? await supabase.from("stock_items").update(row).eq("id", s.id) : await supabase.from("stock_items").insert(row);
    if (error) setErr(error.message); else onSaved();
  }
  return (
    <Modal title={s.id ? `Edit ${s.name}` : "Add stock item"} onClose={onClose}>
      <label>Name<input value={s.name} onChange={(e) => setS({ ...s, name: e.target.value })} placeholder="e.g. Atta, Chicken, Paneer, Parcel box" /></label>
      <div className="form-grid">
        <label>Category<select value={s.category} onChange={(e) => setS({ ...s, category: e.target.value })}>
          {["INGREDIENT", "PACKAGING", "BEVERAGE", "CLEANING", "OTHER"].map((c) => <option key={c}>{c}</option>)}</select></label>
        <label>Unit<select value={s.unit} disabled={!!s.id} onChange={(e) => setS({ ...s, unit: e.target.value })}>{UNITS.map((u) => <option key={u}>{u}</option>)}</select></label>
        <label>Low-stock alert at<input type="number" value={s.min_level} onChange={(e) => setS({ ...s, min_level: Number(e.target.value) })} /></label>
      </div>
      <label className="check"><input type="checkbox" checked={s.is_active} onChange={(e) => setS({ ...s, is_active: e.target.checked })} /> Active</label>
      {err && <p className="error">{err}</p>}
      <button onClick={() => void save()}>Save</button>
    </Modal>
  );
}

function AdjustDialog({ item, type, onClose, onSaved }: { item: StockItem; type: "OPENING" | "WASTAGE" | "COUNT"; onClose: () => void; onSaved: () => void }) {
  const [qty, setQty] = useState("");
  const [cost, setCost] = useState("");
  const [note, setNote] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const title = { OPENING: "Opening stock", WASTAGE: "Record wastage / spoilage", COUNT: "Physical stock count" }[type];
  return (
    <Modal title={`${title} — ${item.name}`} onClose={onClose}>
      <p className="small">Current: {Number(item.qty)} {item.unit}</p>
      <label>{type === "COUNT" ? `Actual quantity counted (${item.unit})` : `Quantity (${item.unit})`}<input value={qty} onChange={(e) => setQty(e.target.value)} /></label>
      {type === "OPENING" && <label>Cost per {item.unit} (₹)<input value={cost} onChange={(e) => setCost(e.target.value)} /></label>}
      <label>Note<input value={note} onChange={(e) => setNote(e.target.value)} placeholder={type === "WASTAGE" ? "e.g. spoiled, burnt, expired" : ""} /></label>
      {err && <p className="error">{err}</p>}
      <button onClick={async () => {
        try {
          await rpc("stock_adjust", { p: { type, itemId: item.id, qty: Number(qty), countedQty: Number(qty), unitCostPaise: toPaise(cost || "0"), note: note || null } });
          onSaved();
        } catch (e) { setErr((e as Error).message); }
      }}>Save (posts to accounts)</button>
    </Modal>
  );
}

function Recipes() {
  const { role } = useAuth();
  const [menu, setMenu] = useState<MenuItem[]>([]);
  const [stock, setStock] = useState<StockItem[]>([]);
  const [lines, setLines] = useState<RecipeLine[]>([]);
  const [costing, setCosting] = useState<Costing[]>([]);
  const [sel, setSel] = useState<string>("");
  const [add, setAdd] = useState({ stock: "", qty: "", unit: "g" });
  const [err, setErr] = useState<string | null>(null);
  const load = async () => {
    try {
      setMenu((await selectAll<MenuItem>("menu_items", "name")).filter((m) => !m.is_archived));
      setStock(await selectAll<StockItem>("stock_items", "name"));
      setLines(await selectAll<RecipeLine>("recipe_lines"));
      setCosting(await rpc<Costing[]>("menu_costing"));
    } catch (e) { setErr((e as Error).message); }
  };
  useEffect(() => { void load(); }, []);
  const editable = can(role, "OWNER", "MANAGER");
  return (
    <>
      <p className="small muted">A recipe = ingredients for ONE portion. When a bill is completed, these quantities are deducted from stock automatically
        (once per order) and food cost is posted to the accounts.</p>
      {err && <p className="error">{err}</p>}
      <div className="table-wrap"><table>
        <thead><tr><th>Dish</th><th className="right">Price</th><th className="right">Recipe cost</th><th className="right">Food cost %</th><th className="right">Margin</th><th /></tr></thead>
        <tbody>{costing.map((c) => (
          <tr key={c.itemId}>
            <td>{c.name}</td><td className="right">{rupees(c.pricePaise)}</td>
            <td className="right">{c.hasRecipe ? rupees(c.recipeCostPaise) : <span className="muted">no recipe</span>}</td>
            <td className={"right" + ((c.foodCostPercent ?? 0) > 40 ? " error" : "")}>{c.foodCostPercent != null ? c.foodCostPercent + "%" : "—"}</td>
            <td className="right">{c.hasRecipe ? rupees(c.marginPaise) : "—"}</td>
            <td><button className="link" onClick={() => setSel(c.itemId)}>Recipe</button></td>
          </tr>
        ))}</tbody>
      </table></div>
      {sel && (
        <Modal title={`Recipe — ${menu.find((m) => m.id === sel)?.name}`} onClose={() => setSel("")}>
          <table><tbody>{lines.filter((l) => l.menu_item_id === sel).map((l) => (
            <tr key={l.id}><td>{stock.find((s) => s.id === l.stock_item_id)?.name}</td><td className="right">{Number(l.qty)} {l.unit}</td>
              <td>{editable && <button className="link" onClick={async () => { await supabase.from("recipe_lines").delete().eq("id", l.id); void load(); }}>Remove</button>}</td></tr>
          ))}</tbody></table>
          {editable && (
            <div className="row" style={{ marginTop: 8 }}>
              <select style={{ maxWidth: 200 }} value={add.stock} onChange={(e) => setAdd({ ...add, stock: e.target.value })}>
                <option value="">Ingredient…</option>{stock.filter((s) => s.is_active).map((s) => <option key={s.id} value={s.id}>{s.name} ({s.unit})</option>)}
              </select>
              <input style={{ maxWidth: 90 }} placeholder="Qty" value={add.qty} onChange={(e) => setAdd({ ...add, qty: e.target.value })} />
              <select style={{ maxWidth: 80 }} value={add.unit} onChange={(e) => setAdd({ ...add, unit: e.target.value })}>{UNITS.map((u) => <option key={u}>{u}</option>)}</select>
              <button className="small" onClick={async () => {
                const { error } = await supabase.from("recipe_lines").insert({ menu_item_id: sel, stock_item_id: Number(add.stock), qty: Number(add.qty), unit: add.unit });
                if (error) setErr(error.message); else { setAdd({ stock: "", qty: "", unit: add.unit }); void load(); }
              }}>Add</button>
            </div>
          )}
          {err && <p className="error small">{err}</p>}
        </Modal>
      )}
    </>
  );
}

function PurchaseEntry() {
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [stock, setStock] = useState<StockItem[]>([]);
  const [sup, setSup] = useState("");
  const [invNo, setInvNo] = useState("");
  const [date, setDate] = useState(todayIST());
  const [due, setDue] = useState("");
  const [lines, setLines] = useState([{ stock: "", account: "", desc: "", qty: "", unit: "kg", rate: "", tax: "", batch: "", expiry: "" }]);
  const [paidNow, setPaidNow] = useState("");
  const [paidTender, setPaidTender] = useState("CASH");
  const [msg, setMsg] = useState<{ ok: boolean; t: string } | null>(null);
  useEffect(() => {
    selectAll<Supplier>("suppliers", "name").then(setSuppliers).catch(() => setMsg({ ok: false, t: "Add suppliers first (Suppliers page)." }));
    selectAll<StockItem>("stock_items", "name").then(setStock);
  }, []);
  const total = lines.reduce((s, l) => s + Math.round(Number(l.qty || 0) * (toPaise(l.rate || "0") ?? 0)) + (toPaise(l.tax || "0") ?? 0), 0);
  const set = (i: number, p: Partial<(typeof lines)[0]>) => setLines(lines.map((x, j) => (j === i ? { ...x, ...p } : x)));
  async function save() {
    try {
      const r = await rpc<{ totalPaise: number }>("record_purchase", { p: {
        supplierId: Number(sup), invoiceNo: invNo || null, invoiceDate: date, dueDate: due || null,
        paidNowPaise: paidNow ? toPaise(paidNow) : 0, paidTender,
        lines: lines.filter((l) => Number(l.qty) > 0).map((l) => ({
          stockItemId: l.stock ? Number(l.stock) : null, accountCode: l.stock ? null : l.account || "5010",
          description: l.desc || null, qty: Number(l.qty), unit: l.unit, ratePaise: toPaise(l.rate || "0"), taxPaise: toPaise(l.tax || "0"),
          batchNo: l.batch || null, expiryDate: l.expiry || null })) } });
      setMsg({ ok: true, t: `Saved. Invoice total ${rupees(r.totalPaise)} — stock and supplier ledger updated.` });
      setLines([{ stock: "", account: "", desc: "", qty: "", unit: "kg", rate: "", tax: "", batch: "", expiry: "" }]); setInvNo(""); setPaidNow("");
    } catch (e) { setMsg({ ok: false, t: (e as Error).message }); }
  }
  return (
    <div className="card">
      <div className="form-grid">
        <label>Supplier<select value={sup} onChange={(e) => setSup(e.target.value)}><option value="">Choose…</option>{suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
        <label>Supplier bill no.<input value={invNo} onChange={(e) => setInvNo(e.target.value)} /></label>
        <label>Bill date<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
        <label>Payment due date<input type="date" value={due} onChange={(e) => setDue(e.target.value)} /></label>
      </div>
      <div className="table-wrap"><table>
        <thead><tr><th>Stock item (or blank for non-stock)</th><th>Qty</th><th>Unit</th><th>Rate ₹/unit</th><th>GST ₹</th><th>Batch / expiry</th></tr></thead>
        <tbody>{lines.map((l, i) => (
          <tr key={i}>
            <td>
              <select value={l.stock} onChange={(e) => { const s = stock.find((x) => x.id === Number(e.target.value)); set(i, { stock: e.target.value, unit: s?.unit ?? l.unit }); }}>
                <option value="">— non-stock expense —</option>{stock.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.unit})</option>)}
              </select>
              {!l.stock && <input placeholder="Description (e.g. gas cylinder)" value={l.desc} onChange={(e) => set(i, { desc: e.target.value })} />}
              {!l.stock && <select value={l.account} onChange={(e) => set(i, { account: e.target.value })}>
                {[["5010", "Purchases — non-stock"], ["5100", "Packaging"], ["5420", "Gas / LPG"], ["5500", "Repairs"], ["5800", "Cleaning"], ["1500", "Fixed asset (equipment)"]].map(([c, n]) => <option key={c} value={c}>{c} {n}</option>)}
              </select>}
            </td>
            <td><input style={{ width: 80 }} value={l.qty} onChange={(e) => set(i, { qty: e.target.value })} /></td>
            <td><select style={{ width: 70 }} value={l.unit} onChange={(e) => set(i, { unit: e.target.value })}>{UNITS.map((u) => <option key={u}>{u}</option>)}</select></td>
            <td><input style={{ width: 90 }} value={l.rate} onChange={(e) => set(i, { rate: e.target.value })} /></td>
            <td><input style={{ width: 80 }} value={l.tax} onChange={(e) => set(i, { tax: e.target.value })} /></td>
            <td><input style={{ width: 90 }} placeholder="Batch" value={l.batch} onChange={(e) => set(i, { batch: e.target.value })} />
              <input type="date" style={{ width: 140 }} value={l.expiry} onChange={(e) => set(i, { expiry: e.target.value })} /></td>
          </tr>
        ))}</tbody>
      </table></div>
      <button className="secondary small" onClick={() => setLines([...lines, { stock: "", account: "", desc: "", qty: "", unit: "kg", rate: "", tax: "", batch: "", expiry: "" }])}>+ Line</button>
      <p><b>Bill total: {rupees(total)}</b></p>
      <div className="row">
        <label style={{ margin: 0 }}>Paid now ₹ (optional)<input style={{ maxWidth: 120 }} value={paidNow} onChange={(e) => setPaidNow(e.target.value)} /></label>
        <select style={{ maxWidth: 120 }} value={paidTender} onChange={(e) => setPaidTender(e.target.value)}>{["CASH", "UPI", "BANK"].map((t) => <option key={t}>{t}</option>)}</select>
        <button disabled={!sup || total <= 0} onClick={() => void save()}>Save purchase</button>
      </div>
      {msg && <p className={msg.ok ? "success" : "error"}>{msg.t}</p>}
      <p className="small muted">GST paid on purchases is claimed as input credit only if “Input tax credit” is on in Settings (regular GST scheme); otherwise it is added to cost.</p>
    </div>
  );
}

function PurchaseHistory() {
  const [rows, setRows] = useState<{ id: number; invoice_no: string; invoice_date: string; due_date: string | null; total_paise: number; tax_paise: number; supplier_id: number }[]>([]);
  const [sup, setSup] = useState<Supplier[]>([]);
  useEffect(() => {
    supabase.from("purchase_invoices").select("*").order("invoice_date", { ascending: false }).limit(200).then(({ data }) => setRows(data ?? []));
    selectAll<Supplier>("suppliers", "name").then(setSup);
  }, []);
  return (
    <table><thead><tr><th>Date</th><th>Supplier</th><th>Bill no.</th><th>Due</th><th className="right">GST</th><th className="right">Total</th></tr></thead>
      <tbody>{rows.map((r) => <tr key={r.id}><td>{r.invoice_date}</td><td>{sup.find((s) => s.id === r.supplier_id)?.name}</td><td>{r.invoice_no}</td>
        <td>{r.due_date ?? "—"}</td><td className="right">{rupees(r.tax_paise)}</td><td className="right">{rupees(r.total_paise)}</td></tr>)}</tbody></table>
  );
}

function Movements() {
  const [rows, setRows] = useState<Movement[]>([]);
  const [items, setItems] = useState<StockItem[]>([]);
  useEffect(() => {
    supabase.from("stock_movements").select("*").order("id", { ascending: false }).limit(300).then(({ data }) => setRows((data as Movement[]) ?? []));
    selectAll<StockItem>("stock_items", "name").then(setItems);
  }, []);
  const soon = rows.filter((r) => r.expiry_date && r.expiry_date <= todayIST(7) && r.expiry_date >= todayIST(-30));
  return (
    <>
      {soon.length > 0 && <div className="banner warn">Expiring soon: {soon.map((r) => `${items.find((i) => i.id === r.item_id)?.name} (batch ${r.batch_no ?? "-"}, ${r.expiry_date})`).join(", ")}</div>}
      <div className="table-wrap"><table>
        <thead><tr><th>When</th><th>Item</th><th>Type</th><th className="right">Qty</th><th className="right">Value</th><th>Note</th></tr></thead>
        <tbody>{rows.map((m) => <tr key={m.id}><td className="small">{dateTime(m.created_at)}</td><td>{items.find((i) => i.id === m.item_id)?.name}</td>
          <td className="small">{m.type}</td><td className="right">{Number(m.qty)}</td><td className="right">{rupees(m.value_paise)}</td>
          <td className="small">{m.note}{m.batch_no ? ` · batch ${m.batch_no}` : ""}{m.expiry_date ? ` · exp ${m.expiry_date}` : ""}</td></tr>)}</tbody>
      </table></div>
    </>
  );
}

