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
- **Multi-cloud cost comparison** — monthly estimate per provider with
  best-value highlighting:
  - Azure prices come **live** from the public
    [Azure Retail Prices API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices)
    (no key needed), cached for 24 hours
  - GCP prices load from a local CSV export if you provide one
  - AWS uses a static baseline for now — the Price List Query API needs
    SigV4-signed requests, and the adapter plug point is ready
  - every response reports its `pricingSource`, so it's always clear
    whether a number is live or baseline
- **JWT authentication** — stateless Spring Security with BCrypt password
  hashing and 24-hour token expiry
- **Per-user history** — past recommendations stored in PostgreSQL and
  reloadable from the UI
- **Rate limiting** — sliding window, 10 requests/minute per user, to
  protect the AI quota; idle users are evicted so memory stays bounded
- **Observability** — Spring Boot Actuator + Micrometer metrics (Prometheus
  endpoint) and structured JSON logs with a request ID on every line
- **Tested** — 70 backend tests (JUnit 5, Mockito, MockMvc, Testcontainers
  PostgreSQL) with a JaCoCo coverage gate, and 30 frontend tests (Jest,
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
  2. GeminiService  → structured plan {services, reasoning, mermaid}
  3. CostService    → map services to Azure/GCP equivalents, price them
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
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   └── src/
│       ├── lib/             API base URL, auth helpers
│       ├── components/      AppShell, ResultView, MermaidDiagram, HistoryPanel
│       ├── pages/           Home, Login, Register
│       └── App.js           routes
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

## Running locally

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
./mvnw spring-boot:run
```

Windows (PowerShell):

```powershell
cd backend
$env:DB_URL = "jdbc:postgresql://localhost:5432/csrdb"
$env:DB_USERNAME = "csr"
$env:DB_PASSWORD = "csrpass"
$env:GEMINI_API_KEY = "your-key-here"
.\mvnw.cmd spring-boot:run
```

The backend starts on http://localhost:8080.

### 3. Start the frontend

```bash
cd frontend
npm install
npm start
```

Open http://localhost:3000, register an account and try a prompt.

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
coverage without Docker (Testcontainers test skipped): **95.2% lines,
87.3% branches**.

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

`.github/workflows/ci.yml` runs both suites, the production frontend build,
and uploads both coverage reports on every push to `main` and every pull
request.

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
| POST   | `/auth/login`    | none   | `{email, password}` → `{token, email}` |
| POST   | `/api/recommend` | Bearer | `{problem}` → services, costs, reasoning, diagram |
| GET    | `/api/history`   | Bearer | last 20 recommendations for the user |
| GET    | `/actuator/health` | none | health with liveness/readiness groups |
| GET    | `/actuator/prometheus` | Bearer | metrics in Prometheus format |

## Configuration notes

- All secrets come from environment variables — see `.env.example`.
  Never commit real values.
- `JWT_SECRET` has a development-only default; always set a real one
  (32+ bytes, e.g. `openssl rand -base64 48`) in any deployed environment.
- `AZURE_PRICING_REGION` changes which region live Azure prices are
  fetched for (default `centralindia`).
- To enable GCP CSV pricing, drop a file at
  `src/main/resources/pricing/gcp-prices.csv` with rows like
  `Compute Engine,6.85` and restart the backend.
- The frontend reads `REACT_APP_API_BASE` at build time if the backend
  isn't on localhost:8080.

## Roadmap

- AWS Price List Query API integration (SigV4)
- Region selector for cost estimates
- Clarifying-questions flow before recommending
- Terraform export of the recommended stack
