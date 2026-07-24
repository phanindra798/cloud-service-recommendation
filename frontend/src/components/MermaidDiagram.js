import { useState, useEffect } from "react";
import mermaid from "mermaid";

mermaid.initialize({ startOnLoad: false, theme: "dark", securityLevel: "strict" });

let diagramCounter = 0;

// Renders a Mermaid definition to inline SVG.
// Falls back to showing the raw definition if it fails to parse
// (the diagram code is AI-generated, so bad syntax is possible).
export default function MermaidDiagram({ code }) {
  const [svg, setSvg] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!code) return;
    let cancelled = false;
    setSvg(null);
    setFailed(false);
    diagramCounter += 1;
    mermaid
      .render(`archDiagram${diagramCounter}`, code)
      .then(({ svg }) => { if (!cancelled) setSvg(svg); })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, [code]);

  if (!code) return null;
  if (failed) return <pre className="result-raw">{code}</pre>;
  if (!svg) return <div className="diagram-loading">Rendering diagram…</div>;
  return <div className="mermaid-diagram" dangerouslySetInnerHTML={{ __html: svg }} />;
}
