import { useState, useEffect, useCallback } from "react";
import { apiFetch } from "../lib/api";

// Collapsible list of the user's past recommendations.
// Clicking an entry reloads that result via onSelect(problem, result).
export default function HistoryPanel({ onSelect, refreshKey }) {
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await apiFetch("/api/history");
      if (res.ok) setItems(await res.json());
    } catch { /* history is non-critical */ }
  }, []);

  useEffect(() => { if (open) load(); }, [open, refreshKey, load]);

  return (
    <div className="card history-card">
      <button className="history-toggle" onClick={() => setOpen(!open)}>
        {open ? "▾" : "▸"} History {items.length > 0 && open ? `(${items.length})` : ""}
      </button>
      {open && (
        <div className="history-list">
          {items.length === 0 && <p className="result-prose">No past recommendations yet.</p>}
          {items.map((h) => (
            <button key={h.id} className="history-item" onClick={() => {
              try { onSelect(h.problem, JSON.parse(h.resultJson)); } catch { /* skip corrupt */ }
            }}>
              <span className="history-problem">{h.problem.slice(0, 80)}{h.problem.length > 80 ? "…" : ""}</span>
              <span className="history-date">{new Date(h.createdAt).toLocaleString()}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
