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
curl -s http://localhost:8080/metrics
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
