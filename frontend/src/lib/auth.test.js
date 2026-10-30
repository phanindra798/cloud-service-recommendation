import { getAuth, authHeader, clearAuth } from "./auth";

test("getAuth is null until both email and token are stored", () => {
  expect(getAuth()).toBeNull();
  localStorage.setItem("auth_email", "alice@example.com");
  expect(getAuth()).toBeNull();
  localStorage.setItem("auth_token", "abc");
  expect(getAuth()).toEqual({ email: "alice@example.com", token: "abc" });
});

test("authHeader builds a Bearer header only when signed in", () => {
  expect(authHeader()).toEqual({});
  localStorage.setItem("auth_email", "alice@example.com");
  localStorage.setItem("auth_token", "abc");
  expect(authHeader()).toEqual({ Authorization: "Bearer abc" });
});

test("clearAuth signs the user out", () => {
  localStorage.setItem("auth_email", "alice@example.com");
  localStorage.setItem("auth_token", "abc");
  clearAuth();
  expect(getAuth()).toBeNull();
});
