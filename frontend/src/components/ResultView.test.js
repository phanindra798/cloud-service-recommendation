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

test("lists services and monthly cost for every provider", () => {
  render(<ResultView data={NO_DIAGRAM} />);

  ["Lambda", "DynamoDB", "Functions", "Cosmos DB", "Cloud Functions", "Firestore"].forEach((s) =>
    expect(screen.getByText(s)).toBeInTheDocument()
  );
  expect(screen.getByText("$7/mo")).toBeInTheDocument();
  expect(screen.getByText("$8.2/mo")).toBeInTheDocument();
  expect(screen.getByText("$7.6/mo")).toBeInTheDocument();
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
