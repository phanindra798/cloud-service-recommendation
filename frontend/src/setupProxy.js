// Development only: the CRA dev server forwards API calls to the Spring Boot
// backend so the browser sees a single origin (the auth cookie is SameSite=Strict).
const { createProxyMiddleware } = require("http-proxy-middleware");

module.exports = function (app) {
  app.use(
    ["/api", "/auth", "/actuator"],
    createProxyMiddleware({ target: process.env.BACKEND_URL || "http://localhost:8080", changeOrigin: false })
  );
};
