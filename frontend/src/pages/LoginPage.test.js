import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import LoginPage from "./LoginPage";
import { renderAt, jsonResponse, textResponse } from "../test/utils";

function fillAndSubmit(email, password) {
  userEvent.type(screen.getByLabelText(/email address/i), email);
  userEvent.type(screen.getByLabelText(/^password$/i), password);
  userEvent.click(screen.getByRole("button", { name: /sign in/i }));
}

test("successful login remembers the email (not a token) and goes home", async () => {
  global.fetch = jest.fn().mockResolvedValue(jsonResponse({ email: "alice@example.com" }));
  renderAt(<LoginPage />, { path: "/login", route: "/login" });

  fillAndSubmit("alice@example.com", "secret123");

  await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent(/^\/$/));
  expect(localStorage.getItem("auth_email")).toBe("alice@example.com");
  expect(localStorage.getItem("auth_token")).toBeNull();

  const [url, options] = global.fetch.mock.calls[0];
  expect(url).toMatch(/\/auth\/login$/);
  expect(JSON.parse(options.body)).toEqual({ email: "alice@example.com", password: "secret123" });
});

test("shows the server's error on bad credentials", async () => {
  global.fetch = jest.fn().mockResolvedValue(textResponse("Invalid email or password", 401));
  renderAt(<LoginPage />, { path: "/login", route: "/login" });

  fillAndSubmit("alice@example.com", "wrong");

  expect(await screen.findByText("Invalid email or password")).toBeInTheDocument();
  expect(localStorage.getItem("auth_email")).toBeNull();
});

test("requires both fields before calling the API", () => {
  global.fetch = jest.fn();
  renderAt(<LoginPage />, { path: "/login", route: "/login" });

  userEvent.click(screen.getByRole("button", { name: /sign in/i }));

  expect(screen.getByText("Email and password are required.")).toBeInTheDocument();
  expect(global.fetch).not.toHaveBeenCalled();
});

test("explains when the backend is unreachable", async () => {
  global.fetch = jest.fn().mockRejectedValue(new TypeError("Failed to fetch"));
  renderAt(<LoginPage />, { path: "/login", route: "/login" });

  fillAndSubmit("alice@example.com", "secret123");

  expect(await screen.findByText(/cannot reach the auth server/i)).toBeInTheDocument();
});
