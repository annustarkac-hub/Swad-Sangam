import type { Session } from "@supabase/supabase-js";
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { supabase } from "./lib/supabase";
import type { Role, StaffProfile } from "./lib/types";

interface AuthState {
  session: Session | null;
  profile: StaffProfile | null;
  loading: boolean;
  role: Role | null;
  refreshProfile: () => Promise<void>;
  /** Re-verifies the password before sensitive operations (PRD §12.2). */
  reauthenticate: (password: string) => Promise<boolean>;
  signOut: () => Promise<void>;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [profile, setProfile] = useState<StaffProfile | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshProfile = useCallback(async () => {
    const { data } = await supabase.rpc("my_staff_profile");
    setProfile((data as StaffProfile | null) ?? null);
  }, []);

  useEffect(() => {
    supabase.auth.getSession().then(async ({ data }) => {
      setSession(data.session);
      if (data.session) await refreshProfile();
      setLoading(false);
    });
    const { data: sub } = supabase.auth.onAuthStateChange((_event, s) => {
      setSession(s);
      if (s) void refreshProfile();
      else setProfile(null);
    });
    return () => sub.subscription.unsubscribe();
  }, [refreshProfile]);

  const reauthenticate = useCallback(
    async (password: string) => {
      const email = session?.user.email;
      if (!email) return false;
      const { error } = await supabase.auth.signInWithPassword({ email, password });
      return !error;
    },
    [session],
  );

  const signOut = useCallback(async () => {
    await supabase.auth.signOut();
  }, []);

  const role = profile?.is_active ? profile.role : null;

  return (
    <Ctx.Provider value={{ session, profile, loading, role, refreshProfile, reauthenticate, signOut }}>
      {children}
    </Ctx.Provider>
  );
}

export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error("useAuth outside provider");
  return v;
}

export function can(role: Role | null, ...allowed: Role[]): boolean {
  return role != null && allowed.includes(role);
}
