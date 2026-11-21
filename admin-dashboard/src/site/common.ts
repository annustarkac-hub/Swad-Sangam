import { createClient } from "@supabase/supabase-js";

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const key = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;

/** Customer-site client. Its own storage key, so a customer login never mixes with a staff login in admin.html. */
export const db = createClient(url ?? "https://example.invalid", key ?? "missing", {
  auth: { persistSession: true, autoRefreshToken: true, storageKey: "ss-customer-auth", detectSessionInUrl: true },
});

export const store = {
  get<T>(k: string, d: T): T { try { const v = localStorage.getItem(k); return v ? (JSON.parse(v) as T) : d; } catch { return d; } },
  set(k: string, v: unknown) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* private mode */ } },
};

export interface Profile { user_id: string; full_name: string; phone: string }
export interface Address { id: number; label: string; address: string; landmark: string | null; is_default: boolean }
export interface Customer { userId: string; email: string; profile: Profile | null; addresses: Address[] }

export const validPhone = (p: string) => /^[6-9]\d{9}$/.test(p.replace(/\D/g, "").slice(-10));
export const cleanPhone = (p: string) => p.replace(/\D/g, "").slice(-10);
