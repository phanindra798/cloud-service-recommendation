import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import mermaid from "mermaid";
import HomePage from "./HomePage";
import { renderAt, jsonResponse, signIn, SAMPLE_RESULT } from "../test/utils";

beforeEach(() => {
  signIn("alice@example.com", "jwt-123");
  mermaid.render.mockResolvedValue({ svg: "<svg></svg>" });
});

function submit(problem) {
  userEvent.type(screen.getByRole("textbox"), problem);
  userEvent.click(screen.getByRole("button", { name: /get recommendation/i }));
}

test("sends the problem with the JWT and renders the recommendation", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse(SAMPLE_RESULT));
  renderAt(<HomePage />);

  submit("a serverless todo app");

  expect(await screen.findByText("Serverless fits spiky traffic.")).toBeInTheDocument();
  expect(screen.getByText("2 services identified")).toBeInTheDocument();

  const [url, options] = global.fetch.mock.calls[0];
  expect(url).toMatch(/\/api\/recommend$/);
  expect(options.headers.Authorization).toBe("Bearer jwt-123");
  expect(JSON.parse(options.body)).toEqual({ problem: "a serverless todo app" });
});

test("submit is disabled until something is typed", () => {
  global.fetch = jest.fn();
  renderAt(<HomePage />);

  expect(screen.getByRole("button", { name: /get recommendation/i })).toBeDisabled();
});

test("shows a friendly message when rate limited", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse({ error: "Rate limit exceeded" }, 429));
  renderAt(<HomePage />);

  submit("anything");

  expect(await screen.findByText(/too many requests/i)).toBeInTheDocument();
});

test("shows the backend's error message", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse({ error: "Problem description too long" }, 400));
  renderAt(<HomePage />);

  submit("anything");

  expect(await screen.findByText("Problem description too long")).toBeInTheDocument();
});

test("an expired session signs the user out", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse({}, 403));
  renderAt(<HomePage />);

  submit("anything");

  await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("/login"));
  expect(localStorage.getItem("auth_token")).toBeNull();
});
