import QRCode from "qrcode";
import { useEffect, useState } from "react";

/**
 * Printable QR codes for promotion (PRD §18): tables, counter, receipts.
 * Table QR codes open the app's menu with the table number pre-filled
 * (dine-in / QR table ordering). The Play Store link is used when the app is not installed.
 */
export default function QrPage() {
  const [store, setStore] = useState("https://play.google.com/store/apps/details?id=in.swadsangam.restaurant");
  const [tables, setTables] = useState(8);
  const [base, setBase] = useState("swadsangam://menu");
  const [codes, setCodes] = useState<{ label: string; url: string; img: string }[]>([]);

  useEffect(() => {
    const list = [
      { label: "Download the Swad Sangam app", url: store },
      ...Array.from({ length: tables }, (_, i) => ({ label: `Table ${i + 1} — scan to order`, url: `${base}?table=${i + 1}` })),
    ];
    Promise.all(list.map(async (x) => ({ ...x, img: await QRCode.toDataURL(x.url, { margin: 1, width: 360, color: { dark: "#34241F" } }) })))
      .then(setCodes);
  }, [store, tables, base]);

  return (
    <div>
      <div className="spread"><h1>QR codes</h1><button onClick={() => window.print()}>Print</button></div>
      <div className="form-grid">
        <label>Play Store link<input value={store} onChange={(e) => setStore(e.target.value)} /></label>
        <label>Table link (app deep link)<input value={base} onChange={(e) => setBase(e.target.value)} /></label>
        <label>Number of tables<input type="number" min={0} max={50} value={tables} onChange={(e) => setTables(Number(e.target.value))} /></label>
      </div>
      <p className="small muted">
        Table QR codes open the installed app on the menu with the table number filled in (dine-in must be enabled in
        Settings). The default <code>swadsangam://menu</code> link works with the phone camera / Google Lens on most
        Android phones. If you later own a website domain, see docs/SETUP_GUIDE.md → “QR table ordering”.
      </p>
      <div className="grid">
        {codes.map((c) => (
          <div key={c.url} className="card" style={{ textAlign: "center", breakInside: "avoid" }}>
            <img src={c.img} alt={c.label} style={{ width: "100%", maxWidth: 240 }} />
            <div><b>Swad Sangam</b></div>
            <div className="small">{c.label}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
