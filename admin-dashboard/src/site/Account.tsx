import { ReceiptText, Star } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { cleanPhone, db, store, validPhone, type Address, type Customer } from "./common";
import type { Lang } from "./i18n";

/** Signed-in customer (Supabase email + password). Null when signed out; undefined while loading. */
export function useCustomer(): [Customer | null | undefined, () => Promise<void>, boolean] {
  const [c, setC] = useState<Customer | null | undefined>(undefined);
  const [recovery, setRecovery] = useState(false);
  const load = useCallback(async () => {
    const { data } = await db.auth.getSession();
    const u = data.session?.user;
    if (!u) { setC(null); return; }
    const [p, a] = await Promise.all([
      db.from("customer_profiles").select("*").eq("user_id", u.id).maybeSingle(),
      db.from("customer_addresses").select("*").order("is_default", { ascending: false }).order("created_at"),
    ]);
    let profile = p.data ?? null;
    // finish the profile from the details given at sign-up (also after email confirmation)
    const meta = (u.user_metadata ?? {}) as { full_name?: string; phone?: string };
    if (!profile && meta.full_name && meta.phone && validPhone(meta.phone)) {
      const r = await db.from("customer_profiles").upsert({ user_id: u.id, full_name: meta.full_name, phone: cleanPhone(meta.phone) }).select().maybeSingle();
      profile = r.data ?? null;
    }
    if (profile) store.set("ss_customer", { name: profile.full_name, phone: profile.phone });
    setC({ userId: u.id, email: u.email ?? "", profile, addresses: (a.data as Address[]) ?? [] });
  }, []);
  useEffect(() => {
    void load();
    const { data } = db.auth.onAuthStateChange((evt, session) => {
      if (evt === "PASSWORD_RECOVERY") setRecovery(true);
      if (evt === "SIGNED_IN" && session) {
        // attach orders this phone placed before logging in
        const tokens = store.get<string[]>("ss_tokens", []);
        if (tokens.length) void db.rpc("claim_my_orders", { p_tokens: tokens });
      }
      if (evt === "SIGNED_IN" || evt === "SIGNED_OUT" || evt === "USER_UPDATED") setTimeout(() => void load(), 0);
    });
    return () => data.subscription.unsubscribe();
  }, [load]);
  return [c, load, recovery];
}

const tx = (lang: Lang, en: string, hi: string) => (lang === "hi" ? hi : en);

function authError(lang: Lang, m: string): string {
  if (/invalid login/i.test(m)) return tx(lang, "Wrong email or password.", "ईमेल या पासवर्ड गलत है।");
  if (/already registered|already exists/i.test(m)) return tx(lang, "This email already has an account. Please log in.", "इस ईमेल का खाता पहले से है। लॉगिन करें।");
  if (/at least 6|password/i.test(m)) return tx(lang, "Password must be at least 6 characters.", "पासवर्ड कम से कम 6 अक्षर का हो।");
  if (/rate limit|too many/i.test(m)) return tx(lang, "Too many tries. Please wait a few minutes.", "बहुत बार कोशिश हुई। कुछ मिनट रुकें।");
  if (/email not confirmed/i.test(m)) return tx(lang, "Please confirm your email first (check your inbox).", "पहले ईमेल की पुष्टि करें (इनबॉक्स देखें)।");
  return m;
}

export function AccountView({ lang, customer, reload, recovery }: { lang: Lang; customer: Customer | null | undefined; reload: () => Promise<void>; recovery: boolean }) {
  if (customer === undefined) return <p className="empty-s">…</p>;
  if (recovery && customer) return <NewPassword lang={lang} />;
  if (!customer) return <AuthForm lang={lang} />;
  return <Profile lang={lang} c={customer} reload={reload} />;
}

function AuthForm({ lang }: { lang: Lang }) {
  const [mode, setMode] = useState<"login" | "signup" | "forgot">("login");
  const [f, setF] = useState({ email: "", password: "", name: "", phone: "" });
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault(); setMsg(null);
    const email = f.email.trim().toLowerCase();
    if (!/^\S+@\S+\.\S+$/.test(email)) return setMsg({ ok: false, text: tx(lang, "Please enter a valid email.", "सही ईमेल लिखें।") });
    setBusy(true);
    try {
      if (mode === "forgot") {
        const { error } = await db.auth.resetPasswordForEmail(email, { redirectTo: location.origin + location.pathname + "#/account" });
        if (error) throw error;
        setMsg({ ok: true, text: tx(lang, "If this email has an account, a reset link has been sent. Check your inbox (and spam).", "अगर इस ईमेल का खाता है तो रीसेट लिंक भेज दिया गया है। इनबॉक्स (और स्पैम) देखें।") });
      } else if (mode === "login") {
        const { error } = await db.auth.signInWithPassword({ email, password: f.password });
        if (error) throw error;
      } else {
        if (f.name.trim().length < 2 || !validPhone(f.phone)) { setMsg({ ok: false, text: tx(lang, "Please enter your name and a valid 10-digit mobile number.", "नाम और सही 10 अंकों का मोबाइल नंबर लिखें।") }); return; }
        if (f.password.length < 6) { setMsg({ ok: false, text: authError(lang, "at least 6") }); return; }
        const { data, error } = await db.auth.signUp({ email, password: f.password, options: { data: { account_type: "customer", full_name: f.name.trim(), phone: cleanPhone(f.phone) } } });
        if (error) throw error;
        if (data.session && data.user) {
          await db.from("customer_profiles").upsert({ user_id: data.user.id, full_name: f.name.trim(), phone: cleanPhone(f.phone) });
        } else {
          setMsg({ ok: true, text: tx(lang, "Account created. Please open the confirmation email we sent, then log in.", "खाता बन गया। भेजे गए ईमेल से पुष्टि करें, फिर लॉगिन करें।") });
        }
      }
    } catch (err) {
      setMsg({ ok: false, text: authError(lang, (err as Error).message) });
    } finally { setBusy(false); }
  }

  return (
    <div className="stack">
      <div className="card-s2 form-s">
        <h2 className="sec" style={{ marginTop: 0 }}>
          {mode === "login" ? tx(lang, "Log in", "लॉगिन") : mode === "signup" ? tx(lang, "Create account", "खाता बनाएँ") : tx(lang, "Reset password", "पासवर्ड रीसेट")}
        </h2>
        <p className="muted-s small">{tx(lang, "Save your details and addresses, and see your orders on any phone. Free — no OTP needed.", "अपनी जानकारी और पते सेव करें, किसी भी फ़ोन पर अपने ऑर्डर देखें। मुफ़्त — OTP की ज़रूरत नहीं।")}</p>
        <form onSubmit={(e) => void submit(e)} className="form-s">
          {mode === "signup" && <>
            <label>{tx(lang, "Your name", "आपका नाम")}<input value={f.name} autoComplete="name" onChange={(e) => setF({ ...f, name: e.target.value })} /></label>
            <label>{tx(lang, "Mobile number", "मोबाइल नंबर")}<input value={f.phone} inputMode="tel" autoComplete="tel" onChange={(e) => setF({ ...f, phone: e.target.value })} /></label>
          </>}
          <label>Email<input type="email" value={f.email} autoComplete="email" onChange={(e) => setF({ ...f, email: e.target.value })} /></label>
          {mode !== "forgot" && <label>{tx(lang, "Password", "पासवर्ड")}<input type="password" value={f.password} minLength={6}
            autoComplete={mode === "signup" ? "new-password" : "current-password"} onChange={(e) => setF({ ...f, password: e.target.value })} /></label>}
          {msg && <p className={msg.ok ? "ok-s" : "bad-s"}>{msg.text}</p>}
          <button className="primary-s wide" disabled={busy}>{busy ? "…" : mode === "login" ? tx(lang, "Log in", "लॉगिन") : mode === "signup" ? tx(lang, "Create account", "खाता बनाएँ") : tx(lang, "Send reset link", "रीसेट लिंक भेजें")}</button>
        </form>
        <div className="row-s between small">
          {mode !== "login" ? <button className="link-s" onClick={() => { setMode("login"); setMsg(null); }}>{tx(lang, "Have an account? Log in", "खाता है? लॉगिन करें")}</button>
            : <button className="link-s" onClick={() => { setMode("signup"); setMsg(null); }}>{tx(lang, "New here? Create account", "नए हैं? खाता बनाएँ")}</button>}
          {mode === "login" && <button className="link-s" onClick={() => { setMode("forgot"); setMsg(null); }}>{tx(lang, "Forgot password?", "पासवर्ड भूल गए?")}</button>}
        </div>
      </div>
      <p className="muted-s small">{tx(lang, "You can also order without an account.", "बिना खाते के भी ऑर्डर कर सकते हैं।")}</p>
    </div>
  );
}

function NewPassword({ lang }: { lang: Lang }) {
  const [p, setP] = useState(""); const [msg, setMsg] = useState<string | null>(null);
  return (
    <div className="card-s2 form-s">
      <h2 className="sec" style={{ marginTop: 0 }}>{tx(lang, "Set a new password", "नया पासवर्ड बनाएँ")}</h2>
      <label>{tx(lang, "New password", "नया पासवर्ड")}<input type="password" minLength={6} value={p} autoComplete="new-password" onChange={(e) => setP(e.target.value)} /></label>
      {msg && <p className="small">{msg}</p>}
      <button className="primary-s wide" onClick={async () => {
        if (p.length < 6) return setMsg(authError(lang, "at least 6"));
        const { error } = await db.auth.updateUser({ password: p });
        setMsg(error ? authError(lang, error.message) : tx(lang, "Password changed ✓", "पासवर्ड बदल गया ✓"));
        if (!error) setTimeout(() => location.reload(), 1200);
      }}>{tx(lang, "Save password", "पासवर्ड सेव करें")}</button>
    </div>
  );
}

function Profile({ lang, c, reload }: { lang: Lang; c: Customer; reload: () => Promise<void> }) {
  const [p, setP] = useState({ full_name: c.profile?.full_name ?? "", phone: c.profile?.phone ?? "" });
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [edit, setEdit] = useState<Partial<Address> | null>(null);
  const [addrMsg, setAddrMsg] = useState<string | null>(null);

  async function saveProfile() {
    setMsg(null);
    if (p.full_name.trim().length < 2 || !validPhone(p.phone)) return setMsg({ ok: false, text: tx(lang, "Please enter your name and a valid 10-digit mobile number.", "नाम और सही 10 अंकों का मोबाइल नंबर लिखें।") });
    const { error } = await db.from("customer_profiles").upsert({ user_id: c.userId, full_name: p.full_name.trim(), phone: cleanPhone(p.phone) });
    if (error) return setMsg({ ok: false, text: error.message });
    store.set("ss_customer", { name: p.full_name.trim(), phone: cleanPhone(p.phone) });
    setMsg({ ok: true, text: tx(lang, "Saved ✓", "सेव हो गया ✓") });
    void reload();
  }
  async function saveAddress() {
    if (!edit) return;
    setAddrMsg(null);
    if ((edit.address ?? "").trim().length < 10) return setAddrMsg(tx(lang, "Please write the full address.", "पूरा पता लिखें।"));
    const row = { label: (edit.label ?? "").trim() || "Home", address: edit.address!.trim(), landmark: edit.landmark?.trim() || null, is_default: !!edit.is_default || c.addresses.length === 0 };
    const { error } = edit.id ? await db.from("customer_addresses").update(row).eq("id", edit.id) : await db.from("customer_addresses").insert(row);
    if (error) return setAddrMsg(/TOO_MANY/.test(error.message) ? tx(lang, "You can save up to 10 addresses.", "अधिकतम 10 पते सेव हो सकते हैं।") : error.message);
    setEdit(null); void reload();
  }

  return (
    <div className="stack">
      <div className="card-s2 form-s">
        <div className="row-s between"><h2 className="sec" style={{ margin: 0 }}>{tx(lang, "My profile", "मेरी प्रोफ़ाइल")}</h2><span className="muted-s small">{c.email}</span></div>
        {!c.profile && <p className="banner-s inline">{tx(lang, "Please complete your profile.", "कृपया अपनी प्रोफ़ाइल पूरी करें।")}</p>}
        <label>{tx(lang, "Your name", "आपका नाम")}<input value={p.full_name} autoComplete="name" onChange={(e) => setP({ ...p, full_name: e.target.value })} /></label>
        <label>{tx(lang, "Mobile number", "मोबाइल नंबर")}<input value={p.phone} inputMode="tel" autoComplete="tel" onChange={(e) => setP({ ...p, phone: e.target.value })} /></label>
        {msg && <p className={msg.ok ? "ok-s" : "bad-s"}>{msg.text}</p>}
        <button className="primary-s" onClick={() => void saveProfile()}>{tx(lang, "Save profile", "प्रोफ़ाइल सेव करें")}</button>
      </div>

      <div className="card-s2 form-s">
        <div className="row-s between"><h3 style={{ margin: 0 }}>{tx(lang, "Saved addresses", "सेव किए गए पते")}</h3>
          {!edit && c.addresses.length < 10 && <button className="ghost-s" onClick={() => setEdit({ label: "Home", address: "", landmark: "", is_default: c.addresses.length === 0 })}>+ {tx(lang, "Add", "जोड़ें")}</button>}</div>
        {!c.addresses.length && !edit && <p className="muted-s small">{tx(lang, "No saved address yet.", "अभी कोई पता सेव नहीं है।")}</p>}
        {c.addresses.map((a) => (
          <div key={a.id} className="addr-row">
            <div className="grow"><b>{a.label}</b>{a.is_default && <span className="tag-s"> {tx(lang, "Default", "डिफ़ॉल्ट")}</span>}
              <div className="small">{a.address}{a.landmark ? ` · ${a.landmark}` : ""}</div></div>
            <div className="row-s">
              {!a.is_default && <button className="link-s" onClick={async () => { await db.from("customer_addresses").update({ is_default: true }).eq("id", a.id); void reload(); }}>{tx(lang, "Make default", "डिफ़ॉल्ट बनाएँ")}</button>}
              <button className="link-s" onClick={() => setEdit(a)}>{tx(lang, "Edit", "बदलें")}</button>
              <button className="link-s bad-s" onClick={async () => { if (!confirm(tx(lang, "Delete this address?", "यह पता हटाएँ?"))) return; await db.from("customer_addresses").delete().eq("id", a.id); void reload(); }}>{tx(lang, "Delete", "हटाएँ")}</button>
            </div>
          </div>
        ))}
        {edit && (
          <div className="form-s addr-edit">
            <label>{tx(lang, "Label (Home, Hostel, Office…)", "नाम (घर, हॉस्टल, ऑफ़िस…)")}<input value={edit.label ?? ""} maxLength={30} onChange={(e) => setEdit({ ...edit, label: e.target.value })} /></label>
            <label>{tx(lang, "Full address", "पूरा पता")}<textarea value={edit.address ?? ""} maxLength={300} onChange={(e) => setEdit({ ...edit, address: e.target.value })} /></label>
            <label>{tx(lang, "Landmark (optional)", "लैंडमार्क (वैकल्पिक)")}<input value={edit.landmark ?? ""} maxLength={120} onChange={(e) => setEdit({ ...edit, landmark: e.target.value })} /></label>
            <label className="check-s"><input type="checkbox" checked={!!edit.is_default} onChange={(e) => setEdit({ ...edit, is_default: e.target.checked })} /><span>{tx(lang, "Use as default", "डिफ़ॉल्ट पता")}</span></label>
            {addrMsg && <p className="bad-s">{addrMsg}</p>}
            <div className="row-s"><button className="primary-s" onClick={() => void saveAddress()}>{tx(lang, "Save address", "पता सेव करें")}</button><button className="ghost-s" onClick={() => setEdit(null)}>{tx(lang, "Cancel", "रद्द")}</button></div>
          </div>
        )}
      </div>

      <div className="card-s2 row-s between">
        <a className="ghost-s ic-text" href="#/orders"><ReceiptText size={16} /> {tx(lang, "My orders", "मेरे ऑर्डर")}</a>
        <button className="ghost-s" onClick={async () => { await db.auth.signOut(); location.hash = "#/account"; }}>{tx(lang, "Log out", "लॉग आउट")}</button>
      </div>
    </div>
  );
}

/** Rating after a finished order (one per order). Saved in the restaurant's feedback table. */
export function FeedbackCard({ lang, token }: { lang: Lang; token: string }) {
  const done = store.get<string[]>("ss_rated", []);
  const [stars, setStars] = useState(0);
  const [text, setText] = useState("");
  const [publish, setPublish] = useState(true);
  const [state, setState] = useState<"idle" | "sent" | "error">(done.includes(token) ? "sent" : "idle");
  const [err, setErr] = useState<string | null>(null);
  if (state === "sent") return <div className="card-s2 small">{tx(lang, "Thank you for your rating ✓", "रेटिंग के लिए धन्यवाद ✓")}</div>;
  return (
    <div className="card-s2 form-s">
      <h3 style={{ margin: 0 }}>{tx(lang, "How was your food?", "खाना कैसा लगा?")}</h3>
      <div className="stars-s" role="radiogroup" aria-label="Rating">
        {[1, 2, 3, 4, 5].map((n) => <button key={n} role="radio" aria-checked={stars === n} className={n <= stars ? "on" : ""} onClick={() => setStars(n)}><Star size={30} fill={n <= stars ? "currentColor" : "none"} /></button>)}
      </div>
      <textarea placeholder={tx(lang, "Comments (optional)", "टिप्पणी (वैकल्पिक)")} maxLength={1000} value={text} onChange={(e) => setText(e.target.value)} />
      <label className="check-s"><input type="checkbox" checked={publish} onChange={(e) => setPublish(e.target.checked)} /><span>{tx(lang, "Show my comment (without my name) on the website", "मेरी टिप्पणी (बिना नाम के) वेबसाइट पर दिखाएँ")}</span></label>
      {err && <p className="bad-s">{err}</p>}
      <button className="primary-s" disabled={!stars} onClick={async () => {
        const { data, error } = await db.rpc("submit_feedback", { p: { orderToken: token, overall: stars, foodQuality: stars, comments: text || null, consentToPublish: publish } });
        const r = data as { ok?: boolean; error?: string; message?: string } | null;
        if (error || !r?.ok) {
          if (r?.error === "ALREADY_SUBMITTED") { store.set("ss_rated", [token, ...done].slice(0, 100)); setState("sent"); return; }
          setErr(r?.message ?? error?.message ?? "Error"); return;
        }
        store.set("ss_rated", [token, ...done].slice(0, 100)); setState("sent");
      }}>{tx(lang, "Submit rating", "रेटिंग भेजें")}</button>
    </div>
  );
}

/** Real customer ratings from the feedback table (only comments customers agreed to publish). */
export function ReviewsCard({ lang }: { lang: Lang }) {
  const [r, setR] = useState<{ count: number; average: number | null; recent: { overall: number; comments: string; at: string }[] } | null>(null);
  useEffect(() => { void db.rpc("public_reviews").then(({ data }) => data && setR(data)); }, []);
  if (!r) return null;
  return (
    <div className="card-s2">
      <h3>{tx(lang, "Customer ratings", "ग्राहकों की रेटिंग")}</h3>
      {r.count === 0 ? <p className="muted-s small">{tx(lang, "No ratings yet. Rate your order after it is completed.", "अभी कोई रेटिंग नहीं। ऑर्डर पूरा होने के बाद रेटिंग दें।")}</p>
        : <p><b>{r.average} ★</b> <span className="muted-s">· {r.count} {tx(lang, "ratings", "रेटिंग")}</span></p>}
      {r.recent.map((x, i) => <div key={i} className="review-s"><b>{"★".repeat(x.overall)}</b> <span className="small">{x.comments}</span>
        <div className="muted-s small">{new Date(x.at).toLocaleDateString("en-IN")}</div></div>)}
    </div>
  );
}
