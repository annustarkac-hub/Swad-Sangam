import { useCallback, useEffect, useState } from "react";
import { can, useAuth } from "../auth";
import Modal from "../components/Modal";
import { FoodDot } from "../components/OrderCard";
import { fromPaise, rupees, slugify, toPaise } from "../lib/format";
import { toWebp } from "../lib/image";
import { friendlyError, supabase } from "../lib/supabase";
import type { Category, ItemOption, MenuItem } from "../lib/types";

const EMPTY: MenuItem = {
  id: "", name: "", name_hi: null, category_id: "", description: null, description_hi: null, price_paise: 0,
  food_type: "VEG", image_url: null, is_available: true, is_archived: false, is_popular: false,
  preparation_minutes: null, allergens: null, portion_info: null, options: [], details_verified: false, sort_order: 0,
};

export default function MenuPage() {
  const { role } = useAuth();
  const editor = can(role, "OWNER", "MANAGER");
  const [cats, setCats] = useState<Category[]>([]);
  const [items, setItems] = useState<MenuItem[]>([]);
  const [filter, setFilter] = useState("");
  const [showArchived, setShowArchived] = useState(false);
  const [edit, setEdit] = useState<MenuItem | null>(null);
  const [editCat, setEditCat] = useState<Category | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [c, i] = await Promise.all([
      supabase.from("categories").select("*").order("sort_order"),
      supabase.from("menu_items").select("*").order("sort_order"),
    ]);
    if (c.error || i.error) setErr(friendlyError((c.error ?? i.error)!.message));
    setCats((c.data as Category[]) ?? []);
    setItems((i.data as MenuItem[]) ?? []);
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function toggleAvailable(it: MenuItem) {
    const { error } = await supabase.rpc("set_item_availability", { p_item_id: it.id, p_available: !it.is_available });
    if (error) setErr(friendlyError(error.message)); else void load();
  }

  const visible = items.filter((i) => (showArchived || !i.is_archived) &&
    (!filter || i.name.toLowerCase().includes(filter.toLowerCase())));
  const unverified = items.filter((i) => !i.is_archived && !i.details_verified).length;

  return (
    <div>
      <div className="spread">
        <h1>Menu</h1>
        {editor && (
          <div className="row">
            <button className="secondary" onClick={() => setEditCat({ id: "", name: "", name_hi: null, sort_order: cats.length + 1, is_active: true })}>Add category</button>
            <button onClick={() => setEdit({ ...EMPTY, category_id: cats[0]?.id ?? "" })}>Add dish</button>
          </div>
        )}
      </div>
      {unverified > 0 && editor && (
        <div className="banner warn small">
          {unverified} dish(es) not yet verified by the owner. Confirm price, description, portion, allergens and photo,
          then tick “Details verified” before enabling ordering (PRD §2.3, §12.5).
        </div>
      )}
      {err && <p className="error">{err}</p>}
      <div className="row" style={{ marginBottom: 12 }}>
        <input style={{ maxWidth: 260 }} placeholder="Search dishes" value={filter} onChange={(e) => setFilter(e.target.value)} />
        <label className="check" style={{ margin: 0 }}><input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> Show archived</label>
      </div>

      {cats.map((c) => {
        const list = visible.filter((i) => i.category_id === c.id);
        return (
          <section key={c.id} style={{ marginBottom: 20 }}>
            <div className="spread">
              <h2>{c.name} {c.name_hi && <span className="muted small">{c.name_hi}</span>} {!c.is_active && <span className="badge">Hidden</span>}</h2>
              {editor && <button className="link" onClick={() => setEditCat(c)}>Edit category</button>}
            </div>
            <div className="table-wrap">
              <table>
                <tbody>
                  {list.map((it) => (
                    <tr key={it.id} style={{ opacity: it.is_archived ? 0.5 : 1 }}>
                      <td style={{ width: 64 }}>{it.image_url ? <img className="thumb" src={it.image_url} alt="" /> : <div className="thumb" />}{it.image_is_representative && <div className="small muted">sample</div>}</td>
                      <td>
                        <FoodDot type={it.food_type} /> <b>{it.name}</b> {it.is_popular && <span className="badge">Popular</span>}
                        {!it.details_verified && <span className="badge" title="Not verified by owner">Unverified</span>}
                        <div className="small muted">{it.description}</div>
                        {it.options.length > 0 && <div className="small">Options: {it.options.map((o) => `${o.name} (+${rupees(o.pricePaise)})`).join(", ")}</div>}
                      </td>
                      <td className="right"><b>{rupees(it.price_paise)}</b></td>
                      <td style={{ width: 170 }}>
                        <button className={it.is_available ? "ok small" : "danger small"} onClick={() => void toggleAvailable(it)}
                          title="Tap to change availability">
                          {it.is_available ? "Available" : "Unavailable"}
                        </button>
                        {editor && <button className="link" onClick={() => setEdit(it)}>Edit</button>}
                      </td>
                    </tr>
                  ))}
                  {list.length === 0 && <tr><td className="muted small">No dishes.</td></tr>}
                </tbody>
              </table>
            </div>
          </section>
        );
      })}
      {edit && <ItemEditor item={edit} cats={cats} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); void load(); }} />}
      {editCat && <CategoryEditor cat={editCat} onClose={() => setEditCat(null)} onSaved={() => { setEditCat(null); void load(); }} />}
    </div>
  );
}

function CategoryEditor({ cat, onClose, onSaved }: { cat: Category; onClose: () => void; onSaved: () => void }) {
  const [c, setC] = useState(cat);
  const [err, setErr] = useState<string | null>(null);
  const isNew = !cat.id;
  async function save() {
    const row = { ...c, id: isNew ? slugify(c.name) : c.id };
    if (row.id.length < 2) return setErr("Enter a category name.");
    const { error } = isNew ? await supabase.from("categories").insert(row) : await supabase.from("categories").update(row).eq("id", cat.id);
    if (error) setErr(friendlyError(error.message)); else onSaved();
  }
  return (
    <Modal title={isNew ? "Add category" : "Edit category"} onClose={onClose}>
      <label>Name<input value={c.name} onChange={(e) => setC({ ...c, name: e.target.value })} /></label>
      <label>Name (Hindi, optional)<input value={c.name_hi ?? ""} onChange={(e) => setC({ ...c, name_hi: e.target.value || null })} /></label>
      <label>Sort order<input type="number" value={c.sort_order} onChange={(e) => setC({ ...c, sort_order: Number(e.target.value) })} /></label>
      <label className="check"><input type="checkbox" checked={c.is_active} onChange={(e) => setC({ ...c, is_active: e.target.checked })} /> Visible to customers</label>
      {err && <p className="error">{err}</p>}
      <button onClick={() => void save()}>Save</button>
    </Modal>
  );
}

function ItemEditor({ item, cats, onClose, onSaved }: { item: MenuItem; cats: Category[]; onClose: () => void; onSaved: () => void }) {
  const [it, setIt] = useState<MenuItem>(item);
  const [price, setPrice] = useState(fromPaise(item.price_paise || null));
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const isNew = !item.id;
  const set = (p: Partial<MenuItem>) => setIt({ ...it, ...p });

  async function upload(file: File) {
    setBusy(true);
    setErr(null);
    try {
      const blob = await toWebp(file);
      const path = `dishes/${Date.now()}-${Math.random().toString(36).slice(2, 8)}.webp`;
      const { error } = await supabase.storage.from("menu-images").upload(path, blob, { contentType: "image/webp", upsert: false });
      if (error) throw error;
      set({ image_url: supabase.storage.from("menu-images").getPublicUrl(path).data.publicUrl, image_is_representative: false, image_credit: null });
    } catch (e) {
      setErr(friendlyError((e as Error).message));
    } finally {
      setBusy(false);
    }
  }

  function setOption(i: number, p: Partial<ItemOption>) {
    const opts = [...it.options];
    opts[i] = { ...opts[i], ...p };
    set({ options: opts });
  }

  async function save() {
    const p = toPaise(price);
    if (!it.name.trim()) return setErr("Name is required.");
    if (!p) return setErr("Enter a valid price, e.g. 60 or 60.50");
    if (!it.category_id) return setErr("Choose a category.");
    const options = it.options
      .filter((o) => o.name.trim())
      .map((o) => ({ ...o, id: o.id || slugify(o.name) || "opt", pricePaise: o.pricePaise || 0 }));
    const row: Partial<MenuItem> = { ...it, price_paise: p, options };
    delete row.updated_at;
    if (isNew) delete row.id;
    setBusy(true);
    const { error } = isNew ? await supabase.from("menu_items").insert(row) : await supabase.from("menu_items").update(row).eq("id", item.id);
    setBusy(false);
    if (error) setErr(friendlyError(error.message)); else onSaved();
  }

  async function remove() {
    if (!confirm(`Delete "${item.name}" permanently? Past orders keep their own copy of the name and price. Archiving is usually better.`)) return;
    const { error } = await supabase.from("menu_items").delete().eq("id", item.id);
    if (error) setErr(friendlyError(error.message)); else onSaved();
  }

  return (
    <Modal title={isNew ? "Add dish" : `Edit ${item.name}`} onClose={onClose}>
      <div className="form-grid">
        <label>Name<input value={it.name} onChange={(e) => set({ name: e.target.value })} /></label>
        <label>Name (Hindi)<input value={it.name_hi ?? ""} onChange={(e) => set({ name_hi: e.target.value || null })} /></label>
        <label>Price (₹)<input inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} /></label>
        <label>Category
          <select value={it.category_id} onChange={(e) => set({ category_id: e.target.value })}>
            {cats.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
        <label>Food type
          <select value={it.food_type} onChange={(e) => set({ food_type: e.target.value as "VEG" | "NON_VEG" })}>
            <option value="VEG">Vegetarian</option>
            <option value="NON_VEG">Non-vegetarian</option>
          </select>
        </label>
        <label>Preparation time (minutes)<input type="number" min={1} max={240} value={it.preparation_minutes ?? ""} onChange={(e) => set({ preparation_minutes: e.target.value ? Number(e.target.value) : null })} /></label>
        <label>Portion<input placeholder="e.g. 2 parathas with curd" value={it.portion_info ?? ""} onChange={(e) => set({ portion_info: e.target.value || null })} /></label>
        <label>Allergens<input placeholder="e.g. Contains gluten, dairy" value={it.allergens ?? ""} onChange={(e) => set({ allergens: e.target.value || null })} /></label>
        <label>Sort order<input type="number" value={it.sort_order} onChange={(e) => set({ sort_order: Number(e.target.value) })} /></label>
      </div>
      <label>Description<textarea value={it.description ?? ""} onChange={(e) => set({ description: e.target.value || null })} /></label>
      <label>Description (Hindi)<textarea value={it.description_hi ?? ""} onChange={(e) => set({ description_hi: e.target.value || null })} /></label>

      <h3>Photo</h3>
      <p className="small muted">Use a real photo of this Swad Sangam dish — not a stock image. It is resized and converted to WebP automatically.</p>
      <div className="row">
        {it.image_url && <img className="thumb" src={it.image_url} alt="" />}
        <input type="file" accept="image/*" style={{ maxWidth: 260 }} onChange={(e) => e.target.files?.[0] && void upload(e.target.files[0])} />
        {it.image_url && <button className="link" onClick={() => set({ image_url: null, image_is_representative: false, image_credit: null })}>Remove photo</button>}
      </div>
      {it.image_is_representative && (
        <p className="small" style={{ color: "var(--saffron)" }}>⚠ This is a representative photo (not from Swad Sangam){it.image_credit ? ` — ${it.image_credit}` : ""}. Upload a real photo of this dish to replace it.</p>
      )}
      <div>
      </div>

      <h3 style={{ marginTop: 12 }}>Options / add-ons</h3>
      {it.options.map((o, i) => (
        <div className="row" key={i} style={{ marginBottom: 6 }}>
          <input style={{ maxWidth: 220 }} placeholder="Option name" value={o.name} onChange={(e) => setOption(i, { name: e.target.value })} />
          <input style={{ maxWidth: 110 }} placeholder="+₹" value={fromPaise(o.pricePaise)} onChange={(e) => setOption(i, { pricePaise: toPaise(e.target.value || "0") ?? 0 })} />
          <label className="check" style={{ margin: 0 }}><input type="checkbox" checked={o.isAvailable !== false} onChange={(e) => setOption(i, { isAvailable: e.target.checked })} /> available</label>
          <button className="link" onClick={() => set({ options: it.options.filter((_, j) => j !== i) })}>Remove</button>
        </div>
      ))}
      <button className="secondary small" onClick={() => set({ options: [...it.options, { id: "", name: "", pricePaise: 0 }] })}>Add option</button>

      <h3 style={{ marginTop: 12 }}>Status</h3>
      <label className="check"><input type="checkbox" checked={it.is_available} onChange={(e) => set({ is_available: e.target.checked })} /> Available today</label>
      <label className="check"><input type="checkbox" checked={it.is_popular} onChange={(e) => set({ is_popular: e.target.checked })} /> Show “Popular” label (only if backed by sales or owner approval)</label>
      <label className="check"><input type="checkbox" checked={it.details_verified} onChange={(e) => set({ details_verified: e.target.checked })} /> Details verified by owner (price, veg/non-veg, description, portion, allergens, photo)</label>
      <label className="check"><input type="checkbox" checked={it.is_archived} onChange={(e) => set({ is_archived: e.target.checked })} /> Archived (hidden from customers; keeps order history intact)</label>
      {err && <p className="error">{err}</p>}
      <div className="spread">
        <button disabled={busy} onClick={() => void save()}>{busy ? "Saving…" : "Save dish"}</button>
        {!isNew && (
          <button className="danger small" disabled={busy} onClick={() => void remove()}>Delete permanently</button>
        )}
      </div>
    </Modal>
  );
}
