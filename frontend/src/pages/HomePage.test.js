import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import mermaid from "mermaid";
import HomePage from "./HomePage";
import { renderAt, jsonResponse, signIn, SAMPLE_RESULT } from "../test/utils";

beforeEach(() => {
  signIn("alice@example.com");
  document.cookie = "XSRF-TOKEN=csrf-abc";
  mermaid.render.mockResolvedValue({ svg: "<svg></svg>" });
});

function describe(problem) {
  userEvent.type(screen.getByRole("textbox", { name: "" }), problem);
  userEvent.click(screen.getByRole("button", { name: /get recommendation/i }));
}

// Describe the app and skip the follow-up questions.
function submit(problem) {
  describe(problem);
  userEvent.click(screen.getByRole("button", { name: /skip/i }));
}

function recommendCall() {
  return global.fetch.mock.calls.find(([url]) => url.endsWith("/api/recommend"));
}

function sentBody() {
  return JSON.parse(recommendCall()[1].body);
}

test("sends the problem with the CSRF token and renders the recommendation", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse(SAMPLE_RESULT));
  renderAt(<HomePage />);

  submit("a serverless todo app");

  expect(await screen.findByText("Serverless fits spiky traffic.")).toBeInTheDocument();
  expect(screen.getByText("2 services identified")).toBeInTheDocument();

  const [, options] = recommendCall();
  expect(options.credentials).toBe("include");
  expect(options.headers["X-XSRF-TOKEN"]).toBe("csrf-abc");
  expect(JSON.parse(options.body)).toEqual({ problem: "a serverless todo app" });
});

test("asks follow-up questions before calling the API", () => {
  global.fetch = jest.fn();
  renderAt(<HomePage />);

  describe("something cool");

  expect(screen.getByText(/where are most of your users/i)).toBeInTheDocument();
  expect(screen.getByText(/how many monthly users/i)).toBeInTheDocument();
  expect(screen.getByText(/what kind of app/i)).toBeInTheDocument();
  expect(screen.getByText(/stay online if a server fails/i)).toBeInTheDocument();
  expect(recommendCall()).toBeUndefined();
});

test("sends the chosen answers with the problem", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse(SAMPLE_RESULT));
  renderAt(<HomePage />);

  describe("something cool");
  userEvent.click(screen.getByRole("button", { name: "India" }));
  userEvent.click(screen.getByRole("button", { name: "10K – 100K" }));
  userEvent.click(screen.getByRole("button", { name: "E-commerce store" }));
  userEvent.click(screen.getByRole("button", { name: /yes, it must stay up/i }));
  userEvent.click(screen.getByRole("button", { name: /get estimate/i }));

  await screen.findByText("Serverless fits spiky traffic.");
  expect(sentBody()).toEqual({
    problem: "something cool",
    region: "INDIA",
    monthlyActiveUsers: 50000,
    appType: "ECOMMERCE",
    highAvailability: true,
  });
});

test("an exact user count overrides the range chips", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse(SAMPLE_RESULT));
  renderAt(<HomePage />);

  describe("something cool");
  userEvent.click(screen.getByRole("button", { name: "1M+" }));
  userEvent.type(screen.getByLabelText(/exact number of monthly users/i), "75000");
  userEvent.click(screen.getByRole("button", { name: /get estimate/i }));

  await screen.findByText("Serverless fits spiky traffic.");
  expect(sentBody().monthlyActiveUsers).toBe(75000);
});

test("skips questions the description already answers and sends what it detected", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse(SAMPLE_RESULT));
  renderAt(<HomePage />);

  describe("a chat app for 50k users in India");

  expect(screen.queryByText(/where are most of your users/i)).not.toBeInTheDocument();
  expect(screen.queryByText(/how many monthly users/i)).not.toBeInTheDocument();
  expect(screen.queryByText(/what kind of app/i)).not.toBeInTheDocument();
  expect(screen.getByText(/stay online if a server fails/i)).toBeInTheDocument();

  userEvent.click(screen.getByRole("button", { name: /skip/i }));

  await screen.findByText("Serverless fits spiky traffic.");
  expect(sentBody()).toEqual({
    problem: "a chat app for 50k users in India",
    region: "INDIA",
    monthlyActiveUsers: 50000,
    appType: "CHAT",
  });
});

test("skipping sends only the problem", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse(SAMPLE_RESULT));
  renderAt(<HomePage />);

  submit("something cool");

  await screen.findByText("Serverless fits spiky traffic.");
  expect(sentBody()).toEqual({ problem: "something cool" });
});

test("editing the description closes the questions", () => {
  global.fetch = jest.fn();
  renderAt(<HomePage />);

  describe("something cool");
  userEvent.type(screen.getByRole("textbox", { name: "" }), "!");

  expect(screen.queryByText(/a few quick questions/i)).not.toBeInTheDocument();
});

test("an expired cookie on page load sends the user to sign in", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse({ error: "Not signed in" }, 401));
  renderAt(<HomePage />);

  await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("/login"));
  expect(localStorage.getItem("auth_email")).toBeNull();
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
  expect(localStorage.getItem("auth_email")).toBeNull();
});
