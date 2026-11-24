import { appBridge } from "./native";
import { useCallback, useEffect, useState } from "react";
import { friendlyError, supabase } from "./supabase";

export type Tender = "CASH" | "UPI" | "CARD" | "BANK" | "CREDIT";
export const TENDER_LABEL: Record<Tender, string> = { CASH: "Cash", UPI: "UPI", CARD: "Card", BANK: "Bank", CREDIT: "Khata (credit)" };

export async function rpc<T = unknown>(fn: string, args?: Record<string, unknown>): Promise<T> {
  const { data, error } = await supabase.rpc(fn, args);
  if (error) throw new Error(friendlyError(error.message));
  return data as T;
}

/** Loads an RPC result and reloads when deps change. */
export function useRpc<T>(fn: string, args: Record<string, unknown> | undefined, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const load = useCallback(async () => {
    setLoading(true);
    try { setData(await rpc<T>(fn, args)); setError(null); }
    catch (e) { setError((e as Error).message); }
    finally { setLoading(false); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fn, JSON.stringify(args), ...deps]);
  useEffect(() => { void load(); }, [load]);
  return { data, error, loading, reload: load };
}

export async function selectAll<T>(table: string, order = "id", filter?: (q: ReturnType<typeof supabase.from>) => unknown): Promise<T[]> {
  let q = supabase.from(table).select("*");
  if (filter) q = filter(q as never) as typeof q;
  const { data, error } = await q.order(order);
  if (error) throw new Error(friendlyError(error.message));
  return data as T[];
}

export function uuid(): string {
  return crypto.randomUUID();
}

/** UPI deep link used for the dynamic payment QR (payment is confirmed by staff, never assumed). */
export function upiLink(upiId: string, payee: string, amountPaise: number, note: string) {
  const p = new URLSearchParams({ pa: upiId, pn: payee, am: (amountPaise / 100).toFixed(2), cu: "INR", tn: note });
  return `upi://pay?${p.toString()}`;
}

export function printHtml(title: string, bodyHtml: string, widthMm?: number) {
  const page = widthMm ? `@page { size: ${widthMm}mm auto; margin: 2mm; } body { width: ${widthMm - 4}mm; }` : "@page { margin: 12mm; }";
  const head = `<!doctype html><html><head><meta charset="utf-8"><title>${esc(title)}</title><style>
    ${page}
    body { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: ${widthMm ? (widthMm < 70 ? "10px" : "12px") : "13px"}; color: #000; margin: 0 auto; }
    h1,h2,h3 { margin: 4px 0; text-align: center; } table { width: 100%; border-collapse: collapse; }
    td, th { padding: 2px 0; vertical-align: top; } .r { text-align: right; } .c { text-align: center; }
    hr { border: 0; border-top: 1px dashed #000; } .big { font-size: 1.3em; font-weight: bold; }
    .doc td, .doc th { border-bottom: 1px solid #ddd; padding: 4px; font-family: system-ui, sans-serif; }
  </style></head><body>${bodyHtml}`;
  const bridge = appBridge();
  if (bridge) { bridge.printHtml(title, head + "</body></html>"); return; } // Android app: native print dialog / printer
  const w = window.open("", "_blank", "width=420,height=640");
  if (!w) return;
  // No inline script in the popup (the Content-Security-Policy blocks it): print from here once it has rendered
  w.document.write(head + "</body></html>");
  w.document.close();
  const go = () => { w.focus(); w.print(); };
  if (w.document.readyState === "complete") setTimeout(go, 250); else w.addEventListener("load", go, { once: true });
}

export const esc = (s: unknown) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
