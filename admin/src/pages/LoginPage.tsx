import { FormEvent, useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { IconShield } from "../components/Icons";

export function LoginPage() {
  const { user, ready, login } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from || "/live";
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  if (ready && user) return <Navigate to={from} replace />;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      await login(username.trim(), password);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-page">
      <div className="demo-watermark">DEMO</div>
      <div className="login-card">
        <div className="login-brand">
          <div className="logo-mark" aria-hidden="true">
            <IconShield />
          </div>
          <div>
            <strong>Smart Visitor</strong>
            <small>Admin dashboard · school from sign-in</small>
          </div>
        </div>
        <h1>Sign in</h1>
        <p className="login-sub">Office Admin or Security Head · JWT to /v1 · fixtures fallback</p>
        <form onSubmit={onSubmit}>
          <div className="field">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          {error && <p className="login-error">{error}</p>}
          <button type="submit" className="btn btn-primary btn-block" disabled={busy || !username || !password}>
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </form>
        <p className="login-hint">
          Demo: <code>admin</code> / <code>admin123</code> · <code>security</code> / <code>sh123</code>
          <br />
          Pranay School Pune: <code>pranay.admin</code> / <code>PranayAdmin@2026</code>
        </p>
        <p className="login-hold">No production · no live school gates (V4 HOLD)</p>
      </div>
    </div>
  );
}
