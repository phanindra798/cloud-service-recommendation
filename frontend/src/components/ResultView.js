import MermaidDiagram from "./MermaidDiagram";

const SERVICE_ICONS = {
  EC2: "🖥️", S3: "🪣", Lambda: "λ", RDS: "🗄️", DynamoDB: "⚡",
  CloudFront: "🌐", "API Gateway": "🔗", "ECS Fargate": "📦", ElastiCache: "🚀",
  SQS: "📨", Cognito: "🔐",
  "Virtual Machines": "🖥️", "Blob Storage": "🪣", "SQL Database": "🗄️",
  "Cosmos DB": "⚡", CDN: "🌐", Functions: "λ", "API Management": "🔗",
  "Container Apps": "📦", "Cache for Redis": "🚀", "Service Bus": "📨",
  "Entra External ID": "🔐",
  "Compute Engine": "🖥️", "Cloud Storage": "🪣", "Cloud SQL": "🗄️",
  Firestore: "⚡", "Cloud CDN": "🌐", "Cloud Functions": "λ",
  "Cloud Run": "📦", Memorystore: "🚀", "Pub/Sub": "📨", "Identity Platform": "🔐",
};

const compact = new Intl.NumberFormat("en-US", { notation: "compact", maximumFractionDigits: 1 });

function formatMoney(value) {
  return `$${value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatQuantity(quantity, unit) {
  const n = Number.isInteger(quantity) ? quantity.toLocaleString("en-US") : quantity.toFixed(2);
  return `${n} ${unit}${unit === "instance" || unit === "node" ? (quantity === 1 ? "" : "s") : ""}`;
}

// The usage numbers costs were sized from, so the estimate is explainable.
function UsageAssumptions({ usage, region, highAvailability }) {
  if (!usage) return null;
  const sources = usage.sources || {};
  const facts = [
    [compact.format(usage.monthlyActiveUsers), "monthly users", sources.monthlyActiveUsers],
    [compact.format(usage.requestsPerMonth), "requests / month", sources.requestsPerMonth],
    [`${usage.peakRequestsPerSecond}`, "peak req/s"],
    [`${compact.format(usage.storageGb)} GB`, "stored", sources.storageGb],
    [`${compact.format(usage.dataTransferGb)} GB`, "transferred / month", sources.dataTransferGb],
  ];
  return (
    <div className="usage-block">
      <h3 className="result-section-title">Estimated usage</h3>
      {(region || highAvailability || usage.appType) && (
        <div className="assumption-chips">
          {region && (
            <span className="assumption-chip" title={`AWS ${region.aws} · Azure ${region.azure} · GCP ${region.gcp}`}>
              📍 {region.label}
            </span>
          )}
          {usage.appType && <span className="assumption-chip">📱 {usage.appType}</span>}
          {highAvailability && <span className="assumption-chip">🛡️ High availability</span>}
        </div>
      )}
      <div className="usage-grid">
        {facts.map(([value, label, source]) => (
          <div key={label} className="usage-fact">
            <span className="usage-value">{value}</span>
            <span className="usage-label">{label}</span>
            {source && <span className="usage-source">{source}</span>}
          </div>
        ))}
      </div>
      <p className="usage-note">Costs below are sized from these numbers (list prices, on-demand, free tiers excluded).</p>
    </div>
  );
}

// One row per service; falls back to a plain list for history saved before per-service costs existed.
function ServiceRows({ names, items }) {
  if (items && items.length) {
    return items.map((item) => (
      <div key={item.service} className="result-bullet cost-row">
        <span className="bullet-icon">{SERVICE_ICONS[item.service] || "☁️"}</span>
        <span className="bullet-text">
          {item.service}
          <span className="cost-detail">{formatQuantity(item.quantity, item.unit)}</span>
        </span>
        <span className="cost-amount" title={item.source}>{formatMoney(item.monthlyCost)}</span>
      </div>
    ));
  }
  return (names || []).map((s, i) => (
    <div key={i} className="result-bullet">
      <span className="bullet-icon">{SERVICE_ICONS[s] || "☁️"}</span>
      <span className="bullet-text">{s}</span>
    </div>
  ));
}

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
  const breakdown = data.breakdown || {};

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

      <UsageAssumptions usage={data.usage} region={data.region} highAvailability={data.highAvailability} />

      <div className="provider-grid">
        {providers.map((p) => (
          <div key={p} className={`provider-col ${data.best === PROVIDER_META[p].label ? "provider-best" : ""}`}>
            <div className="provider-header">
              <span>{PROVIDER_META[p].emoji} {PROVIDER_META[p].label}</span>
              {cost[p] !== undefined && <span className="provider-cost">{formatMoney(cost[p])}/mo</span>}
              {data.best === PROVIDER_META[p].label && <span className="best-badge">BEST VALUE</span>}
            </div>
            <ServiceRows names={data[p]} items={breakdown[p]} />
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
