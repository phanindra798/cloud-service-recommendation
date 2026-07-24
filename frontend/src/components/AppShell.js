import { useNavigate, Link } from "react-router-dom";
import { getAuth, clearAuth } from "../lib/auth";

// Shared page frame: header with logo/auth controls, footer, background layers.
export default function AppShell({ children }) {
  const auth = getAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    clearAuth();
    navigate("/login");
  };

  return (
    <div className="app">
      <div className="grid-bg" />
      <div className="noise-overlay" />

      <header className="header">
        <Link to="/" className="logo-link">
          <div className="logo-cluster">
            <div className="logo-icon">
              <svg viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M18 4L32 12V24L18 32L4 24V12L18 4Z" stroke="currentColor" strokeWidth="1.5" fill="none"/>
                <path d="M18 4L18 32M4 12L32 12M4 24L32 24" stroke="currentColor" strokeWidth="0.75" strokeDasharray="2 2" opacity="0.4"/>
                <circle cx="18" cy="18" r="4" fill="currentColor" opacity="0.9"/>
                <circle cx="18" cy="4" r="2" fill="currentColor"/>
                <circle cx="32" cy="12" r="2" fill="currentColor"/>
                <circle cx="32" cy="24" r="2" fill="currentColor"/>
                <circle cx="18" cy="32" r="2" fill="currentColor"/>
                <circle cx="4"  cy="24" r="2" fill="currentColor"/>
                <circle cx="4"  cy="12" r="2" fill="currentColor"/>
              </svg>
            </div>
            <div className="logo-text">
              <span className="logo-label">CLOUD</span>
              <span className="logo-divider">|</span>
              <span className="logo-sub">Recommendation Engine</span>
            </div>
          </div>
        </Link>

        <div className="header-right">
          {auth ? (
            <div className="auth-cluster">
              <span className="auth-email">{auth.email}</span>
              <button className="logout-btn" onClick={handleLogout}>Sign Out</button>
            </div>
          ) : (
            <div className="auth-cluster">
              <Link className="nav-link" to="/login">Login</Link>
              <Link className="nav-link nav-link--primary" to="/register">Register</Link>
            </div>
          )}
          <div className="status-badge">
            <span className="status-dot" />
            AI ONLINE
          </div>
        </div>
      </header>

      {children}

      <footer className="footer">
        <span>AI Cloud Service Recommendation Platform</span>
        <span className="footer-sep">·</span>
        <span>AWS · Azure · GCP · Powered by Generative AI</span>
      </footer>
    </div>
  );
}
