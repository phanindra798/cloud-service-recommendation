# Cloud Service Recommendation Platform

[![CI](https://github.com/phanindra798/cloud-service-recommendation/actions/workflows/ci.yml/badge.svg)](https://github.com/phanindra798/cloud-service-recommendation/actions/workflows/ci.yml)

Describe what you want to build in plain English — "a real-time chat app
for 50k users with media sharing" — and get back a concrete cloud
architecture: recommended services across **AWS, Azure and GCP**, an
estimated monthly cost for each provider, a written explanation of why
those services fit, and a generated architecture diagram.

## Features

- **AI recommendations** — Google Gemini with structured JSON output
  (`responseSchema`), so responses are typed and validated instead of
  scraped from free text
- **Architecture reasoning** — the model explains why each service was chosen
- **Architecture diagram** — Gemini writes a Mermaid flowchart, rendered
  live in the UI
- **Scale-aware cost comparison** — Gemini estimates the app's usage
  (monthly users, requests, storage, data transfer) and every service is
  priced from it, so a 50k-user chat app is not priced like a hobby project.
  Each provider gets a per-service breakdown and a monthly total, with
  best-value highlighting (see [How costs are estimated](#how-costs-are-estimated)):
  - Azure unit prices come **live** from the public
    [Azure Retail Prices API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices)
    (no key needed) where a matching meter exists, cached for 24 hours
  - GCP unit prices load from a local CSV export if you provide one
  - otherwise list prices are used — AWS's Price List Query API needs
    SigV4-signed requests, and the adapter plug point is ready
  - every line item reports its source, so it's always clear whether a
    number is live or an estimate
- **11 services per cloud** — compute, containers, serverless, object
  storage, SQL, NoSQL, cache, CDN, API gateway, queue and user auth,
  mapped across AWS, Azure and GCP
- **Hardened authentication** — stateless JWT in an httpOnly, Secure,
  SameSite=Strict cookie with CSRF protection, BCrypt passwords, account
  lockout after repeated failures and per-IP rate limits on login/register
  (see [Security](#security))
- **Per-user history** — past recommendations stored in PostgreSQL and
  reloadable from the UI
- **Rate limiting** — sliding window, 10 requests/minute per user, to
  protect the AI quota; idle users are evicted so memory stays bounded
- **Observability** — Spring Boot Actuator + Micrometer metrics (Prometheus
  endpoint) and structured JSON logs with a request ID on every line
- **Follow-up questions** — after you describe the app, a quick chat-style
  step asks where your users are, how many there will be, what kind of app it
  is and whether it must stay up, with tap-to-select chips. Questions the
  description already answers ("a chat app for 50k users in India") are
  skipped, and every question can be skipped
- **One-command setup** — `docker compose up` runs PostgreSQL, the backend and
  an nginx frontend; CI builds the images and smoke-tests the running stack
- **Tested** — 113 backend tests (JUnit 5, Mockito, MockMvc, Testcontainers
  PostgreSQL) with a JaCoCo coverage gate, and 47 frontend tests (Jest,
  React Testing Library), run on every push by GitHub Actions
- **Graceful degradation** — if Gemini or a pricing API is down, the app
  still answers with clearly labelled baseline data instead of failing

## How it works

```
React frontend (JWT, Mermaid rendering)
        │  REST + Bearer token
        ▼
Spring Boot backend
  1. validate input, check rate limit
  2. GeminiService  → structured plan {services, reasoning, mermaid, usage}
  3. CostService    → map services to Azure/GCP equivalents, size each one
                      from the usage profile and price it
  4. save to history, return JSON
        │
        ▼
PostgreSQL (users, recommendation history)
```

The LLM only reasons over text and returns a typed plan; deterministic
code handles pricing, persistence and rendering. Every external call
(AI, pricing APIs) has a fallback, so a request never fails outright.

## Project structure

Two apps, side by side: `backend/` is the Spring Boot API, `frontend/`
is the React UI. Each has its own build tool (Maven / npm).

```
├── backend/
│   ├── src/main/java/com/cloudrec/
│   │   ├── CloudRecommendApplication.java
│   │   ├── config/          security, JWT filter, CORS, error handling
│   │   ├── controller/      /auth and /api endpoints
│   │   ├── dto/             request payloads
│   │   ├── entity/          User, RecommendationHistory
│   │   ├── repository/      Spring Data JPA repositories
│   │   └── service/         Gemini client, JWT, rate limiter
│   │       └── pricing/     Azure live pricing, GCP CSV loader, cost mapping
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── pricing/         optional gcp-prices.csv goes here
│   ├── Dockerfile           multi-stage: JDK build → JRE runtime, non-root
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── lib/             API client (cookies + CSRF), auth, follow-up questions
│   │   ├── components/      AppShell, ResultView, FollowUpQuestions, MermaidDiagram, HistoryPanel
│   │   ├── pages/           Home, Login, Register
│   │   ├── setupProxy.js    dev server → backend proxy
│   │   └── App.js           routes
│   ├── Dockerfile           multi-stage: Node build → nginx
│   └── nginx.conf           serves the app, proxies /api and /auth (one origin)
├── scripts/smoke-test.sh    end-to-end check against a running stack
├── docker-compose.yml       Postgres + backend + nginx frontend
├── .env.example
└── README.md
```

## Tech stack

| Layer      | Tech |
|------------|------|
| Frontend   | React 19, react-router 7, Mermaid |
| Backend    | Spring Boot 4 (Java 17), Spring Security, jjwt, JPA |
| Database   | PostgreSQL 16 |
| AI         | Google Gemini (structured output mode) |
| Pricing    | Azure Retail Prices API (live), GCP CSV, AWS baseline |

## Quick start with Docker

You only need Docker. One command builds and starts PostgreSQL, the backend
and the frontend:

```bash
cp .env.example .env
```

Put a secret in `.env` (`JWT_SECRET=` followed by the output of
`openssl rand -base64 48`) and, optionally, your `GEMINI_API_KEY`. Then:

```bash
docker compose up --build
```

Open http://localhost:3000. nginx serves the React app and proxies `/api`
and `/auth` to the backend on the same origin, which the `SameSite=Strict`
auth cookie requires. Data lives in the `pgdata` volume; `docker compose down -v`
wipes it.

```
browser ──► nginx :3000 ──┬── /            React build (static)
                          └── /api, /auth ──► Spring Boot :8080 ──► PostgreSQL
```

To check a running stack end to end (register, login cookie, CSRF,
recommendation, history, logout):

```bash
scripts/smoke-test.sh
```

## Running locally without Docker

You need Java 17+, Node 18+, and PostgreSQL (Docker is the easy way).
You'll also need a free Gemini API key from
[aistudio.google.com](https://aistudio.google.com) — no card required.

### 1. Start the database

```bash
docker run -d --name csr-postgres \
  -e POSTGRES_USER=csr -e POSTGRES_PASSWORD=csrpass -e POSTGRES_DB=csrdb \
  -p 5432:5432 postgres:16-alpine
```

### 2. Start the backend

Set the environment variables (see `.env.example` for the full list),
then run the Maven wrapper.

Linux / macOS:

```bash
cd backend
export DB_URL=jdbc:postgresql://localhost:5432/csrdb
export DB_USERNAME=csr
export DB_PASSWORD=csrpass
export GEMINI_API_KEY=your-key-here
export JWT_SECRET="$(openssl rand -base64 48)"
./mvnw spring-boot:run
```

Windows (PowerShell):

```powershell
cd backend
$env:DB_URL = "jdbc:postgresql://localhost:5432/csrdb"
$env:DB_USERNAME = "csr"
$env:DB_PASSWORD = "csrpass"
$env:GEMINI_API_KEY = "your-key-here"
$env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
.\mvnw.cmd spring-boot:run
```

The backend starts on http://localhost:8080.

### 3. Start the frontend

```bash
cd frontend
npm install
npm start
```

Open http://localhost:3000, register an account and try a prompt. The dev
server forwards API calls to the backend on :8080 (see `src/setupProxy.js`).

## How costs are estimated

1. **Usage profile.** Gemini returns `monthlyActiveUsers`, `requestsPerMonth`,
   `storageGb` and `dataTransferGb` alongside the services. Anything the user
   gave wins over the AI, field by field:

   | Priority | Source | Example |
   |----------|--------|---------|
   | 1 | the user's own number | "75,000 users", exact storage |
   | 2 | app-type profile × user count | e-commerce: 400 requests, 10 MB stored, 100 MB served per user / month |
   | 3 | AI estimate, scaled to the user's count | user count corrected, AI's per-user ratios kept |
   | 4 | AI estimate as-is | nothing given |

   Values are clamped to a sane range; if the AI is down a default
   small-app profile is used. Peak load is taken as 3× the monthly average
   request rate. The response tags each number with its source.
2. **Drivers.** Each service is billed by one driver, and equivalent services
   on all three clouds share it so totals are comparable:

   | Driver | Quantity | Services |
   |--------|----------|----------|
   | compute instance | ⌈peak req/s ÷ 50⌉ | EC2, ECS Fargate (and equivalents) |
   | database instance | ⌈peak req/s ÷ 500⌉ | RDS |
   | cache node | ⌈peak req/s ÷ 5,000⌉ | ElastiCache |
   | GB stored | `storageGb` | S3 |
   | GB transferred | `dataTransferGb` | CloudFront |
   | million requests | `requestsPerMonth` ÷ 1M | Lambda, DynamoDB, API Gateway, SQS |
   | thousand users | `monthlyActiveUsers` ÷ 1,000 | Cognito |

3. **High availability** (optional): app servers become at least two, and
   each database and cache instance gets a standby. Serverless and managed
   storage are already redundant, so they're unchanged.
4. **Region.** Ten regions (India, US, UK, Germany/EU, Singapore, Japan,
   Australia, Brazil, Canada, UAE) map to the nearest AWS/Azure/GCP region.
   List prices get an approximate regional uplift (e.g. ×1.05 India, ×1.45
   Brazil); live Azure prices are fetched for that exact region.
5. **Price.** `monthly cost = fixed fee + unit price × quantity`. Unit prices
   are on-demand US list prices with free tiers excluded (live Azure / CSV
   GCP prices replace them when available). Treat the totals as a
   comparison between clouds, not a quote.

The response includes the profile (`usage`) and a `breakdown` per provider:

```json
"breakdown": { "aws": [
  { "service": "ECS Fargate", "quantity": 4, "unit": "instance",
    "unitPrice": 18.02, "monthlyCost": 72.08, "source": "list-price estimate" }, …
```

## Testing

### Backend

```bash
cd backend
./mvnw verify
```

- **Unit tests** (JUnit 5 + Mockito + AssertJ): cost calculation and
  best-value selection, Azure price parsing/caching, GCP CSV loading, JWT
  issue/validate/expiry/tampering, BCrypt registration and login, the
  sliding-window rate limiter (window expiry and idle eviction driven by an
  injected `Clock`, plus a concurrency test), metric counters, and the
  request-ID filter
- **HTTP client tests**: Gemini and Azure clients run against an in-process
  stub HTTP server, covering request shape (API key, prompt, response
  schema), parsing, and every fallback path (HTTP errors, malformed JSON,
  unreachable host)
- **API tests** (`@SpringBootTest` + MockMvc on H2): register/login, JWT
  enforcement (401), input validation (400), rate limiting (429), per-user
  history, 404/405 handling, public health vs. protected metrics endpoints,
  and the real JSON log output
- **Repository tests** (`@DataJpaTest`): the top-20-newest history query
- **PostgreSQL integration test** (Testcontainers, `postgres:16-alpine`):
  the full register → login → recommend → history journey and the unique
  email constraint against a real database. It needs Docker and is skipped
  automatically without it; it always runs in CI.

JaCoCo writes a report to `backend/target/site/jacoco/`, and `mvn verify`
fails if coverage drops below 85% of lines or 75% of branches. Current
coverage without Docker (Testcontainers test skipped): **96.6% lines,
88.1% branches**.

### Frontend

```bash
cd frontend
npm test               # watch mode
npm run test:ci        # single run with coverage
```

Jest + React Testing Library tests cover routing and auth guards, the
login and register forms (validation, API errors, redirects), the home page
request flow (JWT header, 429 and error handling, expired sessions), the
result view, the Mermaid diagram fallback and the history panel.

### CI

`.github/workflows/ci.yml` runs on every push to `main` and every pull request:

- **backend** — `mvn verify` (tests, Testcontainers PostgreSQL, JaCoCo gate)
- **frontend** — Jest tests with coverage and the production build
- **docker** — builds both images, starts the full stack with
  `docker compose up --wait`, and runs `scripts/smoke-test.sh` through nginx

## Security

| Threat | Protection |
|--------|------------|
| Token theft via XSS | JWT lives in an `HttpOnly` cookie page scripts can't read; the browser only keeps the email for display |
| Token sent over plain HTTP / to other sites | Cookie is `Secure` and `SameSite=Strict` |
| Cross-site request forgery | Cookie-authenticated writes need a CSRF token (Spring Security SPA mode: `XSRF-TOKEN` cookie echoed in `X-XSRF-TOKEN`) |
| Password guessing | 5 failed logins lock an email for 15 minutes (unknown emails too, so a lock reveals nothing); 20 login/register requests per minute per IP; `429` with `Retry-After` |
| Account discovery via timing | Unknown emails still run a bcrypt check, so response time is the same |
| Weak or leaked signing key | No default `JWT_SECRET`: the app refuses to start without 32+ bytes |
| Weak passwords | 8–72 characters, stored with BCrypt |
| AI abuse / cost | 10 recommendations per minute per user |

API clients can still send `Authorization: Bearer <jwt>`; those requests carry
no ambient credentials, so they skip the CSRF check.

## Observability

**Metrics** (Micrometer, exposed at `/actuator/metrics` and in Prometheus
format at `/actuator/prometheus`, both behind JWT auth):

| Metric | Type | Tags | Meaning |
|--------|------|------|---------|
| `recommendation.latency` | timer (histogram) | `source=ai\|fallback` | end-to-end time for `/api/recommend` |
| `gemini.failures` | counter | `reason=http_error\|no_services\|exception` | Gemini calls that fell back to the baseline |
| `ratelimit.rejections` | counter | — | requests answered with 429 |
| `ratelimit.tracked.users` | gauge | — | users currently held by the rate limiter |
| `pricing.azure.fallbacks` | counter | `service` | Azure lookups that fell back to static prices |

`/actuator/health` (with `liveness`/`readiness` groups for container
probes) and `/actuator/info` are public.

**Logs** are one JSON object per line in Elastic Common Schema. Every line
written while handling a request carries `requestId` (taken from a safe
incoming `X-Request-Id` header or generated, and echoed back in the
response) and, once authenticated, `user`. Each request also gets one
access-log line with `http.method`, `http.path`, `http.status` and
`durationMs`:

```json
{"@timestamp":"…","log":{"level":"INFO","logger":"com.cloudrec.config.RequestIdFilter"},"message":"POST /api/recommend -> 200 in 1613 ms","user":"a@b.co","requestId":"71b0498f-…","http":{"method":"POST","path":"/api/recommend","status":200},"durationMs":1613,"ecs":{"version":"8.11"}}
```

Set `LOG_FORMAT=logstash` or `gelf` to switch format.

## API

| Method | Path             | Auth   | Description |
|--------|------------------|--------|-------------|
| POST   | `/auth/register` | none   | `{email, password}` → create account |
| POST   | `/auth/login`    | none   | `{email, password}` → `{email}` + httpOnly `auth_token` cookie |
| GET    | `/auth/me`       | cookie | the signed-in email, or 401 |
| POST   | `/auth/logout`   | cookie + CSRF | clears the cookie |
| POST   | `/api/recommend` | cookie + CSRF, or Bearer | `{problem, region?, monthlyActiveUsers?, appType?, storageGb?, dataTransferGb?, highAvailability?}` → services, per-service costs, usage, reasoning, diagram |
| GET    | `/api/history`   | cookie or Bearer | last 20 recommendations for the user |
| GET    | `/actuator/health` | none | health with liveness/readiness groups |
| GET    | `/actuator/prometheus` | cookie or Bearer | metrics in Prometheus format |

## Configuration notes

- All secrets come from environment variables — see `.env.example`.
  Never commit real values.
- `JWT_SECRET` is required (32+ bytes, e.g. `openssl rand -base64 48`).
  There is no default: the backend refuses to start without one, so a
  deployment can never run with a guessable signing key.
- The pricing region comes from each request (`region`, default `US`);
  live Azure prices are fetched for the matching Azure region.
- To enable GCP CSV pricing, drop a file at
  `src/main/resources/pricing/gcp-prices.csv` with rows like
  `Compute Engine,24.46` and restart the backend. Each price is per unit of
  that service's driver (per instance-month, per GB, per million requests…).
- The frontend calls the API on its own origin. In development
  `src/setupProxy.js` forwards `/api` and `/auth` to `http://localhost:8080`
  (override with `BACKEND_URL`); in production serve both from one origin,
  since the auth cookie is `SameSite=Strict`.

## Roadmap

- AWS Price List Query API integration (SigV4)
- Let users edit the usage estimate and recalculate costs
- Terraform export of the recommended stack
