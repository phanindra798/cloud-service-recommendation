import { getAuth, setAuth, clearAuth } from "./auth";

test("only the email is kept in the browser, never the token", () => {
  expect(getAuth()).toBeNull();
  setAuth("alice@example.com");
  expect(getAuth()).toEqual({ email: "alice@example.com" });
  expect(Object.keys(localStorage)).toEqual(["auth_email"]);
});

test("clearAuth signs the user out and removes tokens left by older versions", () => {
  setAuth("alice@example.com");
  localStorage.setItem("auth_token", "old-token");
  clearAuth();
  expect(getAuth()).toBeNull();
  expect(localStorage.getItem("auth_token")).toBeNull();
});
