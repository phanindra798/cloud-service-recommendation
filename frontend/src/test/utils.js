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
  cost: { aws: 7, azure: 8.2, gcp: 7.6 },
  best: "AWS",
  pricingSource: { aws: "static baseline", azure: "live (Azure Retail Prices API)", gcp: "static baseline" },
  reasoning: "Serverless fits spiky traffic.",
  mermaid: "graph TD; User-->Lambda; Lambda-->DynamoDB;",
  aiGenerated: true,
};
