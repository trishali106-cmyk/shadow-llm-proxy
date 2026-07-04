# Shadow LLM Proxy — Architecture

This document describes the end-to-end architecture of **shadow-llm-proxy**: a Java 21 / Spring Boot 3 service that serves customer traffic through a **Primary LLM** while asynchronously comparing responses against a **Candidate LLM** in the background (shadow testing).

---

## Table of Contents

1. [Purpose](#purpose)
2. [High-Level Overview](#high-level-overview)
3. [Request Lifecycle](#request-lifecycle)
4. [Component Reference](#component-reference)
5. [Primary vs Candidate Models](#primary-vs-candidate-models)
6. [Threading & Async Design](#threading--async-design)
7. [Output Normalization](#output-normalization)
8. [Metrics & Observability](#metrics--observability)
9. [API Reference](#api-reference)
10. [Configuration](#configuration)
11. [Error Handling](#error-handling)
12. [Testing Strategy](#testing-strategy)
13. [Build, CI/CD & Deployment](#build-cicd--deployment)
14. [Verifying Async Behavior](#verifying-async-behavior)
15. [Project Layout](#project-layout)

---

## Purpose

Shadow testing lets you evaluate a new LLM (the **candidate**) against production traffic without routing users to it. This proxy:

- Returns the **primary** model response to the client **synchronously** (low latency).
- Invokes the **candidate** model **asynchronously** on a separate virtual thread.
- **Normalizes** both outputs and compares them to detect semantic differences.
- **Logs mismatches** and exposes **real-time metrics** for match rate and failure tracking.

Candidate latency, timeouts, and failures must **never** affect the primary response the client receives.

---

## High-Level Overview

```mermaid
flowchart TB
    subgraph Public API
        PC[ProxyController]
        PO[ProxyOrchestrator]
    end

    subgraph Sync Path["Synchronous path (~100ms)"]
        LMS_P[LLMMockService.generatePrimary]
        MOCK_P["/internal/mock/primary<br/>model: primary-mock"]
    end

    subgraph Async Path["Asynchronous shadow path (~500ms)"]
        SP[ShadowProcessor<br/>@Async shadowTaskExecutor]
        LMS_C[LLMMockService.generateCandidate]
        MOCK_C["/internal/mock/candidate<br/>model: candidate-mock"]
        ON[OutputNormalizer]
        MT[MetricsTracker]
    end

    Client([Client]) -->|POST /generate| PC
    PC --> PO
    PO --> LMS_P --> MOCK_P
    MOCK_P --> PO
    PO -->|fire-and-forget| SP
    PO -->|200 OK + primary response| Client

    SP --> LMS_C --> MOCK_C
    MOCK_C --> SP
    SP --> ON
    ON -->|match / mismatch| MT

    Client2([Client / Operator]) -->|GET /metrics| PC
    PC --> MT
```

### Technology Stack

| Layer | Choice |
|-------|--------|
| Runtime | Java 21 (virtual threads) |
| Framework | Spring Boot 3.3.x |
| Build | Gradle |
| HTTP client | Spring `RestClient` |
| Async | `@Async` + `VirtualThreadTaskExecutor` |
| Metrics | In-memory `AtomicLong` counters |
| Container | Docker (Eclipse Temurin 21 Alpine) |

---

## Request Lifecycle

```mermaid
sequenceDiagram
    participant Client
    participant ProxyController
    participant ProxyOrchestrator
    participant Primary as LLMMockService (Primary)
    participant VT as Virtual Thread (shadow-vt-*)
    participant Candidate as LLMMockService (Candidate)
    participant Normalizer as OutputNormalizer
    participant Metrics as MetricsTracker

    Client->>ProxyController: POST /generate { prompt }
    ProxyController->>ProxyOrchestrator: generate(request)
    ProxyOrchestrator->>Primary: generatePrimary(requestId, request)
    Note over Primary: ~100ms simulated delay
    Primary-->>ProxyOrchestrator: LLMResponse (primary-mock)

    ProxyOrchestrator->>VT: processShadow() [no await]
    ProxyOrchestrator-->>Client: 200 OK + X-Request-Id

    Note over VT,Candidate: Decoupled from servlet thread;<br/>survives client disconnect

    VT->>Metrics: recordShadowRequest()
    VT->>Candidate: generateCandidate(requestId, request)
    Note over Candidate: ~500ms simulated delay
    Candidate-->>VT: LLMResponse (candidate-mock)
    VT->>Normalizer: normalizeAndCompare(primary, candidate)

    alt outputs match after normalization
        VT->>Metrics: recordMatch()
    else outputs differ
        VT->>Metrics: recordMismatch()
        VT->>VT: log MismatchLog JSON (WARN)
    end
```

### Step-by-step

1. **Ingress** — `ProxyController` accepts `POST /generate` with a validated `PromptRequest`.
2. **Correlation** — `ProxyOrchestrator` assigns a UUID `requestId` (returned as `X-Request-Id`).
3. **Primary call** — `LLMMockService` POSTs to `/internal/mock/primary` via `primaryRestClient` (~100 ms).
4. **Shadow schedule** — `ShadowProcessor.processShadow()` is invoked; the orchestrator does **not** wait for it.
5. **Client response** — Primary `LLMResponse` is returned immediately.
6. **Background work** — On a `shadow-vt-*` virtual thread, the candidate is called (~500 ms), outputs are normalized and compared, and metrics/logs are updated.

---

## Component Reference

### Controllers

| Class | Role |
|-------|------|
| `ProxyController` | Public REST API: `POST /generate`, `GET /metrics` |
| `MockLlmInternalController` | In-process mock LLM upstreams at `/internal/mock/primary` and `/internal/mock/candidate` |

### Services

| Class | Role |
|-------|------|
| `ProxyOrchestrator` | Coordinates sync primary path + fire-and-forget shadow scheduling |
| `LLMMockService` | HTTP facade; two `RestClient` beans with separate timeouts |
| `ShadowProcessor` | `@Async` shadow worker: candidate call, compare, metrics, mismatch logging |
| `MetricsTracker` | Lock-free counters (`AtomicLong`) for shadow statistics |
| `ServerPortResolver` | Resolves embedded server port for loopback mock URLs |

### Configuration

| Class | Role |
|-------|------|
| `AsyncConfig` | `VirtualThreadTaskExecutor` bean + `LlmTimingProperties` |
| `RestClientConfig` | `primaryRestClient` (500 ms) and `candidateRestClient` (2000 ms) |

### Utilities & Models

| Class | Role |
|-------|------|
| `OutputNormalizer` | Deterministic text/JSON normalization before comparison |
| `PromptRequest` | Inbound DTO with prompt and test flags |
| `LLMResponse` | Standard response: `request_id`, `model`, `content`, `latency_ms` |
| `MetricsSnapshot` | Outbound metrics DTO for `GET /metrics` |
| `MismatchLog` | Structured WARN log payload on disagreement |
| `GlobalExceptionHandler` | Maps validation errors to RFC 7807 `ProblemDetail` |

---

## Primary vs Candidate Models

Both models are simulated by `MockLlmInternalController` inside the same JVM. In production, the same `RestClient` pattern would target external URLs.

| | Primary | Candidate |
|---|---------|-----------|
| **Model name** | `primary-mock` | `candidate-mock` |
| **Endpoint** | `POST /internal/mock/primary` | `POST /internal/mock/candidate` |
| **Simulated delay** | 100 ms (configurable) | 500 ms (configurable) |
| **HTTP timeout** | 500 ms | 2000 ms |
| **RestClient bean** | `primaryRestClient` | `candidateRestClient` |
| **Invocation** | Synchronous in `ProxyOrchestrator` | Async in `ShadowProcessor` |
| **Returned to client?** | Yes | No |
| **Default content** | `"Answer: " + prompt` | Same, unless `force_mismatch` |

### Mock response behavior

- **Primary** always returns `"Answer: {prompt}"` after the configured delay.
- **Candidate** returns the same content by default.
- **`force_mismatch: true`** — candidate returns `"candidate-only: {prompt}"` to trigger a mismatch.
- **`simulate_candidate_failure: true`** — candidate throws `IllegalStateException`; primary is unaffected.

---

## Threading & Async Design

This is the core architectural constraint: **the primary path must never block on candidate work**.

### Mechanisms

| Concern | Implementation |
|---------|----------------|
| Thread decoupling | `@Async("shadowTaskExecutor")` on `ShadowProcessor.processShadow()` |
| Executor | `VirtualThreadTaskExecutor("shadow-vt-")` in `AsyncConfig` |
| Fire-and-forget | `ProxyOrchestrator` calls `processShadow()` without awaiting a result |
| Servlet virtual threads | `spring.threads.virtual.enabled: true` |
| Client disconnect safety | Shadow work is scheduled on a virtual thread **before** the HTTP response is flushed; it continues even if the client closes the connection |
| Candidate failure isolation | All candidate errors are caught inside `ShadowProcessor`; only `candidate_failures` metric increments |

### Thread names you will see

| Phase | Typical thread |
|-------|----------------|
| Primary call + HTTP response | `tomcat-handler-*` or similar servlet thread |
| Shadow / candidate work | `shadow-vt-*` |

If primary and candidate logs share the same servlet thread name for candidate work, async is **not** working correctly.

---

## Output Normalization

`OutputNormalizer` reduces false mismatches caused by formatting differences. Both primary and candidate raw strings pass through the same pipeline before equality is checked.

### Pipeline (in order)

1. **Extract content** — If the input is a chat-completion JSON envelope, pull `choices[0].message.content`, `choices[0].text`, or top-level `content`.
2. **Strip markdown JSON blocks** — Remove ` ```json ... ``` ` wrappers.
3. **Canonicalize JSON** — Parse JSON and re-serialize with recursively sorted keys (deterministic ordering).
4. **Collapse whitespace** — Multiple spaces/newlines → single space.
5. **Normalize plain text** — Lowercase and strip trailing punctuation.

### Comparison

```java
normalize(primaryRaw).equals(normalize(candidateRaw))
```

Formatting-only differences (key order, case, trailing periods, extra whitespace) should **not** count as mismatches.

---

## Metrics & Observability

### `GET /metrics`

`MetricsTracker` maintains in-memory counters with `AtomicLong` (no synchronized blocks):

| Field | Meaning |
|-------|---------|
| `total_shadow_requests` | Shadow comparisons started (candidate invoked) |
| `matches` | Normalized outputs agreed |
| `mismatches` | Normalized outputs disagreed |
| `candidate_failures` | Candidate timeout or exception |
| `real_time_match_rate` | `matches / (matches + mismatches) * 100`, or `100.0` if none compared yet |

Metrics update **after** background shadow work completes, not when `/generate` returns.

### Logging

| Event | Level | Format |
|-------|-------|--------|
| Shadow mismatch | WARN | JSON `MismatchLog` with normalized and raw outputs |
| Candidate timeout/failure | WARN | `requestId` + message |
| Async flow tracing | DEBUG | `[shadow-debug]` lines (see [Verifying Async Behavior](#verifying-async-behavior)) |

### Health

`GET /actuator/health` — exposed via Spring Boot Actuator for load balancers and App Platform health checks.

---

## API Reference

### `POST /generate`

Generate a response from the primary model and schedule background shadow comparison.

**Request:**

```json
{
  "prompt": "Explain virtual threads",
  "force_mismatch": false,
  "simulate_candidate_failure": false
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `prompt` | Yes | User prompt (non-blank) |
| `force_mismatch` | No | Candidate returns different content (testing) |
| `simulate_candidate_failure` | No | Candidate throws an error (testing) |

**Response (200 OK):**

```json
{
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "model": "primary-mock",
  "content": "Answer: Explain virtual threads",
  "latency_ms": 102
}
```

**Headers:** `X-Request-Id` mirrors `request_id`.

**Errors:**

| Status | Cause |
|--------|-------|
| 400 | Blank or missing `prompt` |
| 504 | Primary LLM timeout |

### `GET /metrics`

Returns current shadow comparison statistics (`MetricsSnapshot`).

```json
{
  "total_shadow_requests": 10,
  "matches": 8,
  "mismatches": 2,
  "candidate_failures": 0,
  "real_time_match_rate": 80.0
}
```

### Internal (not public contract)

| Endpoint | Purpose |
|----------|---------|
| `POST /internal/mock/primary` | Simulated primary upstream |
| `POST /internal/mock/candidate` | Simulated candidate upstream |

---

## Configuration

All settings live in `src/main/resources/application.yml`:

```yaml
server:
  port: ${PORT:8080}

spring:
  threads:
    virtual:
      enabled: true

llm:
  mock:
    primary-delay-ms: 100      # Simulated primary LLM latency
    candidate-delay-ms: 500    # Simulated candidate LLM latency
  timeout:
    primary-ms: 500            # RestClient connect + read timeout (primary)
    candidate-ms: 2000         # RestClient connect + read timeout (candidate)
  shadow:
    enabled: true              # Set false to disable background shadow work

logging:
  level:
    com.digitalocean.llmproxy.service.ProxyOrchestrator: DEBUG
    com.digitalocean.llmproxy.service.ShadowProcessor: DEBUG
```

| Property | Default | Effect |
|----------|---------|--------|
| `llm.mock.primary-delay-ms` | 100 | Artificial sleep in primary mock |
| `llm.mock.candidate-delay-ms` | 500 | Artificial sleep in candidate mock |
| `llm.timeout.primary-ms` | 500 | Primary HTTP client budget |
| `llm.timeout.candidate-ms` | 2000 | Candidate HTTP client budget |
| `llm.shadow.enabled` | true | Skip candidate call and comparison when false |

---

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Primary timeout | `504 Gateway Timeout` to client |
| Candidate timeout | Logged WARN; `candidate_failures++`; client already got 200 |
| Candidate exception | Same as timeout (including `simulate_candidate_failure`) |
| Validation error | `400` with RFC 7807 `ProblemDetail` |
| Mismatch | WARN log with full `MismatchLog`; `mismatches++` |

The primary success path is never rolled back or altered by shadow failures.

---

## Testing Strategy

```bash
./gradlew test
```

| Test suite | What it verifies |
|------------|------------------|
| `ProxyIntegrationTest` | End-to-end: `/generate` < 150 ms while candidate takes 500 ms+; metrics update after background processing; failure/mismatch flags |
| `OutputNormalizerTest` | Markdown, JSON key order, whitespace, punctuation edge cases |
| `MetricsTrackerTest` | Lock-free match rate calculation |
| `ShadowProcessorTest` | Compare logic and metric recording |
| `ProxyControllerTest` | WebMvc slice for API contract |

Key integration assertion: response latency reflects **primary only** (~100 ms), not primary + candidate (~600 ms).

---

## Build, CI/CD & Deployment

### Local run

```bash
./gradlew bootRun
```

### Build

```bash
./gradlew build
```

Produces a runnable JAR via Spring Boot; Docker multi-stage build runs `./gradlew bootJar`.

### CI

GitHub Actions (`.github/workflows/ci.yml`) runs `./gradlew build` on Java 21 for every push/PR to `main` or `master`.

### Docker

- **Build stage:** Eclipse Temurin 21 JDK Alpine, Gradle `bootJar`
- **Runtime stage:** JRE Alpine, non-root `spring` user, port 8080

### Production deployment

See [DEPLOY.md](DEPLOY.md) for DigitalOcean App Platform, `doctl`, and Droplet options. For real LLM upstreams, point `RestClient` targets at external URLs and supply API keys as platform secrets.

---

## Verifying Async Behavior

### 1. Latency check

```bash
time curl -s -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"latency test"}'
```

Expect **< ~150 ms**. If you see ~600 ms, candidate work is blocking the primary path.

### 2. Metrics delay

```bash
curl -s -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"async test"}'

curl -s http://localhost:8080/metrics    # may show shadow started only

sleep 0.8
curl -s http://localhost:8080/metrics    # matches/mismatches updated
```

### 3. Candidate failure isolation

```bash
curl -s -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"fail","simulate_candidate_failure":true}'
```

Expect `200 OK` with primary content; after ~800 ms, `candidate_failures` increases in `/metrics`.

### 4. Debug logs (`[shadow-debug]`)

With DEBUG logging enabled for `ProxyOrchestrator` and `ShadowProcessor`, a successful request produces:

```
[shadow-debug] requestId=... phase=primary-complete thread=tomcat-handler-... primaryLatencyMs=100 ts=...
[shadow-debug] requestId=... phase=shadow-scheduled thread=tomcat-handler-... ts=...
[shadow-debug] requestId=... phase=shadow-started thread=shadow-vt-1 ts=...
[shadow-debug] requestId=... phase=candidate-call-start thread=shadow-vt-1 ts=...
[shadow-debug] requestId=... phase=candidate-call-complete thread=shadow-vt-1 candidateLatencyMs=500 model=candidate-mock ts=...
[shadow-debug] requestId=... phase=compare-complete thread=shadow-vt-1 matched=true ts=...
```

**What to confirm:**

- `primary-complete` and `shadow-scheduled` run on the **servlet** thread.
- All candidate and compare phases run on **`shadow-vt-*`**.
- `shadow-scheduled` timestamp is close to `primary-complete` (no 500 ms gap before response).

---

## Project Layout

```
src/main/java/com/digitalocean/llmproxy/
├── LlmProxyApplication.java       # @SpringBootApplication + @EnableAsync
├── config/
│   ├── AsyncConfig.java           # VirtualThreadTaskExecutor, timing properties
│   └── RestClientConfig.java      # Primary/candidate RestClient beans
├── controller/
│   ├── ProxyController.java       # POST /generate, GET /metrics
│   └── MockLlmInternalController.java  # Internal mock LLM endpoints
├── exception/
│   └── GlobalExceptionHandler.java
├── model/
│   ├── PromptRequest.java
│   ├── LLMResponse.java
│   ├── MetricsSnapshot.java
│   └── MismatchLog.java
├── service/
│   ├── ProxyOrchestrator.java     # Sync primary + schedule shadow
│   ├── LLMMockService.java        # RestClient calls to mock endpoints
│   ├── ShadowProcessor.java       # Async compare pipeline
│   ├── MetricsTracker.java        # AtomicLong metrics
│   └── ServerPortResolver.java    # Loopback port for mock URLs
└── util/
    └── OutputNormalizer.java      # Normalization pipeline

src/test/java/...                    # Unit + integration tests
src/main/resources/application.yml   # Runtime configuration
Dockerfile                           # Multi-stage Java 21 container
.github/workflows/ci.yml             # CI pipeline
```

---

## Design Principles Summary

| Principle | How it is enforced |
|-----------|-------------------|
| Low latency for users | Primary path only; no `await` on shadow |
| Safe shadow evaluation | Candidate on isolated virtual thread with separate timeout |
| Resilience | Candidate failures never propagate to client |
| Fair comparison | Shared normalization pipeline before equality |
| Observable | Real-time metrics, mismatch JSON logs, optional debug tracing |
| Testable | Integration test proves < 150 ms response under 500 ms candidate delay |

---

## Related Documents

- [README.md](README.md) — Quick start and API examples
- [DEPLOY.md](DEPLOY.md) — DigitalOcean deployment guide
- [AgentInstructions.md](AgentInstructions.md) — Original system requirements specification
