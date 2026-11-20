import { useState, type FormEvent } from "react";
import { supabase } from "../lib/supabase";

export default function LoginPage() {
  const [mode, setMode] = useState<"login" | "signup" | "reset">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      if (mode === "login") {
        const { error } = await supabase.auth.signInWithPassword({ email, password });
        if (error) throw error;
      } else if (mode === "signup") {
        if (password.length < 10) throw new Error("Use a password of at least 10 characters.");
        const { error } = await supabase.auth.signUp({
          email, password,
          options: { data: { display_name: name }, emailRedirectTo: window.location.origin + window.location.pathname },
        });
        if (error) throw error;
        setMsg({ ok: true, text: "Account created. Confirm your email, then ask the owner to activate your account." });
      } else {
        const { error } = await supabase.auth.resetPasswordForEmail(email, {
          redirectTo: window.location.origin + window.location.pathname,
        });
        if (error) throw error;
        setMsg({ ok: true, text: "If the email exists, a reset link has been sent." });
      }
    } catch (err) {
      setMsg({ ok: false, text: (err as Error).message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="center-page">
      <form className="card narrow" onSubmit={submit}>
        <div className="row" style={{ marginBottom: 12 }}>
          <img src="./favicon.svg" alt="" width={40} height={40} />
          <div>
            <h1 style={{ margin: 0 }}>Swad Sangam</h1>
            <div className="muted small">Restaurant admin</div>
          </div>
        </div>
        {mode === "signup" && (
          <label>Your name<input value={name} onChange={(e) => setName(e.target.value)} required /></label>
        )}
        <label>Email<input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
        {mode !== "reset" && (
          <label>Password
            <input type="password" autoComplete={mode === "login" ? "current-password" : "new-password"}
              value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
        )}
        <button type="submit" disabled={busy} style={{ width: "100%" }}>
          {mode === "login" ? "Sign in" : mode === "signup" ? "Create staff account" : "Send reset link"}
        </button>
        {msg && <p className={msg.ok ? "success" : "error"}>{msg.text}</p>}
        <div className="spread small" style={{ marginTop: 12 }}>
          {mode !== "login" && <button type="button" className="link" onClick={() => setMode("login")}>Back to sign in</button>}
          {mode === "login" && <button type="button" className="link" onClick={() => setMode("signup")}>New staff? Create account</button>}
          {mode === "login" && <button type="button" className="link" onClick={() => setMode("reset")}>Forgot password</button>}
        </div>
      </form>
    </div>
  );
}
