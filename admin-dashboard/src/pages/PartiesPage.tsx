import { useEffect, useState } from "react";
import { printPage } from "../lib/native";
import DateRange from "../components/DateRange";
import Modal from "../components/Modal";
import { rpc, selectAll, TENDER_LABEL, uuid, type Tender } from "../lib/erp";
import { fromPaise, rupees, todayIST, toPaise } from "../lib/format";
import { supabase } from "../lib/supabase";
import { LedgerTable } from "./AccountingPage";

type Kind = "CUSTOMER" | "SUPPLIER";
interface Party { id: number; name: string; phone: string | null; loyalty_points?: number; gstin?: string | null; credit_limit_paise?: number; notes: string | null; is_active: boolean; address?: string | null }
interface Balance { partyId: number; name: string; balancePaise: number; d0_30: number; d31_60: number; d61_90: number; d90plus: number; lastActivity: string }

/** Customer ledger (khata) and supplier ledger with ageing, statements and payments. */
export default function PartiesPage({ kind }: { kind: Kind }) {
  const isCust = kind === "CUSTOMER";
  const table = isCust ? "customers" : "suppliers";
  const [parties, setParties] = useState<Party[]>([]);
  const [balances, setBalances] = useState<Balance[]>([]);
  const [edit, setEdit] = useState<Party | null>(null);
  const [pay, setPay] = useState<Party | null>(null);
  const [stmt, setStmt] = useState<Party | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = async () => {
    try {
      setParties(await selectAll<Party>(table, "name"));
      setBalances(await rpc<Balance[]>("party_balances", { p_type: kind }));
    } catch (e) { setErr((e as Error).message); }
  };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [kind]);
  const bal = (id: number) => balances.find((b) => b.partyId === id);
  const total = balances.reduce((s, b) => s + b.balancePaise, 0);

  return (
    <div>
      <div className="spread">
        <h1>{isCust ? "Customers, khata & loyalty" : "Suppliers & payables"}</h1>
        <button onClick={() => setEdit({ id: 0, name: "", phone: "", notes: "", is_active: true, credit_limit_paise: 0, gstin: "" })}>Add {isCust ? "customer" : "supplier"}</button>
      </div>
      <p>{isCust ? "Total customers owe you" : "Total you owe suppliers"}: <b>{rupees(total)}</b></p>
      {err && <p className="error">{err}</p>}
      <div className="table-wrap"><table>
        <thead><tr><th>Name</th><th>Phone</th>{isCust && <th className="right">Points</th>}{isCust && <th className="right">Credit limit</th>}<th className="right">Balance</th>
          <th className="right">0–30 d</th><th className="right">31–60</th><th className="right">61–90</th><th className="right">90+</th><th /></tr></thead>
        <tbody>{parties.map((p) => {
          const b = bal(p.id);
          return (
            <tr key={p.id} style={{ opacity: p.is_active ? 1 : 0.5 }}>
              <td>{p.name}</td><td>{p.phone}</td>{isCust && <td className="right">{p.loyalty_points ?? 0}</td>}{isCust && <td className="right">{rupees(p.credit_limit_paise)}</td>}
              <td className="right"><b>{rupees(b?.balancePaise ?? 0)}</b></td>
              <td className="right">{rupees(b?.d0_30 ?? 0)}</td><td className="right">{rupees(b?.d31_60 ?? 0)}</td>
              <td className="right">{rupees(b?.d61_90 ?? 0)}</td><td className={"right" + ((b?.d90plus ?? 0) > 0 ? " error" : "")}>{rupees(b?.d90plus ?? 0)}</td>
              <td className="row">
                <button className="small" onClick={() => setPay(p)}>{isCust ? "Receive payment" : "Pay"}</button>
                <button className="link" onClick={() => setStmt(p)}>Statement</button>
                <button className="link" onClick={() => setEdit(p)}>Edit</button>
                {isCust && p.phone && (b?.balancePaise ?? 0) > 0 && (
                  <a className="small" target="_blank" rel="noreferrer"
                    href={`https://wa.me/91${p.phone.replace(/\D/g, "").slice(-10)}?text=${encodeURIComponent(`Namaste ${p.name}, Swad Sangam mein aapka baki ${rupees(b!.balancePaise)} hai. Kripya jaldi bhugtan karein. Dhanyavaad!`)}`}>
                    WhatsApp reminder</a>
                )}
              </td>
            </tr>
          );
        })}</tbody>
      </table></div>
      <p className="small muted">WhatsApp reminders open WhatsApp on this device with a ready message — you press send yourself. No automatic messages are sent.</p>
      {edit && <PartyEditor kind={kind} party={edit} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); void load(); }} />}
      {pay && <PaymentDialog kind={kind} party={pay} due={bal(pay.id)?.balancePaise ?? 0} onClose={() => setPay(null)} onSaved={() => { setPay(null); void load(); }} />}
      {stmt && <Statement kind={kind} party={stmt} onClose={() => setStmt(null)} />}
    </div>
  );
}

function PartyEditor({ kind, party, onClose, onSaved }: { kind: Kind; party: Party; onClose: () => void; onSaved: () => void }) {
  const [p, setP] = useState(party);
  const [limit, setLimit] = useState(fromPaise(party.credit_limit_paise ?? 0));
  const [err, setErr] = useState<string | null>(null);
  const isCust = kind === "CUSTOMER";
  async function save() {
    const row: Record<string, unknown> = { name: p.name, phone: p.phone || null, notes: p.notes || null, is_active: p.is_active };
    if (isCust) row.credit_limit_paise = toPaise(limit || "0") ?? 0; else { row.gstin = p.gstin || null; row.address = p.address || null; }
    const t = isCust ? "customers" : "suppliers";
    const { error } = p.id ? await supabase.from(t).update(row).eq("id", p.id) : await supabase.from(t).insert(row);
    if (error) setErr(error.message); else onSaved();
  }
  return (
    <Modal title={p.id ? `Edit ${p.name}` : isCust ? "Add customer" : "Add supplier"} onClose={onClose}>
      <label>Name<input value={p.name} onChange={(e) => setP({ ...p, name: e.target.value })} /></label>
      <label>Phone<input value={p.phone ?? ""} onChange={(e) => setP({ ...p, phone: e.target.value })} /></label>
      {isCust ? <label>Credit limit ₹ (0 = no credit)<input value={limit} onChange={(e) => setLimit(e.target.value)} /></label> : (
        <><label>GSTIN<input value={p.gstin ?? ""} onChange={(e) => setP({ ...p, gstin: e.target.value })} /></label>
          <label>Address<input value={p.address ?? ""} onChange={(e) => setP({ ...p, address: e.target.value })} /></label></>
      )}
      <label>Notes<input value={p.notes ?? ""} onChange={(e) => setP({ ...p, notes: e.target.value })} /></label>
      <label className="check"><input type="checkbox" checked={p.is_active} onChange={(e) => setP({ ...p, is_active: e.target.checked })} /> Active</label>
      {err && <p className="error">{err}</p>}
      <button onClick={() => void save()}>Save</button>
    </Modal>
  );
}

function PaymentDialog({ kind, party, due, onClose, onSaved }: { kind: Kind; party: Party; due: number; onClose: () => void; onSaved: () => void }) {
  const [amt, setAmt] = useState(fromPaise(Math.max(due, 0)));
  const [tender, setTender] = useState<Tender>("CASH");
  const [ref, setRef] = useState("");
  const [date, setDate] = useState(todayIST());
  const [err, setErr] = useState<string | null>(null);
  const [clientRef] = useState(uuid());
  async function save() {
    try {
      const p = { amountPaise: toPaise(amt), tender, reference: ref || null, clientRef };
      if (kind === "CUSTOMER") await rpc("record_customer_receipt", { p: { ...p, customerId: party.id, receivedOn: date } });
      else await rpc("record_supplier_payment", { p: { ...p, supplierId: party.id, paidOn: date } });
      onSaved();
    } catch (e) { setErr((e as Error).message); }
  }
  return (
    <Modal title={kind === "CUSTOMER" ? `Payment from ${party.name}` : `Pay ${party.name}`} onClose={onClose}>
      <p>Outstanding: <b>{rupees(due)}</b></p>
      <div className="form-grid">
        <label>Amount ₹<input value={amt} onChange={(e) => setAmt(e.target.value)} /></label>
        <label>Mode<select value={tender} onChange={(e) => setTender(e.target.value as Tender)}>
          {(["CASH", "UPI", "BANK", "CARD"] as Tender[]).map((t) => <option key={t} value={t}>{TENDER_LABEL[t]}</option>)}</select></label>
        <label>Reference<input value={ref} onChange={(e) => setRef(e.target.value)} /></label>
        <label>Date<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
      </div>
      {err && <p className="error">{err}</p>}
      <button onClick={() => void save()}>Save payment</button>
    </Modal>
  );
}

function Statement({ kind, party, onClose }: { kind: Kind; party: Party; onClose: () => void }) {
  const [from, setFrom] = useState(todayIST(-90));
  const [to, setTo] = useState(todayIST());
  const [data, setData] = useState<Parameters<typeof LedgerTable>[0]["data"] | null>(null);
  useEffect(() => {
    rpc<NonNullable<typeof data>>("account_ledger", { p_code: kind === "CUSTOMER" ? "1100" : "2000", p_from: from, p_to: to, p_party_type: kind, p_party_id: party.id })
      .then(setData);
  }, [from, to, kind, party.id]);
  return (
    <Modal title={`Statement — ${party.name}`} onClose={onClose}>
      <DateRange from={from} to={to} onChange={(a, b) => { setFrom(a); setTo(b); }} />
      <p className="small muted">{kind === "CUSTOMER" ? "Dr = customer owes you" : "Cr = you owe the supplier"}</p>
      {data && <LedgerTable data={data} file={`statement-${party.name}-${from}-${to}.csv`} />}
      <button className="secondary small" onClick={() => printPage()}>Print / PDF</button>
    </Modal>
  );
}
