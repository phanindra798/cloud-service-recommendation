import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import HistoryPanel from "./HistoryPanel";
import { jsonResponse, signIn, SAMPLE_RESULT } from "../test/utils";

const HISTORY = [
  { id: 2, problem: "a chat app", resultJson: JSON.stringify(SAMPLE_RESULT), createdAt: "2026-10-01T10:00:00Z" },
  { id: 1, problem: "x".repeat(100), resultJson: "{corrupt", createdAt: "2026-09-01T10:00:00Z" },
];

beforeEach(() => signIn("alice@example.com"));

test("loads history only when opened", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse(HISTORY));
  render(<HistoryPanel onSelect={jest.fn()} refreshKey={0} />);

  expect(global.fetch).not.toHaveBeenCalled();
  userEvent.click(screen.getByRole("button", { name: /history/i }));

  expect(await screen.findByText("a chat app")).toBeInTheDocument();
  expect(screen.getByText("x".repeat(80) + "…")).toBeInTheDocument();
  expect(global.fetch.mock.calls[0][1].credentials).toBe("include");
});

test("selecting an entry passes the parsed result back", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse(HISTORY));
  const onSelect = jest.fn();
  render(<HistoryPanel onSelect={onSelect} refreshKey={0} />);

  userEvent.click(screen.getByRole("button", { name: /history/i }));
  userEvent.click(await screen.findByText("a chat app"));

  expect(onSelect).toHaveBeenCalledWith("a chat app", SAMPLE_RESULT);
});

test("corrupt entries are ignored instead of crashing", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse(HISTORY));
  const onSelect = jest.fn();
  render(<HistoryPanel onSelect={onSelect} refreshKey={0} />);

  userEvent.click(screen.getByRole("button", { name: /history/i }));
  userEvent.click(await screen.findByText("x".repeat(80) + "…"));

  expect(onSelect).not.toHaveBeenCalled();
});

test("shows an empty state", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse([]));
  render(<HistoryPanel onSelect={jest.fn()} refreshKey={0} />);

  userEvent.click(screen.getByRole("button", { name: /history/i }));

  expect(await screen.findByText(/no past recommendations/i)).toBeInTheDocument();
});
