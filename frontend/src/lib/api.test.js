import { apiFetch } from "./api";

beforeEach(() => {
  global.fetch = jest.fn().mockResolvedValue({ ok: true });
  document.cookie = "XSRF-TOKEN=csrf-abc";
});

afterEach(() => {
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
});

test("sends cookies with every request, to the same origin", async () => {
  await apiFetch("/api/history");

  const [url, options] = global.fetch.mock.calls[0];
  expect(url).toBe("/api/history");
  expect(options.credentials).toBe("include");
});

test("echoes the CSRF cookie on writes only", async () => {
  await apiFetch("/api/history");
  await apiFetch("/api/recommend", { method: "POST", headers: { "Content-Type": "application/json" } });

  expect(global.fetch.mock.calls[0][1].headers["X-XSRF-TOKEN"]).toBeUndefined();
  expect(global.fetch.mock.calls[1][1].headers).toEqual({
    "Content-Type": "application/json",
    "X-XSRF-TOKEN": "csrf-abc",
  });
});
