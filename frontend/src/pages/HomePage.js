import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { apiFetch } from "../lib/api";
import { clearAuth } from "../lib/auth";
import ResultView from "../components/ResultView";
import HistoryPanel from "../components/HistoryPanel";
import FollowUpQuestions from "../components/FollowUpQuestions";
import { pendingQuestions } from "../lib/questions";

const PLACEHOLDERS = [
  "e.g. I need to build a real-time analytics pipeline for 10M daily events...",
  "e.g. My startup needs a scalable image processing backend with ML inference...",
  "e.g. We want to migrate a monolithic app to microservices with auto-scaling...",
  "e.g. Need a serverless API that handles variable traffic spikes efficiently...",
];

export default function HomePage() {
  const [problem, setProblem] = useState("");
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [placeholderIndex, setPlaceholderIndex] = useState(0);
  const [charCount, setCharCount] = useState(0);
  const [animateResult, setAnimateResult] = useState(false);
  const [historyRefresh, setHistoryRefresh] = useState(0);
  // { questions, known } while the follow-up step is showing
  const [followUp, setFollowUp] = useState(null);
  const navigate = useNavigate();
  const resultRef = useRef(null);

  // The cookie may have expired since the last visit; this also primes the CSRF cookie.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await apiFetch("/auth/me");
        if (!cancelled && res && res.status === 401) { clearAuth(); navigate("/login"); }
      } catch { /* backend unreachable: the first real request will report it */ }
    })();
    return () => { cancelled = true; };
  }, [navigate]);

  useEffect(() => {
    const id = setInterval(() => setPlaceholderIndex((i) => (i + 1) % PLACEHOLDERS.length), 3500);
    return () => clearInterval(id);
  }, []);

  const handleInput = (e) => {
    setProblem(e.target.value);
    setCharCount(e.target.value.length);
    if (error) setError(null);
    if (followUp) setFollowUp(null);
  };

  // Step 1: ask only what the description doesn't already say.
  const handleSubmit = () => {
    if (!problem.trim()) { setError("Please describe your problem or use case first."); return; }
    setError(null); setResult(null); setAnimateResult(false);
    setFollowUp(pendingQuestions(problem));
  };

  // Step 2: run the recommendation with whatever was answered.
  const runRecommendation = async (answers = {}) => {
    const known = followUp ? followUp.known : {};
    const body = { problem: problem.trim() };
    // An answer picked in the follow-up wins; otherwise use what the description said
    const region = answers.region || known.region;
    const users = answers.users || known.users;
    const appType = answers.appType || known.appType;
    if (region) body.region = region;
    if (users) body.monthlyActiveUsers = users;
    if (appType) body.appType = appType;
    if (answers.highAvailability) body.highAvailability = true;

    setFollowUp(null);
    setLoading(true); setError(null); setResult(null); setAnimateResult(false);
    try {
      const res = await apiFetch("/api/recommend", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (res.status === 401 || res.status === 403) {
        clearAuth();
        navigate("/login");
        return;
      }
      if (res.status === 429) throw new Error("Too many requests — wait a minute and try again.");
      if (!res.ok) {
        let msg = `Server error: ${res.status}`;
        try { const j = await res.json(); msg = j.error || msg; } catch { /* keep default */ }
        throw new Error(msg);
      }
      const data = await res.json();
      setResult(data);
      setHistoryRefresh((k) => k + 1);
      setTimeout(() => setAnimateResult(true), 50);
    } catch (err) {
      setError(err.message || "Something went wrong");
    } finally { setLoading(false); }
  };

  const showHistoryEntry = (histProblem, histResult) => {
    setFollowUp(null);
    setProblem(histProblem);
    setCharCount(histProblem.length);
    setResult(histResult);
    setAnimateResult(true);
    resultRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  const handleKeyDown = (e) => { if ((e.ctrlKey || e.metaKey) && e.key === "Enter") handleSubmit(); };

  const serviceCount = result ? (result.aws || []).length : 0;

  return (
    <main className="main">
      <div className="hero">
        <p className="hero-eyebrow">Powered by Generative AI</p>
        <h1 className="hero-title">
          AI Cloud Service<br />
          <span className="hero-accent">Recommendation</span> Platform
        </h1>
        <p className="hero-desc">
          Describe your architecture challenge. Get AWS, Azure and GCP recommendations
          with cost comparison and an architecture diagram.
        </p>
      </div>

      <div className="card">
        <div className="card-header">
          <span className="card-tag">INPUT</span>
          <span className="card-hint">Ctrl+Enter to submit</span>
        </div>

        <div className="textarea-wrapper">
          <textarea
            className={`textarea ${problem ? "has-content" : ""}`}
            value={problem}
            onChange={handleInput}
            onKeyDown={handleKeyDown}
            placeholder={PLACEHOLDERS[placeholderIndex]}
            rows={5}
            maxLength={2000}
            disabled={loading}
          />
          <div className="textarea-footer">
            <span className="char-count">{charCount} / 2000</span>
            {problem && (
              <button className="clear-btn" onClick={() => { setProblem(""); setCharCount(0); setError(null); setFollowUp(null); }}>
                Clear
              </button>
            )}
          </div>
        </div>

        <button
          className={`submit-btn ${loading ? "loading" : ""}`}
          onClick={handleSubmit}
          disabled={loading || !problem.trim()}
        >
          {loading ? (
            <><span className="spinner" />Analyzing...</>
          ) : (
            <>
              <svg className="btn-icon" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M11.3 1.046A1 1 0 0112 2v5h4a1 1 0 01.82 1.573l-7 10A1 1 0 018 18v-5H4a1 1 0 01-.82-1.573l7-10a1 1 0 011.12-.38z" clipRule="evenodd" />
              </svg>
              Get Recommendation
            </>
          )}
        </button>

        {error && (
          <div className="error-box">
            <svg viewBox="0 0 20 20" fill="currentColor" className="error-icon">
              <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
            </svg>
            <div><strong>Error</strong><p>{error}</p></div>
          </div>
        )}
      </div>

      {(result || loading) && (
        <div ref={resultRef} className={`result-card ${animateResult ? "visible" : ""}`}>
          <div className="result-header">
            <span className="card-tag result-tag">RECOMMENDATION</span>
            <div className="result-meta">
              {result && (
                <span className="result-count">{serviceCount} services identified</span>
              )}
            </div>
          </div>

          {loading && !result && (
            <div className="skeleton-wrapper">
              {[...Array(5)].map((_, i) => (
                <div key={i} className="skeleton-line"
                  style={{ width: `${75 + Math.random() * 20}%`, animationDelay: `${i * 0.1}s` }} />
              ))}
            </div>
          )}

          <ResultView data={result} />
        </div>
      )}

      {followUp && !loading && (
        <FollowUpQuestions
          questions={followUp.questions}
          onSubmit={runRecommendation}
          onSkip={() => runRecommendation({})}
        />
      )}

      <HistoryPanel onSelect={showHistoryEntry} refreshKey={historyRefresh} />
    </main>
  );
}
