// Backend base URL. Empty means same origin: in development the CRA dev server
// proxies /api and /auth to the Spring Boot backend (see src/setupProxy.js),
// and in production both are served from one origin. Same origin matters because
// the auth cookie is SameSite=Strict.
export const API_BASE = process.env.REACT_APP_API_BASE || "";

function readCookie(name) {
  const match = document.cookie.split("; ").find((c) => c.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.split("=")[1]) : null;
}

// fetch wrapper: sends the httpOnly auth cookie, and on writes echoes the
// XSRF-TOKEN cookie in the X-XSRF-TOKEN header (Spring Security CSRF check).
export function apiFetch(path, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const headers = { ...(options.headers || {}) };
  if (method !== "GET" && method !== "HEAD") {
    const csrf = readCookie("XSRF-TOKEN");
    if (csrf) headers["X-XSRF-TOKEN"] = csrf;
  }
  return fetch(`${API_BASE}${path}`, { ...options, headers, credentials: "include" });
}
