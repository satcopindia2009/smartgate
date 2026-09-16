import type { ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { Layout } from "./components/Layout";
import { BlacklistPage } from "./pages/BlacklistPage";
import { GatesPage } from "./pages/GatesPage";
import { HistoryPage } from "./pages/HistoryPage";
import { LivePage } from "./pages/LivePage";
import { LoginPage } from "./pages/LoginPage";
import { ReportsPage } from "./pages/ReportsPage";

function RequireAuth({ children }: { children: ReactNode }) {
  const { ready, user } = useAuth();
  if (!ready) {
    return <div className="loading-msg">Loading session…</div>;
  }
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/live" element={<LivePage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/blacklist" element={<BlacklistPage />} />
        <Route path="/gates" element={<GatesPage />} />
        <Route path="/reports" element={<ReportsPage />} />
      </Route>
      <Route path="/" element={<Navigate to="/live" replace />} />
      <Route path="*" element={<Navigate to="/live" replace />} />
    </Routes>
  );
}
