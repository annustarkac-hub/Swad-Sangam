import { useCallback, useEffect, useState } from "react";
import Modal from "../components/Modal";
import { dateTime, fromPaise, rupees, toPaise } from "../lib/format";
import { toWebp } from "../lib/image";
import { friendlyError, supabase } from "../lib/supabase";

interface Offer { id?: number; title: string; description: string | null; image_url: string | null; coupon_code: string | null; is_active: boolean; starts_at: string | null; ends_at: string | null; sort_order: number }
interface Coupon { code: string; description: string | null; discount_type: "PERCENT" | "FLAT"; value: number; max_discount_paise: number | null; min_order_paise: number; max_uses: number | null; used_count: number; is_active: boolean; starts_at: string | null; ends_at: string | null }

const toLocal = (iso: string | null) => (iso ? new Date(iso).toISOString().slice(0, 16) : "");
const fromLocal = (v: string) => (v ? new Date(v).toISOString() : null);

export default function OffersPage() {
  const [offers, setOffers] = useState<Offer[]>([]);
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [editOffer, setEditOffer] = useState<Offer | null>(null);
  const [editCoupon, setEditCoupon] = useState<{ c: Coupon; isNew: boolean } | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [o, c] = await Promise.all([
      supabase.from("offers").select("*").order("sort_order"),
      supabase.from("coupons").select("*").order("created_at", { ascending: false }),
    ]);
    if (o.error || c.error) setErr(friendlyError((o.error ?? c.error)!.message));
    setOffers((o.data as Offer[]) ?? []);
    setCoupons((c.data as Coupon[]) ?? []);
  }, []);
  useEffect(() => { void load(); }, [load]);

  return (
    <div>
      <h1>Offers &amp; coupons</h1>
      <p className="small muted">Offers appear as banners on the app home screen. Only advertise offers the restaurant will honour.</p>
      {err && <p className="error">{err}</p>}
      <div className="spread"><h2>Offers / promotions</h2>
        <button onClick={() => setEditOffer({ title: "", description: null, image_url: null, coupon_code: null, is_active: true, starts_at: null, ends_at: null, sort_order: offers.length + 1 })}>Add offer</button>
      </div>
      <div className="table-wrap"><table><tbody>
        {offers.map((o) => (
          <tr key={o.id}>
            <td><b>{o.title}</b><div className="small muted">{o.description}</div></td>
            <td className="small">{o.coupon_code}</td>
            <td className="small">{o.starts_at ? dateTime(o.starts_at) : "now"} → {o.ends_at ? dateTime(o.ends_at) : "no end"}</td>
            <td>{o.is_active ? <span className="badge READY">Active</span> : <span className="badge">Off</span>}</td>
            <td><button className="link" onClick={() => setEditOffer(o)}>Edit</button></td>
          </tr>
        ))}
        {offers.length === 0 && <tr><td className="muted">No offers yet.</td></tr>}
      </tbody></table></div>

      <div className="spread"><h2>Coupon codes</h2>
        <button onClick={() => setEditCoupon({ isNew: true, c: { code: "", description: null, discount_type: "PERCENT", value: 10, max_discount_paise: null, min_order_paise: 0, max_uses: null, used_count: 0, is_active: true, starts_at: null, ends_at: null } })}>Add coupon</button>
      </div>
      <div className="table-wrap"><table>
        <thead><tr><th>Code</th><th>Discount</th><th>Min order</th><th>Used</th><th>Status</th><th /></tr></thead>
        <tbody>
          {coupons.map((c) => (
            <tr key={c.code}>
              <td><b>{c.code}</b><div className="small muted">{c.description}</div></td>
              <td>{c.discount_type === "PERCENT" ? `${c.value}%` : rupees(c.value)}{c.max_discount_paise ? ` (max ${rupees(c.max_discount_paise)})` : ""}</td>
              <td>{rupees(c.min_order_paise)}</td>
              <td>{c.used_count}{c.max_uses ? ` / ${c.max_uses}` : ""}</td>
              <td>{c.is_active ? "Active" : "Off"}</td>
              <td><button className="link" onClick={() => setEditCoupon({ c, isNew: false })}>Edit</button></td>
            </tr>
          ))}
        </tbody>
      </table></div>

      {editOffer && <OfferEditor offer={editOffer} onClose={() => setEditOffer(null)} onSaved={() => { setEditOffer(null); void load(); }} />}
      {editCoupon && <CouponEditor {...editCoupon} onClose={() => setEditCoupon(null)} onSaved={() => { setEditCoupon(null); void load(); }} />}
    </div>
  );
}

function OfferEditor({ offer, onClose, onSaved }: { offer: Offer; onClose: () => void; onSaved: () => void }) {
  const [o, setO] = useState(offer);
  const [err, setErr] = useState<string | null>(null);
  async function upload(f: File) {
    try {
      const blob = await toWebp(f, 1280);
      const path = `offers/${Date.now()}.webp`;
      const { error } = await supabase.storage.from("menu-images").upload(path, blob, { contentType: "image/webp" });
      if (error) throw error;
      setO({ ...o, image_url: supabase.storage.from("menu-images").getPublicUrl(path).data.publicUrl });
    } catch (e) { setErr((e as Error).message); }
  }
  async function save() {
    if (!o.title.trim()) return setErr("Title is required");
    const row = { ...o, coupon_code: o.coupon_code?.toUpperCase() || null };
    const { error } = o.id ? await supabase.from("offers").update(row).eq("id", o.id) : await supabase.from("offers").insert(row);
    if (error) setErr(friendlyError(error.message)); else onSaved();
  }
  async function remove() {
    if (!o.id || !confirm("Delete this offer?")) return;
    const { error } = await supabase.from("offers").delete().eq("id", o.id);
    if (error) setErr(friendlyError(error.message)); else onSaved();
  }
  return (
    <Modal title={o.id ? "Edit offer" : "Add offer"} onClose={onClose}>
      <label>Title<input value={o.title} onChange={(e) => setO({ ...o, title: e.target.value })} placeholder="Explore Our Thalis" /></label>
      <label>Description<textarea value={o.description ?? ""} onChange={(e) => setO({ ...o, description: e.target.value || null })} /></label>
      <label>Coupon code shown on banner (optional)<input value={o.coupon_code ?? ""} onChange={(e) => setO({ ...o, coupon_code: e.target.value || null })} /></label>
      <div className="form-grid">
        <label>Starts<input type="datetime-local" value={toLocal(o.starts_at)} onChange={(e) => setO({ ...o, starts_at: fromLocal(e.target.value) })} /></label>
        <label>Ends<input type="datetime-local" value={toLocal(o.ends_at)} onChange={(e) => setO({ ...o, ends_at: fromLocal(e.target.value) })} /></label>
        <label>Sort order<input type="number" value={o.sort_order} onChange={(e) => setO({ ...o, sort_order: Number(e.target.value) })} /></label>
      </div>
      <div className="row">{o.image_url && <img className="thumb" src={o.image_url} alt="" />}<input type="file" accept="image/*" style={{ maxWidth: 260 }} onChange={(e) => e.target.files?.[0] && void upload(e.target.files[0])} /></div>
      <label className="check"><input type="checkbox" checked={o.is_active} onChange={(e) => setO({ ...o, is_active: e.target.checked })} /> Active</label>
      {err && <p className="error">{err}</p>}
      <div className="spread"><button onClick={() => void save()}>Save</button>{o.id && <button className="danger small" onClick={() => void remove()}>Delete</button>}</div>
    </Modal>
  );
}

function CouponEditor({ c: initial, isNew, onClose, onSaved }: { c: Coupon; isNew: boolean; onClose: () => void; onSaved: () => void }) {
  const [c, setC] = useState(initial);
  const [err, setErr] = useState<string | null>(null);
  async function save() {
    const row = { ...c, code: c.code.toUpperCase().replace(/[^A-Z0-9]/g, "") } as Partial<Coupon>;
    delete row.used_count;
    const { error } = isNew ? await supabase.from("coupons").insert(row) : await supabase.from("coupons").update(row).eq("code", initial.code);
    if (error) setErr(friendlyError(error.message)); else onSaved();
  }
  return (
    <Modal title={isNew ? "Add coupon" : `Edit ${c.code}`} onClose={onClose}>
      <div className="form-grid">
        <label>Code (A–Z, 0–9)<input value={c.code} disabled={!isNew} onChange={(e) => setC({ ...c, code: e.target.value })} /></label>
        <label>Type<select value={c.discount_type} onChange={(e) => setC({ ...c, discount_type: e.target.value as "PERCENT" | "FLAT" })}><option value="PERCENT">Percent off</option><option value="FLAT">Flat ₹ off</option></select></label>
        <label>{c.discount_type === "PERCENT" ? "Percent (1–100)" : "Amount (₹)"}
          <input value={c.discount_type === "PERCENT" ? c.value : fromPaise(c.value)} onChange={(e) => setC({ ...c, value: c.discount_type === "PERCENT" ? Number(e.target.value) : toPaise(e.target.value) ?? 0 })} />
        </label>
        <label>Max discount (₹, optional)<input value={fromPaise(c.max_discount_paise)} onChange={(e) => setC({ ...c, max_discount_paise: e.target.value ? toPaise(e.target.value) : null })} /></label>
        <label>Minimum order (₹)<input value={fromPaise(c.min_order_paise)} onChange={(e) => setC({ ...c, min_order_paise: toPaise(e.target.value || "0") ?? 0 })} /></label>
        <label>Max uses (optional)<input type="number" value={c.max_uses ?? ""} onChange={(e) => setC({ ...c, max_uses: e.target.value ? Number(e.target.value) : null })} /></label>
        <label>Starts<input type="datetime-local" value={toLocal(c.starts_at)} onChange={(e) => setC({ ...c, starts_at: fromLocal(e.target.value) })} /></label>
        <label>Ends<input type="datetime-local" value={toLocal(c.ends_at)} onChange={(e) => setC({ ...c, ends_at: fromLocal(e.target.value) })} /></label>
      </div>
      <label>Description<input value={c.description ?? ""} onChange={(e) => setC({ ...c, description: e.target.value || null })} /></label>
      <label className="check"><input type="checkbox" checked={c.is_active} onChange={(e) => setC({ ...c, is_active: e.target.checked })} /> Active</label>
      {err && <p className="error">{err}</p>}
      <button onClick={() => void save()}>Save</button>
    </Modal>
  );
}
