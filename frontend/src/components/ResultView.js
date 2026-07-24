import MermaidDiagram from "./MermaidDiagram";

const SERVICE_ICONS = {
  EC2: "🖥️", S3: "🪣", Lambda: "λ", RDS: "🗄️", DynamoDB: "⚡",
  CloudFront: "🌐", "API Gateway": "🔗",
  "Virtual Machines": "🖥️", "Blob Storage": "🪣", "SQL Database": "🗄️",
  "Cosmos DB": "⚡", CDN: "🌐", Functions: "λ", "API Management": "🔗",
  "Compute Engine": "🖥️", "Cloud Storage": "🪣", "Cloud SQL": "🗄️",
  Firestore: "⚡", "Cloud CDN": "🌐", "Cloud Functions": "λ",
};

const PROVIDER_META = {
  aws:   { label: "AWS",   emoji: "🟧" },
  azure: { label: "Azure", emoji: "🔷" },
  gcp:   { label: "GCP",   emoji: "🟢" },
};

// Full recommendation display: reasoning, per-provider service lists with
// cost comparison, and the generated architecture diagram.
export default function ResultView({ data }) {
  if (!data) return null;

  const providers = ["aws", "azure", "gcp"];
  const cost = data.cost || {};

  return (
    <>
      {data.reasoning && (
        <div className="reasoning-block">
          <h3 className="result-section-title">Why this architecture</h3>
          <p className="result-prose">{data.reasoning}</p>
          {data.aiGenerated === false && (
            <p className="fallback-note">⚠ AI unavailable — baseline recommendation shown.</p>
          )}
        </div>
      )}

      <div className="provider-grid">
        {providers.map((p) => (
          <div key={p} className={`provider-col ${data.best === PROVIDER_META[p].label ? "provider-best" : ""}`}>
            <div className="provider-header">
              <span>{PROVIDER_META[p].emoji} {PROVIDER_META[p].label}</span>
              {cost[p] !== undefined && <span className="provider-cost">${cost[p]}/mo</span>}
              {data.best === PROVIDER_META[p].label && <span className="best-badge">BEST VALUE</span>}
            </div>
            {(data[p] || []).map((s, i) => (
              <div key={i} className="result-bullet">
                <span className="bullet-icon">{SERVICE_ICONS[s] || "☁️"}</span>
                <span className="bullet-text">{s}</span>
              </div>
            ))}
          </div>
        ))}
      </div>

      {data.mermaid && (
        <div className="diagram-block">
          <h3 className="result-section-title">Architecture Diagram</h3>
          <MermaidDiagram code={data.mermaid} />
        </div>
      )}
    </>
  );
}
