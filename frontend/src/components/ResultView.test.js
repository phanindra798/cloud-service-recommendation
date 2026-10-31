import { render, screen } from "@testing-library/react";
import mermaid from "mermaid";
import ResultView from "./ResultView";
import { SAMPLE_RESULT } from "../test/utils";

// Diagram rendering is async; tests that don't care about it leave it out.
const NO_DIAGRAM = { ...SAMPLE_RESULT, mermaid: "" };

beforeEach(() => {
  mermaid.render.mockResolvedValue({ svg: "<svg data-testid='diagram'></svg>" });
});

test("renders nothing without data", () => {
  const { container } = render(<ResultView data={null} />);
  expect(container).toBeEmptyDOMElement();
});

test("lists services and monthly total for every provider", () => {
  render(<ResultView data={NO_DIAGRAM} />);

  ["Lambda", "DynamoDB", "Functions", "Cosmos DB", "Cloud Functions", "Firestore"].forEach((s) =>
    expect(screen.getByText(s)).toBeInTheDocument()
  );
  expect(screen.getByText("$3.06/mo")).toBeInTheDocument();
  expect(screen.getByText("$3.50/mo")).toBeInTheDocument();
  expect(screen.getByText("$4.20/mo")).toBeInTheDocument();
});

test("shows quantity and cost for each service", () => {
  render(<ResultView data={NO_DIAGRAM} />);

  const lambda = screen.getByText("Lambda").closest(".cost-row");
  expect(lambda).toHaveTextContent("2 million requests");
  expect(lambda).toHaveTextContent("$2.06");
});

test("shows the usage the estimate was sized from", () => {
  render(<ResultView data={NO_DIAGRAM} />);

  expect(screen.getByText("Estimated usage")).toBeInTheDocument();
  expect(screen.getByText("50K")).toBeInTheDocument();
  expect(screen.getByText("monthly users")).toBeInTheDocument();
  expect(screen.getByText("2M")).toBeInTheDocument();
  expect(screen.getByText("1.5K GB")).toBeInTheDocument();
});

test("still renders history saved before per-service costs existed", () => {
  const { breakdown, usage, ...legacy } = NO_DIAGRAM;
  render(<ResultView data={{ ...legacy, cost: { aws: 7, azure: 8.2, gcp: 7.6 } }} />);

  expect(screen.getByText("Lambda")).toBeInTheDocument();
  expect(screen.getByText("$7.00/mo")).toBeInTheDocument();
  expect(screen.queryByText("Estimated usage")).not.toBeInTheDocument();
  expect(document.querySelector(".cost-row")).toBeNull();
});

test("highlights the cheapest provider", () => {
  render(<ResultView data={NO_DIAGRAM} />);

  const badge = screen.getByText("BEST VALUE");
  expect(badge.closest(".provider-col")).toHaveClass("provider-best");
  expect(badge.closest(".provider-col")).toHaveTextContent("AWS");
});

test("warns when the AI fallback was used", () => {
  render(<ResultView data={{ ...NO_DIAGRAM, aiGenerated: false }} />);

  expect(screen.getByText(/ai unavailable/i)).toBeInTheDocument();
});

test("renders the architecture diagram", async () => {
  render(<ResultView data={SAMPLE_RESULT} />);

  expect(await screen.findByTestId("diagram")).toBeInTheDocument();
  expect(mermaid.render).toHaveBeenCalledWith(expect.any(String), SAMPLE_RESULT.mermaid);
});
