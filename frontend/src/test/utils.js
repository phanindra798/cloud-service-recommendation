import { render } from "@testing-library/react";
import { MemoryRouter, Routes, Route, useLocation } from "react-router-dom";

// Shows the current path so tests can assert on navigation.
function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
}

export function renderAt(ui, { path = "/", route = "/" } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path={path} element={ui} />
        <Route path="*" element={null} />
      </Routes>
      <LocationProbe />
    </MemoryRouter>
  );
}

export function jsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    text: async () => (typeof body === "string" ? body : JSON.stringify(body)),
  };
}

export function textResponse(text, status) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => { throw new Error("not json"); },
    text: async () => text,
  };
}

export function signIn(email = "alice@example.com", token = "test-token") {
  localStorage.setItem("auth_email", email);
  localStorage.setItem("auth_token", token);
}

export const SAMPLE_RESULT = {
  aws: ["Lambda", "DynamoDB"],
  azure: ["Functions", "Cosmos DB"],
  gcp: ["Cloud Functions", "Firestore"],
  cost: { aws: 3.06, azure: 3.5, gcp: 4.2 },
  breakdown: {
    aws: [
      { service: "Lambda", quantity: 2, unit: "million requests", unitPrice: 1.03, monthlyCost: 2.06, source: "list-price estimate" },
      { service: "DynamoDB", quantity: 2, unit: "million requests", unitPrice: 0.5, monthlyCost: 1, source: "list-price estimate" },
    ],
    azure: [
      { service: "Functions", quantity: 2, unit: "million requests", unitPrice: 1, monthlyCost: 2, source: "list-price estimate" },
      { service: "Cosmos DB", quantity: 2, unit: "million requests", unitPrice: 0.75, monthlyCost: 1.5, source: "list-price estimate" },
    ],
    gcp: [
      { service: "Cloud Functions", quantity: 2, unit: "million requests", unitPrice: 1.2, monthlyCost: 2.4, source: "list-price estimate" },
      { service: "Firestore", quantity: 2, unit: "million requests", unitPrice: 0.9, monthlyCost: 1.8, source: "list-price estimate" },
    ],
  },
  usage: { monthlyActiveUsers: 50000, requestsPerMonth: 2000000, storageGb: 50, dataTransferGb: 1500, peakRequestsPerSecond: 2.3 },
  best: "AWS",
  pricingSource: { aws: "static baseline", azure: "live (Azure Retail Prices API)", gcp: "static baseline" },
  reasoning: "Serverless fits spiky traffic.",
  mermaid: "graph TD; User-->Lambda; Lambda-->DynamoDB;",
  aiGenerated: true,
};
