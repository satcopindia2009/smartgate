import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { SCHOOL_NAME } from "../lib/constants";
import { AuditPanel, AuditProvider } from "./AuditContext";
import { IconBlacklist, IconGates, IconHistory, IconHours, IconLive, IconPeople, IconPickupHistory, IconReports, IconShield } from "./Icons";

const NAV = [
  { to: "/live", label: "Live", icon: <IconLive /> },
  { to: "/history", label: "History", icon: <IconHistory /> },
  { to: "/pickup", label: "Students & lists", icon: <IconPeople /> },
  { to: "/pickup-history", label: "Pickup history", icon: <IconPickupHistory /> },
  { to: "/access-rules", label: "Access rules", icon: <IconHours /> },
  { to: "/blacklist", label: "Blacklist", icon: <IconBlacklist /> },
  { to: "/gates", label: "Gates", icon: <IconGates /> },
  { to: "/reports", label: "Reports", icon: <IconReports /> },
];

export function Layout() {
  const { user, source, logout } = useAuth();
  const roleLabel = user?.role === "security_head" ? "Security Head" : "Office Admin";

  return (
    <div className="admin-page">
      <div className="demo-watermark">DEMO</div>
      <aside className="sidebar">
        <div className="logo">
          <div className="logo-mark" aria-hidden="true">
            <IconShield />
          </div>
          <div>
            Smart Visitor
            <small>{SCHOOL_NAME}</small>
          </div>
        </div>
        <nav className="nav" aria-label="Admin">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? "active" : "")}>
              {item.icon}
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-role">
          <div className="role-label">{roleLabel}</div>
          <div className="signed-as">{user?.displayName}</div>
          <div className={`source-pill ${source}`}>{source === "api" ? "API /v1" : "Fixtures"}</div>
          <button type="button" className="btn btn-ghost btn-sm logout-btn" onClick={logout}>
            Sign out
          </button>
        </div>
        <div className="sidebar-foot">Day-1 MVP + P2 pickup / after-hours · no live school</div>
      </aside>
      <main className="main">
        <AuditProvider>
          <Outlet />
          <AuditPanel />
        </AuditProvider>
      </main>
    </div>
  );
}
