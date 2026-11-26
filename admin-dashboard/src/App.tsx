import {
  Armchair, BarChart3, CalendarCheck, ChefHat, Landmark, LayoutDashboard, LogOut, Menu, MessageSquare, Moon, Package, QrCode,
  Bell, Bike, Receipt, ScrollText, Settings, ShoppingBag, Sun, TicketPercent, TrendingUp, Truck, UserCog, Users, UtensilsCrossed, Wallet,
  type LucideIcon,
} from "lucide-react";
import { useEffect, useState } from "react";
import ErrorBoundary from "./components/ErrorBoundary";
import { Link, Navigate, NavLink, Route, Routes, useLocation } from "react-router-dom";
import { useLiveOrders } from "./components/useOrders";
import { playAlert, unlockAudio } from "./lib/alert";
import DeliveryPage from "./pages/DeliveryPage";
import { appBridge } from "./lib/native";
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
/** Roles that see new online orders (alert strip + sound on every page) */
const ALERT_ROLES: Role[] = ["OWNER", "MANAGER", "STAFF", "CASHIER"];

type NavItem = { to: string; label: string; short?: string; roles: Role[]; group: string; icon: LucideIcon; bottom?: boolean };
const GROUPS = ["Operations", "Menu & stock", "Customers", "Finance", "Team", "System"];
const NAV: NavItem[] = [
  { to: "/", label: "Dashboard", short: "Home", roles: [...FOH, "ACCOUNTANT"], group: "Operations", icon: LayoutDashboard, bottom: true },
  { to: "/pos", label: "POS / Billing", short: "POS", roles: FOH, group: "Operations", icon: Receipt, bottom: true },
  { to: "/orders", label: "Online orders", short: "Orders", roles: FOH, group: "Operations", icon: ShoppingBag, bottom: true },
  { to: "/delivery", label: "My deliveries", short: "Deliveries", roles: ["DELIVERY"], group: "Operations", icon: Bike, bottom: true },
  { to: "/kitchen", label: "Kitchen display", short: "Kitchen", roles: [...FOH, "KITCHEN"], group: "Operations", icon: ChefHat, bottom: true },
  { to: "/tables", label: "Tables & bookings", short: "Tables", roles: FOH, group: "Operations", icon: Armchair, bottom: true },
  { to: "/menu", label: "Menu", roles: [...FOH, "KITCHEN"], group: "Menu & stock", icon: UtensilsCrossed },
  { to: "/inventory", label: "Inventory & recipes", roles: [...FIN, "KITCHEN"], group: "Menu & stock", icon: Package },
  { to: "/khata", label: "Customers & khata", roles: FIN, group: "Customers", icon: Users },
  { to: "/offers", label: "Offers & coupons", roles: MGMT, group: "Customers", icon: TicketPercent },
  { to: "/feedback", label: "Feedback", roles: MGMT, group: "Customers", icon: MessageSquare },
  { to: "/qr", label: "QR codes", roles: MGMT, group: "Customers", icon: QrCode },
  { to: "/accounting", label: "Accounts", roles: FIN, group: "Finance", icon: Landmark },
  { to: "/expenses", label: "Expenses", roles: [...FIN, "CASHIER"], group: "Finance", icon: Wallet },
  { to: "/suppliers", label: "Suppliers", roles: FIN, group: "Finance", icon: Truck },
  { to: "/reports", label: "Sales reports", roles: FIN, group: "Finance", icon: BarChart3 },
  { to: "/insights", label: "Profit insights", roles: FIN, group: "Finance", icon: TrendingUp },
  { to: "/payroll", label: "Attendance & payroll", roles: FIN, group: "Team", icon: CalendarCheck },
  { to: "/staff", label: "Staff logins", roles: MGMT, group: "Team", icon: UserCog },
  { to: "/settings", label: "Settings", roles: ["OWNER"], group: "System", icon: Settings },
  { to: "/audit", label: "Audit log", roles: ["OWNER"], group: "System", icon: ScrollText },
];

export default function App() {
  const { session, profile, role, loading, signOut, refreshProfile } = useAuth();
  const [drawer, setDrawer] = useState(false);
  const [theme, setTheme] = useState<string>(() => { try { return localStorage.getItem("ss_theme") ?? (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"); } catch { return "light"; } });
  const location = useLocation();
  // Mark horizontally scrolling tab rows so only those show the "more →" fade
  useEffect(() => {
    const mark = () => document.querySelectorAll<HTMLElement>(".tabs").forEach((el) => {
      el.classList.toggle("scrolls", el.scrollWidth > el.clientWidth + 2);
      el.classList.toggle("at-end", el.scrollLeft + el.clientWidth >= el.scrollWidth - 2);
    });
    let raf = 0;
    const schedule = () => { cancelAnimationFrame(raf); raf = requestAnimationFrame(mark); };
    const mo = new MutationObserver(schedule);
    mo.observe(document.body, { childList: true, subtree: true });
    document.addEventListener("scroll", schedule, true);
    window.addEventListener("resize", schedule);
    schedule();
    return () => { mo.disconnect(); document.removeEventListener("scroll", schedule, true); window.removeEventListener("resize", schedule); cancelAnimationFrame(raf); };
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try { localStorage.setItem("ss_theme", theme); } catch { /* ignore */ }
    // Android app: paint the system bars in the same colours as the header / bottom bar
    try { appBridge()?.setChrome?.(theme === "dark" ? "dark" : "light"); } catch { /* older app */ }
  }, [theme]);
  const toggleTheme = () => setTheme(theme === "dark" ? "light" : "dark");
  const current = NAV.find((n) => (n.to === "/" ? location.pathname === "/" : location.pathname.startsWith(n.to)));

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

  const home = role === "KITCHEN" ? "/kitchen" : role === "ACCOUNTANT" ? "/accounting" : role === "DELIVERY" ? "/delivery" : "/";
  const alerts = ALERT_ROLES.includes(role);
  const navItems = NAV.filter((n) => can(role, ...n.roles));
  const bottomItems = navItems.filter((n) => n.bottom).slice(0, 4);
  // "More" / the menu button only when the drawer holds something the bottom bar does not (a rider has one screen)
  const hasMore = navItems.length > bottomItems.length;

  return (
    <div className="shell">
      <aside className={"sidebar" + (drawer ? " open" : "")}>
        <div className="brand"><img className="logo logo-img" src={`${import.meta.env.BASE_URL}logo-mark.png`} alt="" width={34} height={34} /><div>Swad Sangam<small>Restaurant OS</small></div></div>
        {GROUPS.map((g) => {
          const items = NAV.filter((n) => n.group === g && can(role, ...n.roles));
          if (!items.length) return null;
          return (
            <div key={g}>
              <div className="nav-group">{g}</div>
              {items.map((n) => (
                <NavLink key={n.to} to={n.to} end={n.to === "/"} className="nav-link" onClick={() => setDrawer(false)}>
                  <n.icon /> {n.label}
                </NavLink>
              ))}
            </div>
          );
        })}
      </aside>
      <div className={"drawer-backdrop" + (drawer ? " open" : "")} onClick={() => setDrawer(false)} />
      <div className="main">
        <header className="topbar">
          {hasMore && <button className="ghost icon menu-btn" aria-label="Menu" onClick={() => setDrawer(true)}><Menu /></button>}
          <div className="title">{current?.label ?? "Swad Sangam"}</div>
          <button className="ghost icon" aria-label="Toggle dark mode" onClick={toggleTheme}>{theme === "dark" ? <Sun /> : <Moon />}</button>
          <div className="user-chip"><span className="badge">{role}</span><span className="email">{profile?.email}</span></div>
          <button className="ghost icon" aria-label="Sign out" title="Sign out" onClick={() => void signOut()}><LogOut /></button>
        </header>
        {alerts && <NewOrderStrip onOrdersPage={location.pathname.startsWith("/orders")} />}
        <main className="content">
        <ErrorBoundary resetKey={location.pathname}>
        <Routes>
          <Route path="/" element={role === "KITCHEN" ? <Navigate to="/kitchen" /> : role === "ACCOUNTANT" ? <Navigate to="/accounting" /> : role === "DELIVERY" ? <Navigate to="/delivery" /> : <DashboardPage />} />
          <Route path="/delivery" element={<Guard roles={["DELIVERY"]}><DeliveryPage /></Guard>} />
          <Route path="/pos" element={<Guard roles={FOH}><PosPage /></Guard>} />
          <Route path="/tables" element={<Guard roles={FOH}><TablesPage /></Guard>} />
          <Route path="/inventory" element={<Guard roles={[...FIN, "KITCHEN"]}><InventoryPage /></Guard>} />
          <Route path="/accounting" element={<Guard roles={FIN}><AccountingPage /></Guard>} />
          <Route path="/expenses" element={<Guard roles={[...FIN, "CASHIER"]}><ExpensesPage /></Guard>} />
          <Route path="/khata" element={<Guard roles={FIN}><PartiesPage kind="CUSTOMER" /></Guard>} />
          <Route path="/suppliers" element={<Guard roles={FIN}><PartiesPage kind="SUPPLIER" /></Guard>} />
          <Route path="/payroll" element={<Guard roles={FIN}><PayrollPage /></Guard>} />
          <Route path="/insights" element={<Guard roles={FIN}><InsightsPage /></Guard>} />
          <Route path="/orders" element={<Guard roles={FOH}><OrdersPage /></Guard>} />
          <Route path="/kitchen" element={<Guard roles={[...FOH, "KITCHEN"]}><KitchenPage /></Guard>} />
          <Route path="/menu" element={<Guard roles={[...FOH, "KITCHEN"]}><MenuPage /></Guard>} />
          <Route path="/offers" element={<Guard roles={["OWNER", "MANAGER"]}><OffersPage /></Guard>} />
          <Route path="/reports" element={<Guard roles={FIN}><ReportsPage /></Guard>} />
          <Route path="/feedback" element={<Guard roles={["OWNER", "MANAGER"]}><FeedbackPage /></Guard>} />
          <Route path="/settings" element={<Guard roles={["OWNER"]}><SettingsPage /></Guard>} />
          <Route path="/staff" element={<Guard roles={["OWNER", "MANAGER"]}><StaffPage /></Guard>} />
          <Route path="/qr" element={<Guard roles={["OWNER", "MANAGER"]}><QrPage /></Guard>} />
          <Route path="/audit" element={<Guard roles={["OWNER"]}><AuditPage /></Guard>} />
          <Route path="*" element={<Navigate to={home} />} />
        </Routes>
        </ErrorBoundary>
        </main>
      </div>
      <nav className="bottom-bar">
        {bottomItems.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.to === "/"}><n.icon />{n.short ?? n.label}</NavLink>
        ))}
        {hasMore && <button onClick={() => setDrawer(true)}><Menu />More</button>}
      </nav>
    </div>
  );
}

function Guard({ roles, children }: { roles: Role[]; children: JSX.Element }) {
  const { role } = useAuth();
  if (can(role, ...roles)) return children;
  // Riders open the Staff app (which starts on POS) — send them to their own screen instead of an error
  if (role === "DELIVERY") return <Navigate to="/delivery" replace />;
  if (role === "KITCHEN") return <Navigate to="/kitchen" replace />;
  return <p>You do not have access to this page.</p>;
}

/**
 * New online orders on EVERY page (POS, tables, dashboard…): sound + native notification + a strip that links to Orders.
 * Before this, alerts only worked while the Orders page was open.
 */
function NewOrderStrip({ onOrdersPage }: { onOrdersPage: boolean }) {
  const { orders } = useLiveOrders(["PLACED"], { alertOnNew: true });
  useEffect(() => {
    const unlock = () => unlockAudio();
    window.addEventListener("pointerdown", unlock, { once: true });
    return () => window.removeEventListener("pointerdown", unlock);
  }, []);
  // Escalation: while any new order has waited 2+ minutes, beep again every minute (on every page)
  const overdue = orders.filter((o) => Date.now() - new Date(o.createdAt).getTime() > 120000).length;
  useEffect(() => {
    if (!overdue) return;
    const t = setInterval(() => playAlert(), 60000);
    return () => clearInterval(t);
  }, [overdue]);
  if (orders.length === 0 || onOrdersPage) return null;
  return (
    <Link to="/orders" className="alert-strip" role="status">
      <Bell size={18} aria-hidden />
      <span className="alert-text"><b>{orders.length} new order{orders.length > 1 ? "s" : ""}</b>{overdue > 0 ? ` · ${overdue} waiting 2+ min` : ""}</span>
      <span className="alert-cta">Open</span>
    </Link>
  );
}
