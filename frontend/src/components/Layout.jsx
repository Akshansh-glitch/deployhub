import { Link, Outlet, useNavigate } from "react-router-dom";
import { auth } from "../api.js";

export function Logo() {
  return (
    <span className="logo">
      <svg viewBox="0 0 32 32" width="22" height="22" aria-hidden="true">
        <rect width="32" height="32" rx="7" fill="var(--harbour)" />
        <circle cx="9" cy="16" r="3.5" fill="var(--on-harbour)" />
        <circle cx="23" cy="16" r="3.5" fill="var(--on-harbour)" />
        <rect x="9" y="14.5" width="14" height="3" fill="var(--on-harbour)" />
      </svg>
      DeployHub
    </span>
  );
}

export default function Layout() {
  const navigate = useNavigate();
  const signOut = () => {
    auth.clear();
    navigate("/login");
  };
  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="topbar-home">
            <Logo />
          </Link>
          <nav className="topbar-nav">
            <Link to="/">Apps</Link>
            <span className="topbar-user">{auth.email}</span>
            <button className="btn-link" onClick={signOut}>
              Sign out
            </button>
          </nav>
        </div>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </>
  );
}
