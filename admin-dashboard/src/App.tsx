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
import AccountingPage from "./pages/AccountingPage";
import ExpensesPage from "./pages/ExpensesPage";
import InsightsPage from "./pages/InsightsPage";
import InventoryPage from "./pages/InventoryPage";
import PartiesPage from "./pages/PartiesPage";
import PayrollPage from "./pages/PayrollPage";
import PosPage from "./pages/PosPage";
import TablesPage from "./pages/TablesPage";
import StaffPage from "./pages/StaffPage";
import type { Role } from "./lib/types";

const FOH: Role[] = ["OWNER", "MANAGER", "STAFF", "CASHIER", "WAITER"];
const MGMT: Role[] = ["OWNER", "MANAGER"];
const FIN: Role[] = ["OWNER", "MANAGER", "ACCOUNTANT"];

const NAV: { to: string; label: string; roles: Role[] }[] = [
  { to: "/", label: "Dashboard", roles: [...FOH, "ACCOUNTANT"] },
  { to: "/pos", label: "POS / Billing", roles: FOH },
  { to: "/orders", label: "Orders", roles: FOH },
  { to: "/kitchen", label: "Kitchen", roles: [...FOH, "KITCHEN"] },
  { to: "/tables", label: "Tables", roles: FOH },
  { to: "/menu", label: "Menu", roles: [...FOH, "KITCHEN"] },
  { to: "/inventory", label: "Inventory", roles: [...FIN, "KITCHEN"] },
  { to: "/accounting", label: "Accounts", roles: FIN },
  { to: "/expenses", label: "Expenses", roles: [...FIN, "STAFF", "CASHIER"] },
  { to: "/khata", label: "Khata", roles: FIN },
  { to: "/suppliers", label: "Suppliers", roles: FIN },
  { to: "/payroll", label: "Payroll", roles: FIN },
  { to: "/reports", label: "Reports", roles: FIN },
  { to: "/insights", label: "Insights", roles: FIN },
  { to: "/offers", label: "Offers", roles: MGMT },
  { to: "/feedback", label: "Feedback", roles: MGMT },
  { to: "/qr", label: "QR codes", roles: MGMT },
  { to: "/staff", label: "Staff logins", roles: MGMT },
  { to: "/settings", label: "Settings", roles: ["OWNER"] },
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

  const home = role === "KITCHEN" ? "/kitchen" : role === "ACCOUNTANT" ? "/accounting" : "/";

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
          <Route path="/" element={role === "KITCHEN" ? <Navigate to="/kitchen" /> : role === "ACCOUNTANT" ? <Navigate to="/accounting" /> : <DashboardPage />} />
          <Route path="/pos" element={<Guard roles={FOH}><PosPage /></Guard>} />
          <Route path="/tables" element={<Guard roles={FOH}><TablesPage /></Guard>} />
          <Route path="/inventory" element={<Guard roles={[...FIN, "KITCHEN"]}><InventoryPage /></Guard>} />
          <Route path="/accounting" element={<Guard roles={FIN}><AccountingPage /></Guard>} />
          <Route path="/expenses" element={<Guard roles={[...FIN, "STAFF", "CASHIER"]}><ExpensesPage /></Guard>} />
          <Route path="/khata" element={<Guard roles={FIN}><PartiesPage kind="CUSTOMER" /></Guard>} />
          <Route path="/suppliers" element={<Guard roles={FIN}><PartiesPage kind="SUPPLIER" /></Guard>} />
          <Route path="/payroll" element={<Guard roles={FIN}><PayrollPage /></Guard>} />
          <Route path="/insights" element={<Guard roles={FIN}><InsightsPage /></Guard>} />
          <Route path="/orders" element={<Guard roles={FOH}><OrdersPage /></Guard>} />
          <Route path="/kitchen" element={<KitchenPage />} />
          <Route path="/menu" element={<MenuPage />} />
          <Route path="/offers" element={<Guard roles={["OWNER", "MANAGER"]}><OffersPage /></Guard>} />
          <Route path="/reports" element={<Guard roles={FIN}><ReportsPage /></Guard>} />
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
