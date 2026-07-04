# Shadow LLM Proxy — Interview Guide

Use this document to explain what was built, why, and how it works in a technical interview.

---

## 30-Second Elevator Pitch

> I built a **Shadow LLM Proxy** in **Java 21 / Spring Boot 3** that sits in front of LLM traffic. Customer requests hit a **Primary model synchronously** (~100 ms) and get an immediate response. In the background, on **Java 21 virtual threads**, the same prompt is sent to a **Candidate model** (~500 ms). Outputs are **normalized and compared**; mismatches are logged as structured JSON and tracked in **real-time metrics**. Candidate latency, failures, and timeouts **never affect** the user-facing response. The system is containerized, tested end-to-end, CI-validated, and deployable to **DigitalOcean App Platform** with optional **Redis-backed cluster metrics**.

---

## 1. Problem Statement

When rolling out a new LLM (model upgrade, vendor change, prompt change), you need to validate it against **real production traffic** without routing users to it.

**Shadow testing** solves this:

| Path | Who sees it | Latency budget | Failure impact |
|------|-------------|----------------|----------------|
| **Primary** | Customer | Must stay low (~100–500 ms) | User-facing errors |
| **Candidate (shadow)** | Internal only | Can be slower (~500 ms+) | Must be zero impact on customer |

This proxy implements that pattern as a production-ready HTTP service.

---

## 2. What Was Built (Feature Checklist)

### Core proxy behavior
- [x] `POST /generate` — synchronous primary LLM call, immediate 200 response
- [x] Fire-and-forget async shadow call to candidate LLM
- [x] UUID `request_id` per request + `X-Request-Id` response header
- [x] Separate HTTP clients with different timeouts (primary 500 ms, candidate 2000 ms)
- [x] Mock LLM endpoints for local/dev (`/internal/mock/primary`, `/internal/mock/candidate`)

### Async & threading
- [x] Java 21 virtual threads via `VirtualThreadTaskExecutor`
- [x] `@Async("shadowTaskExecutor")` on `ShadowProcessor`
- [x] Shadow work survives client disconnect (scheduled before response flush)
- [x] Concurrency limiter (`llm.shadow.max-concurrency`) — drops excess shadow work
- [x] Resilience4j circuit breaker on candidate calls

### Comparison & observability
- [x] `OutputNormalizer` — markdown strip, JSON canonicalization, whitespace collapse, lowercase, punctuation strip
- [x] Structured `MismatchLog` JSON on WARN for disagreements
- [x] `GET /metrics` — match rate, failures, skipped/dropped counts
- [x] Pluggable `CounterStore`: in-memory (per instance) or Redis (cluster-wide)
- [x] Micrometer + Prometheus at `/actuator/prometheus`
- [x] Spring Actuator health at `/actuator/health`

### Production hardening
- [x] Probabilistic shadow sampling (100% local, 10% prod)
- [x] API key auth on `/generate` and `/metrics` in `prod` profile
- [x] Rate limiting (Bucket4j filter)
- [x] OpenAPI / Swagger docs
- [x] RFC 7807 `ProblemDetail` for validation errors
- [x] OWASP dependency check in Gradle

### DevOps
- [x] Gradle build + Spring Boot fat JAR
- [x] Multi-stage Dockerfile (Temurin 21 Alpine)
- [x] GitHub Actions CI (`./gradlew build` on Java 21)
- [x] DigitalOcean App Platform deploy (`app.yaml`, `deploy/agent.sh`)
- [x] Automated Redis provisioning for multi-instance metrics

### Testing
- [x] Integration test: `/generate` responds in **< 150 ms** while candidate takes 500 ms+
- [x] Unit tests for normalization edge cases
- [x] Metrics, controller, and shadow processor tests

---

## 3. Architecture (Explain This Flow)

```
Client
  │
  ▼ POST /generate
ProxyController
  │
  ▼
ProxyOrchestrator ──────────────────────────────────────────────┐
  │                                                              │
  ├─► Primary LLM (sync, ~100ms) ──► LLMResponse                │
  │                                                              │
  ├─► ShadowProcessor.processShadow() [fire-and-forget]         │
  │         │                                                    │
  │         ▼ (virtual thread: shadow-vt-*)                      │
  │    [sampling check] ──skip──► shadow_skipped++            │
  │    [concurrency check] ──drop──► shadow_dropped++           │
  │    Candidate LLM (~500ms)                                    │
  │    OutputNormalizer.compare()                                │
  │    match ──► matches++    mismatch ──► mismatches++ + log   │
  │    failure ──► candidate_failures++ (client already got 200)│
  │                                                              │
  └─► 200 OK + primary response ◄───────────────────────────────┘
```

### Thread model (critical interview point)

| Phase | Thread | Blocking? |
|-------|--------|-----------|
| Primary call + HTTP response | `tomcat-handler-*` (servlet) | Yes, but only primary (~100 ms) |
| Shadow / candidate / compare | `shadow-vt-*` (virtual) | Isolated; never blocks client |

**Proof:** Integration test asserts response time < 150 ms with 500 ms candidate delay.

---

## 4. Tech Stack

| Layer | Technology | Why |
|-------|------------|-----|
| Language | Java 21 | Virtual threads (Project Loom) for cheap async I/O |
| Framework | Spring Boot 3.3.x | REST, async, config, actuator, security |
| Build | Gradle | Wrapper included, Java 21 toolchain |
| HTTP client | Spring `RestClient` + Apache HttpClient 5 | Explicit per-upstream timeouts |
| Async | `@EnableAsync` + `VirtualThreadTaskExecutor` | Lightweight shadow tasks at scale |
| Resilience | Resilience4j circuit breaker | Stop hammering failing candidate |
| Rate limit | Bucket4j | Protect public endpoints |
| Metrics storage | `AtomicLong` or Redis `INCR` | Lock-free, cluster-safe option |
| Observability | Micrometer, Prometheus, structured logs | Ops-ready |
| Container | Docker multi-stage, Temurin 21 Alpine | Small prod image |
| Deploy | DigitalOcean App Platform + `doctl` | Managed HTTPS, health checks, auto-deploy |
| CI | GitHub Actions | `./gradlew build` on every push/PR |

---

## 5. Key Design Decisions (and Why)

### 5.1 Virtual threads instead of platform thread pool

**Decision:** Use `VirtualThreadTaskExecutor("shadow-vt-")` for shadow work.

**Why:** Shadow calls are I/O-bound (HTTP to LLM). Virtual threads let you run many concurrent shadow comparisons without tying up OS threads. Cheaper than a fixed platform thread pool at high traffic.

**Interview line:** *"I chose virtual threads because shadow work is I/O-bound and fire-and-forget — we need high concurrency without blocking servlet threads or sizing a thread pool manually."*

### 5.2 Fire-and-forget, never await shadow

**Decision:** `ProxyOrchestrator` calls `shadowProcessor.processShadow(...)` and immediately returns.

**Why:** Hard requirement — candidate latency must never add to user latency.

**Interview line:** *"The orchestrator never joins or waits on the shadow future. Primary path is strictly synchronous; shadow is scheduled and forgotten."*

### 5.3 Separate RestClient beans with different timeouts

**Decision:** `primaryRestClient` (500 ms) vs `candidateRestClient` (2000 ms).

**Why:** Primary has a tight SLA; candidate can tolerate longer waits since it's background-only.

### 5.4 Normalization before string equality

**Decision:** Multi-step pipeline in `OutputNormalizer` before comparing.

**Why:** LLMs often differ only in formatting (JSON key order, markdown fences, trailing punctuation). Without normalization you get false-positive mismatches.

**Pipeline:**
1. Extract content from chat-completion JSON envelope
2. Strip ` ```json ` blocks
3. Parse JSON → re-serialize with sorted keys
4. Collapse whitespace
5. Lowercase + strip trailing punctuation (for plain text)

### 5.5 Probistic sampling in production

**Decision:** `llm.shadow.sample-rate: 0.1` in `prod` profile (10% of traffic).

**Why:** Shadow doubles LLM cost/latency load internally. Sampling gives statistically useful match rates without shadowing every request.

**Metric:** Skipped requests increment `shadow_skipped`.

### 5.6 Pluggable CounterStore (memory vs Redis)

**Decision:** Abstract counter interface with in-memory and Redis implementations.

**Why:** Behind a load balancer, each JVM has its own counters. Repeated `GET /metrics` can return different numbers. Redis gives cluster-wide totals (`scope: "cluster"`).

### 5.7 Candidate failure isolation

**Decision:** All candidate exceptions caught inside `ShadowProcessor`; increment `candidate_failures` only.

**Why:** Client already received 200 with primary content. Shadow failures are observability events, not user errors.

### 5.8 Circuit breaker on candidate

**Decision:** Resilience4j `candidateLlm` circuit breaker wraps candidate calls.

**Why:** If candidate is down, fail fast instead of queuing thousands of doomed shadow requests.

---

## 6. API Reference (Know These Endpoints)

### `POST /generate`

**Request:**
```json
{
  "prompt": "Explain virtual threads",
  "force_mismatch": false,
  "simulate_candidate_failure": false
}
```

**Response (200):**
```json
{
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "model": "primary-mock",
  "content": "Answer: Explain virtual threads",
  "latency_ms": 102
}
```

**Errors:**
- `400` — blank/missing prompt
- `504` — primary timeout
- `401` — missing/invalid API key (prod)

**Test flags** (mock profile only in prod — stripped in `ProxyOrchestrator.sanitizeDevFlags`):
- `force_mismatch: true` — candidate returns different content
- `simulate_candidate_failure: true` — candidate throws; primary still succeeds

### `GET /metrics`

```json
{
  "total_shadow_requests": 10,
  "matches": 8,
  "mismatches": 2,
  "candidate_failures": 0,
  "shadow_dropped": 0,
  "shadow_skipped": 90,
  "real_time_match_rate": 80.0,
  "instance_id": "api-7f3a2b1c",
  "scope": "instance"
}
```

**Important:** Metrics update **after** async shadow completes (~500 ms), not when `/generate` returns.

### Other endpoints
- `GET /actuator/health` — load balancer health check
- `GET /actuator/prometheus` — Prometheus scrape (auth in prod)
- `/swagger-ui.html` — OpenAPI UI

---

## 7. Configuration & Profiles

| Profile | Shadow sample rate | Security | Use case |
|---------|-------------------|----------|----------|
| `mock` (default local) | 100% | Disabled | Dev, integration tests |
| `prod` | 10% | API key required | App Platform |
| `dev` | Inherits base | Configurable | Verbose logging |

**Key properties:**
```yaml
llm:
  shadow:
    enabled: true
    sample-rate: 1.0      # prod overrides to 0.1
    max-concurrency: 100
  timeout:
    primary-ms: 500
    candidate-ms: 2000

metrics:
  store: memory           # or redis for cluster totals
```

**Env vars for production:**
- `SPRING_PROFILES_ACTIVE=prod`
- `PROXY_API_KEY` — API key for public endpoints
- `LLM_PRIMARY_URL` / `LLM_CANDIDATE_URL` — real LLM endpoints
- `METRICS_STORE=redis` + `REDIS_HOST/PORT/PASSWORD`
- `LLM_SHADOW_SAMPLE_RATE=1.0` — override for demos

---

## 8. Code Walkthrough (Files to Reference)

| File | What to say |
|------|-------------|
| `ProxyOrchestrator.java` | Entry point for request flow; primary sync, shadow schedule, dev flag sanitization |
| `ShadowProcessor.java` | `@Async` shadow worker; sampling, concurrency, circuit breaker, compare, mismatch log |
| `OutputNormalizer.java` | Deterministic comparison pipeline |
| `MetricsTracker.java` + `CounterStore` | Thread-safe counters, match rate calculation |
| `AsyncConfig.java` | `VirtualThreadTaskExecutor`, concurrency limiter |
| `RestClientConfig.java` | Separate clients/timeouts for primary vs candidate |
| `HttpLlmUpstreamClient.java` | Production HTTP calls to external LLM URLs |
| `DirectMockLlmClient.java` | In-process mock for local profile |
| `SecurityConfig.java` | Optional `X-API-Key` auth in prod |
| `ProxyIntegrationTest.java` | Proves < 150 ms response under 500 ms candidate delay |

---

## 9. Testing Strategy (Proof Points)

```bash
./gradlew test
```

| Test | What it proves |
|------|----------------|
| `ProxyIntegrationTest.generate_respondsInUnder150msWhileCandidateRunsInBackground` | Async decoupling works |
| `ProxyIntegrationTest.metrics_reflectsShadowComparisonAfterBackgroundProcessing` | Metrics update after shadow |
| `OutputNormalizerTest` | Formatting differences don't false-match |
| `ShadowProcessorTest` | Compare logic and metric recording |
| `MetricsTrackerTest` | Lock-free match rate math |
| `ProxyControllerTest` | API contract |

**Live demo commands:**
```bash
./gradlew bootRun

# Primary path (~100ms)
curl -s -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Hello"}'

# Metrics (wait ~800ms after generate)
curl -s http://localhost:8080/metrics

# Force mismatch
curl -s -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"test","force_mismatch":true}'

# Candidate failure — primary still 200
curl -s -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"test","simulate_candidate_failure":true}'
```

---

## 10. Deployment Story

1. **Build:** `./gradlew build` → fat JAR; Docker multi-stage build
2. **CI:** GitHub Actions runs tests on every push
3. **Deploy:** `./deploy/agent.sh` with DigitalOcean token
   - Creates/updates App Platform app
   - Provisions managed Redis for cluster metrics
   - Sets `METRICS_STORE=redis`, `SPRING_PROFILES_ACTIVE=prod`
4. **Health:** App Platform hits `/actuator/health`
5. **Verify:** curl with `X-API-Key` header

**Production swap:** Replace mock URLs with real LLM API endpoints; store API keys as platform secrets.

---

## 11. Likely Interview Questions & Answers

### Q: What is shadow testing?

**A:** Running a candidate model against duplicate production traffic in the background. Users only see the primary model. You compare outputs to decide if the candidate is safe to promote.

### Q: How do you guarantee the candidate doesn't slow down users?

**A:** Three layers: (1) orchestrator never awaits shadow, (2) shadow runs on separate virtual threads, (3) integration test asserts < 150 ms response with 500 ms candidate. Debug logs show servlet thread schedules shadow, virtual thread runs candidate.

### Q: What happens if the client disconnects?

**A:** Shadow is scheduled on a virtual thread before the HTTP response is sent. It continues even if the client closes the connection.

### Q: What happens if the candidate times out or crashes?

**A:** Caught in `ShadowProcessor`. Increments `candidate_failures`. Client already has primary 200. Circuit breaker prevents cascade when candidate is unhealthy.

### Q: How do you avoid false mismatch alerts?

**A:** `OutputNormalizer` — strip markdown, canonicalize JSON key order, normalize whitespace/case/punctuation. Only log mismatch after normalization.

### Q: Why virtual threads vs `@Async` on a thread pool?

**A:** Shadow work is I/O-bound HTTP. Virtual threads give massive concurrency cheaply. Platform thread pools need careful sizing; virtual threads scale with blocked I/O automatically.

### Q: How do metrics work with multiple instances?

**A:** Default in-memory counters are per-JVM (`scope: instance`). Behind a load balancer, `/metrics` may vary by instance. Redis-backed `CounterStore` aggregates cluster-wide (`scope: cluster`). Response includes `instance_id` for debugging.

### Q: Why 10% sampling in prod?

**A:** Cost and load — shadowing every request doubles LLM calls. 10% gives statistically meaningful match rates at lower cost. Configurable via `LLM_SHADOW_SAMPLE_RATE`.

### Q: How would you promote candidate to primary?

**A:** This proxy doesn't auto-promote — it observability for the decision. Ops watches `real_time_match_rate`, mismatch logs, and failure rate. When confident, you'd flip routing config (primary URL → candidate URL) or use a feature flag outside this service.

### Q: What would you add next?

**A:** Semantic similarity (embeddings) instead of string equality; request/response storage for offline analysis; distributed tracing (OpenTelemetry); shadow queue with backpressure (Kafka/SQS) for very high throughput; A/B metrics dashboards.

### Q: How is this different from a simple proxy?

**A:** A simple proxy forwards to one backend. This orchestrates **two** backends with **different SLAs**, async decoupling, normalization, sampling, resilience patterns, and real-time comparison metrics — purpose-built for safe LLM model migration.

---

## 12. Trade-offs & Honest Limitations

| Limitation | Current behavior | Mitigation |
|------------|------------------|------------|
| String equality ≠ semantic equivalence | Exact match after normalization | Future: embedding similarity |
| Sampling misses some traffic | 90% not shadowed in prod | Adjustable sample rate |
| In-memory metrics split by instance | Fluctuating `/metrics` | Redis or single instance |
| Mock LLMs in default deploy | Loopback mock endpoints | Env vars for real URLs |
| No request replay/storage | Mismatch log only | Add object storage |

---

## 13. One-Minute Demo Script

1. Start app: `./gradlew bootRun`
2. Generate: show ~100 ms response with primary content
3. Metrics immediately: `total_shadow_requests` may increment, `matches` not yet
4. Wait 1s, metrics again: `matches` updated, `real_time_match_rate` shown
5. Force mismatch: show `mismatches` increment
6. Simulate candidate failure: show 200 OK + `candidate_failures` increment
7. Mention: prod uses 10% sampling, API key, Redis cluster metrics

---

## 14. Related Docs in This Repo

| Document | Contents |
|----------|----------|
| [README.md](README.md) | Quick start, API, architecture diagram |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Deep dive: lifecycle, components, config |
| [BUILD.md](BUILD.md) | Build, test, run commands |
| [DEPLOY.md](DEPLOY.md) | DigitalOcean App Platform guide |
| [AgentInstructions.md](AgentInstructions.md) | Original requirements spec |
| [deploy/REQUIREMENTS.md](deploy/REQUIREMENTS.md) | Deploy agent credentials |

---

## 15. Summary Table (Quick Reference)

| Aspect | Implementation |
|--------|----------------|
| **Purpose** | Shadow-test candidate LLM against production traffic |
| **User latency** | Primary only (~100 ms mock) |
| **Shadow latency** | Background (~500 ms mock), non-blocking |
| **Threading** | Java 21 virtual threads + `@Async` |
| **Comparison** | Normalized string equality |
| **Metrics** | Real-time, memory or Redis |
| **Resilience** | Circuit breaker, concurrency limit, sampling |
| **Security** | API key (prod), rate limiting |
| **Tests** | < 150 ms integration proof |
| **Deploy** | Docker + DigitalOcean App Platform |
