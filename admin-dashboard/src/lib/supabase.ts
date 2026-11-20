import { createClient } from "@supabase/supabase-js";

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const key = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;

export const isConfigured = Boolean(url && key);

export const supabase = createClient(url ?? "https://example.invalid", key ?? "missing", {
  auth: { persistSession: true, autoRefreshToken: true },
});

/** Unwraps a Supabase response, throwing a readable error. */
export function unwrap<T>(res: { data: T | null; error: { message: string } | null }): T {
  if (res.error) throw new Error(friendlyError(res.error.message));
  return res.data as T;
}

export function friendlyError(message: string): string {
  if (message.includes("NOT_AUTHORIZED") || message.includes("permission denied") || message.includes("row-level security"))
    return "You do not have permission for this action.";
  if (message.includes("INVALID_TRANSITION")) return "That status change is not allowed: " + message.split("INVALID_TRANSITION:")[1];
  if (message.includes("REASON_REQUIRED")) return "Please give a reason.";
  if (message.includes("LAST_OWNER")) return "Add another active owner before changing your own access.";
  if (message.includes("ORDER_AMOUNTS_IMMUTABLE")) return "Order amounts cannot be edited. Record an adjustment instead.";
  return message;
}
