#!/usr/bin/env bash
# End-to-end smoke test against a running stack (docker compose up, or the dev
# servers): page loads, register, login sets the cookie, CSRF is enforced,
# a recommendation comes back, logout clears the session.
#   BASE_URL=http://localhost:3000 scripts/smoke-test.sh
set -euo pipefail

BASE="${BASE_URL:-http://localhost:3000}"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT
EMAIL="smoke-$(date +%s)-$RANDOM@example.com"
PASSWORD="smoke-test-pass"
JSON='Content-Type: application/json'

step() { printf '\n▶ %s\n' "$1"; }
fail() { printf '✗ %s\n' "$1" >&2; exit 1; }
status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

step "frontend is served"
curl -fsS "$BASE/" | grep -q '<div id="root">' || fail "index.html not served"

step "backend is healthy through the proxy"
curl -fsS "$BASE/actuator/health" | grep -q '"UP"' || fail "health is not UP"

step "register"
[ "$(status -X POST "$BASE/auth/register" -H "$JSON" -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")" = 200 ] \
  || fail "register failed"

step "login sets an httpOnly auth cookie"
curl -fsS -c "$JAR" -b "$JAR" -X POST "$BASE/auth/login" -H "$JSON" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | grep -q "$EMAIL" || fail "login failed"
grep -q $'#HttpOnly_.*\tauth_token\t' "$JAR" || fail "auth_token cookie missing or not httpOnly"

step "session is recognised"
curl -fsS -c "$JAR" -b "$JAR" "$BASE/auth/me" | grep -q "$EMAIL" || fail "/auth/me did not return the user"
XSRF="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR")"
[ -n "$XSRF" ] || fail "no XSRF-TOKEN cookie issued"

step "writes without the CSRF token are refused"
[ "$(status -b "$JAR" -X POST "$BASE/api/recommend" -H "$JSON" -d '{"problem":"smoke test"}')" = 403 ] \
  || fail "POST without CSRF token was not refused"

step "recommendation with CSRF token"
curl -fsS -b "$JAR" -c "$JAR" -X POST "$BASE/api/recommend" -H "$JSON" -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"problem":"an online shop","region":"INDIA","monthlyActiveUsers":20000,"appType":"ECOMMERCE"}' \
  | grep -q '"breakdown"' || fail "recommendation response missing breakdown"

step "history has the recommendation"
curl -fsS -b "$JAR" "$BASE/api/history" | grep -q 'an online shop' || fail "history is empty"

step "logout clears the session"
XSRF="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR")"
[ "$(status -b "$JAR" -c "$JAR" -X POST "$BASE/auth/logout" -H "X-XSRF-TOKEN: $XSRF")" = 204 ] || fail "logout failed"
[ "$(status -b "$JAR" "$BASE/auth/me")" = 401 ] || fail "still signed in after logout"

printf '\n✓ smoke test passed against %s\n' "$BASE"
