import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import RegisterPage from "./RegisterPage";
import { renderAt, textResponse } from "../test/utils";

function fill(email, password, confirm) {
  userEvent.type(screen.getByLabelText(/email address/i), email);
  userEvent.type(screen.getByLabelText(/^password$/i), password);
  userEvent.type(screen.getByLabelText(/confirm password/i), confirm);
  userEvent.click(screen.getByRole("button", { name: /create account/i }));
}

test("rejects mismatched passwords without calling the API", () => {
  global.fetch = jest.fn();
  renderAt(<RegisterPage />, { path: "/register", route: "/register" });

  fill("alice@example.com", "secret123", "secret124");

  expect(screen.getByText("Passwords do not match.")).toBeInTheDocument();
  expect(global.fetch).not.toHaveBeenCalled();
});

test("rejects short passwords", () => {
  global.fetch = jest.fn();
  renderAt(<RegisterPage />, { path: "/register", route: "/register" });

  fill("alice@example.com", "123", "123");

  expect(screen.getByText("Password must be at least 6 characters.")).toBeInTheDocument();
});

test("successful registration shows confirmation and redirects to login", async () => {
  jest.useFakeTimers();
  global.fetch = jest.fn().mockResolvedValue(textResponse("Registered successfully", 200));
  renderAt(<RegisterPage />, { path: "/register", route: "/register" });

  fill("alice@example.com", "secret123", "secret123");

  expect(await screen.findByText(/account created/i)).toBeInTheDocument();
  act(() => jest.advanceTimersByTime(2500));
  await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("/login"));
  jest.useRealTimers();
});

test("shows conflict message for an existing account", async () => {
  global.fetch = jest.fn().mockResolvedValue(textResponse("User already exists", 409));
  renderAt(<RegisterPage />, { path: "/register", route: "/register" });

  fill("alice@example.com", "secret123", "secret123");

  expect(await screen.findByText("User already exists")).toBeInTheDocument();
});
