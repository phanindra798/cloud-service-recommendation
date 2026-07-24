// JWT auth helpers — token and email live in localStorage.

export function getAuth() {
  const email = localStorage.getItem("auth_email");
  const token = localStorage.getItem("auth_token");
  return email && token ? { email, token } : null;
}

export function authHeader() {
  const auth = getAuth();
  return auth ? { Authorization: `Bearer ${auth.token}` } : {};
}

export function clearAuth() {
  localStorage.removeItem("auth_email");
  localStorage.removeItem("auth_token");
}
