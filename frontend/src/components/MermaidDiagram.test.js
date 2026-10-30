import { render, screen } from "@testing-library/react";
import mermaid from "mermaid";
import MermaidDiagram from "./MermaidDiagram";

test("renders the SVG produced by mermaid", async () => {
  mermaid.render.mockResolvedValue({ svg: "<svg data-testid='svg'></svg>" });
  render(<MermaidDiagram code="graph TD; A-->B;" />);

  expect(screen.getByText(/rendering diagram/i)).toBeInTheDocument();
  expect(await screen.findByTestId("svg")).toBeInTheDocument();
});

test("falls back to the raw definition when mermaid can't parse it", async () => {
  mermaid.render.mockRejectedValue(new Error("Parse error"));
  render(<MermaidDiagram code="not a diagram" />);

  expect(await screen.findByText("not a diagram")).toBeInTheDocument();
});

test("renders nothing for empty code", () => {
  const { container } = render(<MermaidDiagram code="" />);
  expect(container).toBeEmptyDOMElement();
  expect(mermaid.render).not.toHaveBeenCalled();
});
