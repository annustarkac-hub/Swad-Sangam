import { Navigate, NavLink, Route, Routes } from "react-router-dom";
import { can, useAuth } from "./auth";
import { isConfigured } from "./lib/supabase";
import AuditPage from "./pages/AuditPage";
import DashboardPage from "./pages/DashboardPage";
import FeedbackPage from "./pages/FeedbackPage";
import KitchenPage from "./pages/KitchenPage";
import LoginPage from "./pages/LoginPage";
import MenuPage from "./pages/MenuPage";
import OffersPage from "./pages/OffersPage";
import OrdersPage from "./pages/OrdersPage";
import QrPage from "./pages/QrPage";
import ReportsPage from "./pages/ReportsPage";
import SettingsPage from "./pages/SettingsPage";
import StaffPage from "./pages/StaffPage";
import type { Role } from "./lib/types";

const NAV: { to: string; label: string; roles: Role[] }[] = [
  { to: "/", label: "Dashboard", roles: ["OWNER", "MANAGER", "STAFF"] },
  { to: "/orders", label: "Orders", roles: ["OWNER", "MANAGER", "STAFF"] },
  { to: "/kitchen", label: "Kitchen", roles: ["OWNER", "MANAGER", "STAFF", "KITCHEN"] },
  { to: "/menu", label: "Menu", roles: ["OWNER", "MANAGER", "STAFF", "KITCHEN"] },
  { to: "/offers", label: "Offers", roles: ["OWNER", "MANAGER"] },
  { to: "/reports", label: "Reports", roles: ["OWNER", "MANAGER"] },
  { to: "/feedback", label: "Feedback", roles: ["OWNER", "MANAGER"] },
  { to: "/settings", label: "Settings", roles: ["OWNER"] },
  { to: "/staff", label: "Staff", roles: ["OWNER", "MANAGER"] },
  { to: "/qr", label: "QR codes", roles: ["OWNER", "MANAGER"] },
  { to: "/audit", label: "Audit log", roles: ["OWNER"] },
];

export default function App() {
  const { session, profile, role, loading, signOut, refreshProfile } = useAuth();

  if (!isConfigured) {
    return (
      <div className="center-page">
        <div className="card narrow">
          <h1>Setup needed</h1>
          <p>
            The dashboard is not connected to the database yet. Set <code>VITE_SUPABASE_URL</code> and{" "}
            <code>VITE_SUPABASE_ANON_KEY</code> (see <b>docs/SETUP_GUIDE.md</b>, step 4).
          </p>
        </div>
      </div>
    );
  }
  if (loading) return <div className="center-page">Loading…</div>;
  if (!session) return <LoginPage />;
  if (!role) {
    return (
      <div className="center-page">
        <div className="card narrow">
          <h1>Waiting for approval</h1>
          <p>
            Your account <b>{profile?.email ?? session.user.email}</b> has been created. The restaurant owner must
            activate it and assign a role before you can use the dashboard.
          </p>
          <div className="row">
            <button onClick={() => void refreshProfile()}>Check again</button>
            <button className="secondary" onClick={() => void signOut()}>Sign out</button>
          </div>
        </div>
      </div>
    );
  }

  const home = role === "KITCHEN" ? "/kitchen" : "/";

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <img src="./favicon.svg" alt="" width={28} height={28} /> Swad Sangam <span className="muted">Admin</span>
        </div>
        <nav className="nav">
          {NAV.filter((n) => can(role, ...n.roles)).map((n) => (
            <NavLink key={n.to} to={n.to} end={n.to === "/"}>
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="user">
          <span className="badge">{role}</span> <span className="muted small">{profile?.email}</span>
          <button className="link" onClick={() => void signOut()}>Sign out</button>
        </div>
      </header>
      <main className="content">
        <Routes>
          <Route path="/" element={role === "KITCHEN" ? <Navigate to="/kitchen" /> : <DashboardPage />} />
          <Route path="/orders" element={<Guard roles={["OWNER", "MANAGER", "STAFF"]}><OrdersPage /></Guard>} />
          <Route path="/kitchen" element={<KitchenPage />} />
          <Route path="/menu" element={<MenuPage />} />
          <Route path="/offers" element={<Guard roles={["OWNER", "MANAGER"]}><OffersPage /></Guard>} />
          <Route path="/reports" element={<Guard roles={["OWNER", "MANAGER"]}><ReportsPage /></Guard>} />
          <Route path="/feedback" element={<Guard roles={["OWNER", "MANAGER"]}><FeedbackPage /></Guard>} />
          <Route path="/settings" element={<Guard roles={["OWNER"]}><SettingsPage /></Guard>} />
          <Route path="/staff" element={<Guard roles={["OWNER", "MANAGER"]}><StaffPage /></Guard>} />
          <Route path="/qr" element={<Guard roles={["OWNER", "MANAGER"]}><QrPage /></Guard>} />
          <Route path="/audit" element={<Guard roles={["OWNER"]}><AuditPage /></Guard>} />
          <Route path="*" element={<Navigate to={home} />} />
        </Routes>
      </main>
    </div>
  );
}

function Guard({ roles, children }: { roles: Role[]; children: JSX.Element }) {
  const { role } = useAuth();
  return can(role, ...roles) ? children : <p>You do not have access to this page.</p>;
}
