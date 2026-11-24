import { useEffect, useRef, useState } from "react";

/**
 * In-app replacements for window.confirm() / window.prompt(), which look broken (or do nothing)
 * inside the Android WebView apps. Usage: `if (!(await askConfirm("Delete?"))) return;`
 * Mount <DialogHost /> once near the root of each app (admin and customer site).
 */
type Req =
  | { kind: "confirm"; message: string; ok: string; cancel: string; danger?: boolean; resolve: (v: boolean) => void }
  | { kind: "prompt"; message: string; ok: string; cancel: string; placeholder?: string; resolve: (v: string | null) => void };

let push: ((r: Req) => void) | null = null;

export function askConfirm(message: string, opts: { ok?: string; cancel?: string; danger?: boolean } = {}): Promise<boolean> {
  return new Promise((resolve) => {
    if (!push) { resolve(window.confirm(message)); return; }
    push({ kind: "confirm", message, ok: opts.ok ?? "OK", cancel: opts.cancel ?? "Cancel", danger: opts.danger, resolve });
  });
}

export function askText(message: string, opts: { ok?: string; cancel?: string; placeholder?: string } = {}): Promise<string | null> {
  return new Promise((resolve) => {
    if (!push) { resolve(window.prompt(message)); return; }
    push({ kind: "prompt", message, ok: opts.ok ?? "OK", cancel: opts.cancel ?? "Cancel", placeholder: opts.placeholder, resolve });
  });
}

export function DialogHost() {
  const [req, setReq] = useState<Req | null>(null);
  const [text, setText] = useState("");
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => {
    push = (r) => { setText(""); setReq(r); };
    return () => { push = null; };
  }, []);
  useEffect(() => { if (req?.kind === "prompt") input.current?.focus(); }, [req]);
  if (!req) return null;
  const close = (ok: boolean) => {
    if (req.kind === "confirm") req.resolve(ok);
    else req.resolve(ok ? text : null);
    setReq(null);
  };
  return (
    <div className="ss-dialog-backdrop" role="presentation" onClick={() => close(false)}>
      <div className="ss-dialog" role="alertdialog" aria-modal="true" aria-label={req.message} onClick={(e) => e.stopPropagation()}
        onKeyDown={(e) => { if (e.key === "Escape") close(false); }}>
        <p>{req.message}</p>
        {req.kind === "prompt" && (
          <input ref={input} value={text} placeholder={req.placeholder} onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") close(true); }} />
        )}
        <div className="ss-dialog-actions">
          <button type="button" className="ss-dialog-cancel" onClick={() => close(false)}>{req.cancel}</button>
          <button type="button" className={"ss-dialog-ok" + (req.kind === "confirm" && req.danger ? " danger" : "")} autoFocus={req.kind === "confirm"}
            onClick={() => close(true)}>{req.ok}</button>
        </div>
      </div>
    </div>
  );
}
