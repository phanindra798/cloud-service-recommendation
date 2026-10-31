import { useState, useEffect } from "react";
import { useNavigate, Link } from "react-router-dom";
import { getAuth } from "../lib/auth";
import { API_BASE } from "../lib/api";

export default function RegisterPage() {
  const [email, setEmail]       = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm]   = useState("");
  const [showPw, setShowPw]     = useState(false);
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState(null);
  const [success, setSuccess]   = useState(false);
  const navigate = useNavigate();

  // Redirect if already logged in
  useEffect(() => {
    if (getAuth()) navigate("/", { replace: true });
  }, [navigate]);

  const pwStrength = () => {
    if (password.length === 0) return null;
    if (password.length < 8)  return "weak";
    if (password.length < 12) return "good";
    return "strong";
  };

  const strengthLabel = { weak: "Too short", good: "Good", strong: "Strong" };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim() || !password || !confirm) { setError("All fields are required."); return; }
    if (password.length < 8)  { setError("Password must be at least 8 characters."); return; }
    if (password !== confirm) { setError("Passwords do not match."); return; }
    setLoading(true); setError(null);
    try {
      const res = await fetch(`${API_BASE}/auth/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.trim(), password }),
      });
      if (!res.ok) {
        const t = await res.text();
        throw new Error(t || `Registration failed (${res.status})`);
      }
      setSuccess(true);
      setTimeout(() => navigate("/login"), 2200);
    } catch (err) {
      setError(
        err.name === "TypeError" && err.message.includes("fetch")
          ? "Cannot reach the auth server at localhost:8080. Is it running?"
          : err.message || "Registration failed. Please try again."
      );
    } finally { setLoading(false); }
  };

  const strength = pwStrength();

  return (
    <main className="main main--auth">
      {/* Hero */}
      <div className="auth-hero">
        <p className="hero-eyebrow">Get Started Free</p>
        <h1 className="hero-title hero-title--sm">
          Create your<br />
          <span className="hero-accent">Account</span>
        </h1>
        <p className="hero-desc">
          Join engineers using AI-powered cloud recommendations to build faster.
        </p>
      </div>

      {/* Card */}
      <div className="card auth-card">
        <div className="card-header">
          <span className="card-tag">REGISTER</span>
          <Link to="/login" className="auth-switch-link">
            Have an account? Login →
          </Link>
        </div>

        {success ? (
          <div className="success-box">
            <svg viewBox="0 0 20 20" fill="currentColor" className="success-icon">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
            </svg>
            <div>
              <strong>Account Created!</strong>
              <p>Redirecting you to login…</p>
            </div>
          </div>
        ) : (
          <form className="auth-form" onSubmit={handleSubmit} noValidate>
            {/* Email */}
            <div className="field-group">
              <label className="field-label" htmlFor="reg-email">Email Address</label>
              <div className="field-wrap">
                <svg className="field-icon" viewBox="0 0 20 20" fill="currentColor">
                  <path d="M2.003 5.884L10 9.882l7.997-3.998A2 2 0 0016 4H4a2 2 0 00-1.997 1.884z"/>
                  <path d="M18 8.118l-8 4-8-4V14a2 2 0 002 2h12a2 2 0 002-2V8.118z"/>
                </svg>
                <input
                  id="reg-email"
                  className="auth-input"
                  type="email"
                  placeholder="you@example.com"
                  value={email}
                  onChange={(e) => { setEmail(e.target.value); setError(null); }}
                  disabled={loading}
                  autoComplete="email"
                  required
                />
              </div>
            </div>

            {/* Password */}
            <div className="field-group">
              <label className="field-label" htmlFor="reg-password">Password</label>
              <div className="field-wrap">
                <svg className="field-icon" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clipRule="evenodd"/>
                </svg>
                <input
                  id="reg-password"
                  className="auth-input auth-input--pw"
                  type={showPw ? "text" : "password"}
                  placeholder="Min. 8 characters"
                  value={password}
                  onChange={(e) => { setPassword(e.target.value); setError(null); }}
                  disabled={loading}
                  autoComplete="new-password"
                  required
                />
                <button
                  type="button"
                  className="pw-toggle"
                  onClick={() => setShowPw((v) => !v)}
                  tabIndex={-1}
                  aria-label={showPw ? "Hide password" : "Show password"}
                >
                  {showPw ? (
                    <svg viewBox="0 0 20 20" fill="currentColor">
                      <path fillRule="evenodd" d="M3.707 2.293a1 1 0 00-1.414 1.414l14 14a1 1 0 001.414-1.414l-1.473-1.473A10.014 10.014 0 0019.542 10C18.268 5.943 14.478 3 10 3a9.958 9.958 0 00-4.512 1.074l-1.78-1.781zm4.261 4.26l1.514 1.515a2.003 2.003 0 012.45 2.45l1.514 1.514a4 4 0 00-5.478-5.478z" clipRule="evenodd"/>
                      <path d="M12.454 16.697L9.75 13.992a4 4 0 01-3.742-3.741L2.335 6.578A9.98 9.98 0 00.458 10c1.274 4.057 5.065 7 9.542 7 .847 0 1.669-.105 2.454-.303z"/>
                    </svg>
                  ) : (
                    <svg viewBox="0 0 20 20" fill="currentColor">
                      <path d="M10 12a2 2 0 100-4 2 2 0 000 4z"/>
                      <path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd"/>
                    </svg>
                  )}
                </button>
              </div>

              {/* Strength bar */}
              {strength && (
                <div className="pw-strength">
                  <div className={`pw-bar pw-bar--${strength}`} />
                  <span className="pw-label">{strengthLabel[strength]}</span>
                </div>
              )}
            </div>

            {/* Confirm Password */}
            <div className="field-group">
              <label className="field-label" htmlFor="reg-confirm">Confirm Password</label>
              <div className="field-wrap">
                <svg className="field-icon" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5c.11.65.166 1.32.166 2.001 0 5.225-3.34 9.67-8 11.317C5.34 16.67 2 12.225 2 7c0-.682.057-1.35.166-2.001zm11.541 3.708a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                </svg>
                <input
                  id="reg-confirm"
                  className={`auth-input ${confirm && confirm !== password ? "auth-input--error" : ""}`}
                  type={showPw ? "text" : "password"}
                  placeholder="Re-enter your password"
                  value={confirm}
                  onChange={(e) => { setConfirm(e.target.value); setError(null); }}
                  disabled={loading}
                  autoComplete="new-password"
                  required
                />
                {confirm && confirm === password && (
                  <svg className="field-check" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                  </svg>
                )}
              </div>
            </div>

            {/* Error */}
            {error && (
              <div className="error-box">
                <svg viewBox="0 0 20 20" fill="currentColor" className="error-icon">
                  <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                </svg>
                <div><strong>Error</strong><p>{error}</p></div>
              </div>
            )}

            {/* Submit */}
            <button
              type="submit"
              className={`submit-btn ${loading ? "loading" : ""}`}
              disabled={loading}
            >
              {loading ? (
                <><span className="spinner" />Creating account...</>
              ) : (
                <>
                  <svg className="btn-icon" viewBox="0 0 20 20" fill="currentColor">
                    <path d="M8 9a3 3 0 100-6 3 3 0 000 6zM8 11a6 6 0 016 6H2a6 6 0 016-6zM16 7a1 1 0 10-2 0v1h-1a1 1 0 100 2h1v1a1 1 0 102 0v-1h1a1 1 0 100-2h-1V7z" />
                  </svg>
                  Create Account
                </>
              )}
            </button>
          </form>
        )}
      </div>
    </main>
  );
}

