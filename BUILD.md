# Build & Run Commands

Quick reference for building, testing, and running **shadow-llm-proxy**.

## Prerequisites

- **Java 21** (project uses Gradle toolchain)
- **Gradle wrapper** (included — use `./gradlew`, no global Gradle install required)

Check Java:

```bash
java -version
```

Make the wrapper executable (if needed):

```bash
chmod +x gradlew
```

---

## Build

From the project root:

```bash
cd /workspaces/shadow-llm-proxy
```

### Standard build (compile + test + JAR)

```bash
./gradlew build
```

### Clean rebuild (recommended after big changes)

```bash
./gradlew clean build
```

### Build without running tests

```bash
./gradlew clean build -x test
```

### Build only the runnable JAR

```bash
./gradlew bootJar
```

Output JAR:

```text
build/libs/shadow-llm-proxy-1.0.0.jar
```

### Refresh dependencies (if resolution issues)

```bash
./gradlew clean build --refresh-dependencies
```

### Show Gradle daemon status

```bash
./gradlew --status
```

### Stop Gradle daemons

```bash
./gradlew --stop
```

---

## Test

### Run all tests

```bash
./gradlew test
```

### Run a single test class

```bash
./gradlew test --tests "com.digitalocean.llmproxy.integration.ProxyIntegrationTest"
```

### Run tests with more output

```bash
./gradlew test --info
```

---

## Run Locally

### Start the app (dev — compiles automatically)

```bash
./gradlew bootRun
```

App listens on **http://localhost:8080** (override with `PORT` env var).

### Run the built JAR directly

```bash
java -jar build/libs/shadow-llm-proxy-1.0.0.jar
```

### Run on a different port

```bash
PORT=9090 ./gradlew bootRun
```

---

## Docker

### Build image

```bash
docker build -t shadow-llm-proxy .
```

### Run container

```bash
docker run -p 8080:8080 shadow-llm-proxy
```

### Run container in background

```bash
docker run -d -p 8080:8080 --name shadow-llm-proxy shadow-llm-proxy
```

### Stop and remove container

```bash
docker stop shadow-llm-proxy
docker rm shadow-llm-proxy
```

---

## Verify After Build / Run

### Health check

```bash
curl -s http://localhost:8080/actuator/health
```

### Generate (primary path)

```bash
curl -s -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Hello, world!"}'
```

### Metrics

```bash
curl -s http://localhost:8080/metrics | jq .
```

Example response (local, single instance):

```json
{
  "total_shadow_requests": 3,
  "matches": 3,
  "mismatches": 0,
  "candidate_failures": 0,
  "shadow_dropped": 0,
  "shadow_skipped": 0,
  "real_time_match_rate": 100.0,
  "instance_id": "localhost",
  "scope": "instance"
}
```

Counters update **after** background shadow work (~500 ms), not immediately after `/generate`:

```bash
curl -s -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"metrics timing test"}'

curl -s http://localhost:8080/metrics    # may not show match yet
sleep 0.8
curl -s http://localhost:8080/metrics    # matches updated
```

### Production-like local run

```bash
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

Uses 10% shadow sampling and enables API-key security. For full shadow coverage locally:

```bash
SPRING_PROFILES_ACTIVE=prod LLM_SHADOW_SAMPLE_RATE=1.0 ./gradlew bootRun
```

### Test flags

```bash
# Force candidate mismatch
curl -s -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"test","force_mismatch":true}'

# Simulate candidate failure (primary should still succeed)
curl -s -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"test","simulate_candidate_failure":true}'
```

---

## CI-equivalent command

Same command used in GitHub Actions:

```bash
./gradlew build --no-daemon
```

---

## Troubleshooting

| Issue | Command / fix |
|-------|----------------|
| Stale build artifacts | `./gradlew clean build` |
| Port 8080 already in use | Stop the other process or use `PORT=9090 ./gradlew bootRun` |
| `Permission denied` on gradlew | `chmod +x gradlew` |
| Slow or stuck Gradle daemon | `./gradlew --stop` then rebuild |
| Docker daemon not running | Start Docker Desktop / daemon, then retry `docker build` |
| Metrics fluctuate on App Platform | Multiple instances with `metrics.store=memory`; use `./deploy/agent.sh` (Redis) or scale to 1 instance |
| Metrics lower than `/generate` count | `prod` profile samples 10% of traffic; set `LLM_SHADOW_SAMPLE_RATE=1.0` for demos |
| Need full architecture details | See [ARCHITECTURE.md](ARCHITECTURE.md) |
| Need deployment steps | See [DEPLOY.md](DEPLOY.md) |

---

## Common Workflows

**Day-to-day development:**

```bash
./gradlew bootRun
```

**Before opening a PR:**

```bash
./gradlew clean build
```

**Production-like local run:**

```bash
./gradlew clean bootJar
java -jar build/libs/shadow-llm-proxy-1.0.0.jar
```

**Container deploy:**

```bash
./gradlew clean build
docker build -t shadow-llm-proxy .
docker run -p 8080:8080 shadow-llm-proxy
```

---

## Publish to Git

Full step-by-step guide: **[GITHUB_PUBLISH.md](GITHUB_PUBLISH.md)**

Quick publish after one-time `gh auth login` or `GH_TOKEN` setup:

```bash
chmod +x scripts/publish-to-github.sh .cursor/hooks/publish-to-github.sh
./scripts/publish-to-github.sh
```

Creates `https://github.com/<you>/shadow-llm-proxy` if missing, or pushes updates to `origin`. Auto-publish also runs via Cursor hook on agent `stop` (log: `/tmp/shadow-llm-proxy-publish.log`).

