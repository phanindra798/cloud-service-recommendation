// The session itself lives in an httpOnly cookie that scripts can't read.
// Only the signed-in email is kept here, to drive the UI; the backend decides
// who is actually signed in (a 401 means the cookie is missing or expired).

export function getAuth() {
  const email = localStorage.getItem("auth_email");
  return email ? { email } : null;
}

export function setAuth(email) {
  localStorage.setItem("auth_email", email);
}

export function clearAuth() {
  localStorage.removeItem("auth_email");
  localStorage.removeItem("auth_token"); // left behind by older versions
}
