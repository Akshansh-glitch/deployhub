import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { api, auth } from "../api.js";
import { Logo } from "../components/Layout.jsx";

export default function Login() {
  const navigate = useNavigate();
  const [mode, setMode] = useState("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (auth.token) return <Navigate to="/" replace />;

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await api(`/auth/${mode === "login" ? "login" : "register"}`, {
        method: "POST",
        body: { email, password },
      });
      auth.save(res);
      navigate("/");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="login">
      <div className="login-panel">
        <Logo />
        <h1>{mode === "login" ? "Sign in" : "Create your account"}</h1>
        <p className="muted">Deploy a GitHub repository to Kubernetes and watch it run.</p>
        <form onSubmit={submit} className="form">
          <label>
            Email
            <input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          <label>
            Password
            <input
              type="password"
              required
              minLength={8}
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            {mode === "register" && <span className="hint">At least 8 characters</span>}
          </label>
          {error && <p className="error" role="alert">{error}</p>}
          <button className="btn btn-primary" disabled={busy}>
            {mode === "login" ? "Sign in" : "Create account"}
          </button>
        </form>
        <p className="small">
          {mode === "login" ? "New here? " : "Already have an account? "}
          <button className="btn-link" onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(null); }}>
            {mode === "login" ? "Create an account" : "Sign in"}
          </button>
        </p>
      </div>
    </main>
  );
}
