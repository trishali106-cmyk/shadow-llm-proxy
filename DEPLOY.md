# Deploy to DigitalOcean App Platform

This guide walks through deploying **shadow-llm-proxy** to [DigitalOcean App Platform](https://docs.digitalocean.com/products/app-platform/). App Platform builds the Docker image, runs the container, and gives you a public HTTPS URL.

## Prerequisites

1. A [DigitalOcean account](https://cloud.digitalocean.com/registrations/new)
2. A [GitHub](https://github.com) account
3. A DigitalOcean [API token](https://cloud.digitalocean.com/account/api/tokens) (Read/Write scope)

## Option A — Deploy via DigitalOcean Control Panel (easiest)

### 1. Push code to GitHub

```bash
cd shadow-llm-proxy
git init
git add .
git commit -m "Initial commit: shadow LLM proxy"
gh repo create shadow-llm-proxy --public --source=. --push
```

Or create a repo manually on GitHub and push:

```bash
git remote add origin https://github.com/YOUR_USER/shadow-llm-proxy.git
git push -u origin main
```

### 2. Create the app in App Platform

1. Open [cloud.digitalocean.com/apps](https://cloud.digitalocean.com/apps)
2. Click **Create App**
3. Choose **GitHub** as the source and authorize DigitalOcean
4. Select your `shadow-llm-proxy` repository and `main` branch
5. App Platform detects the **Dockerfile** automatically
6. Confirm settings:
   - **Resource type:** Web Service
   - **HTTP port:** `8080`
   - **Instance size:** Basic (cheapest, ~$5/mo)
   - **Health check path:** `/actuator/health`

### 3. Set environment variables

In the app settings, add these **Run Time** env vars:

| Key | Value |
|-----|-------|
| `PORT` | `8080` |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `LLM_PRIMARY_URL` | `http://127.0.0.1:8080/internal/mock/primary` |
| `LLM_CANDIDATE_URL` | `http://127.0.0.1:8080/internal/mock/candidate` |
| `LLM_SHADOW_ENABLED` | `true` |
| `PROXY_API_KEY` | *(secret)* API key for `/generate` and `/metrics` |

The mock endpoints run inside the same container, so loopback (`127.0.0.1`) is correct.

**Recommended:** Use `./deploy/agent.sh` instead of manual setup — it provisions a managed **Redis** database and sets `METRICS_STORE=redis` so `/metrics` returns cluster-wide totals across instances.

Optional overrides:

| Key | Default (prod) | Purpose |
|-----|----------------|---------|
| `METRICS_STORE` | `memory` (manual) / `redis` (agent) | Cluster-wide metrics aggregation |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | — | Required when `METRICS_STORE=redis` |
| `LLM_SHADOW_SAMPLE_RATE` | `0.1` | Fraction of requests shadowed; set `1.0` for demos |

For **real LLM endpoints** in production, replace the URLs:

| Key | Example value |
|-----|---------------|
| `LLM_PRIMARY_URL` | `https://api.openai.com/v1/chat/completions` |
| `LLM_CANDIDATE_URL` | `https://your-candidate-model.example/v1/chat/completions` |

### 4. Deploy

Click **Create Resources**. App Platform will:

1. Clone your repo
2. Build the Docker image
3. Deploy the container
4. Assign a URL like `https://shadow-llm-proxy-xxxxx.ondigitalocean.app`

First deploy usually takes 5–10 minutes.

### 5. Verify

```bash
APP_URL="https://shadow-llm-proxy-xxxxx.ondigitalocean.app"

curl -s "$APP_URL/actuator/health"

curl -s -X POST "$APP_URL/generate" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $PROXY_API_KEY" \
  -d '{"prompt":"Hello from DO"}'

# Wait for async shadow work (~500 ms), then check metrics
sleep 1
curl -s "$APP_URL/metrics" -H "X-API-Key: $PROXY_API_KEY"
```

Expected metrics fields: `total_shadow_requests`, `matches`, `shadow_skipped`, `instance_id`, `scope`.

- **`scope: "cluster"`** — Redis-backed totals (via `./deploy/agent.sh`).
- **`scope: "instance"`** — Per-container counters; values may differ across repeated calls if multiple instances are running.

---

## Option B — Deploy with `doctl` CLI

### 1. Install and authenticate doctl

```bash
# macOS
brew install doctl

# Linux
snap install doctl

# Authenticate
doctl auth init
# Paste your DigitalOcean API token when prompted
```

### 2. Push to GitHub

Same as Option A, step 1.

### 3. Edit `app.yaml`

Replace the placeholder GitHub repo:

```yaml
github:
  repo: YOUR_GITHUB_USER/shadow-llm-proxy
  branch: main
```

Optionally change `region` (e.g. `sfo`, `lon`, `blr`).

### 4. Create the app

```bash
doctl apps create --spec app.yaml
```

Note the app ID from the output.

### 5. Monitor deployment

```bash
doctl apps list
doctl apps get <APP_ID>
doctl apps logs <APP_ID> --type build
doctl apps logs <APP_ID> --type run
```

### 6. Get the live URL

```bash
doctl apps get <APP_ID> --format DefaultIngress --no-header
```

---

## Option C — Deploy from local Docker (Droplet)

If you prefer a VM instead of App Platform:

```bash
# On a Droplet with Docker installed
git clone https://github.com/YOUR_USER/shadow-llm-proxy.git
cd shadow-llm-proxy
docker build -t shadow-llm-proxy .
docker run -d -p 8080:8080 \
  -e LLM_PRIMARY_URL=http://127.0.0.1:8080/mock/primary/v1/chat/completions \
  -e LLM_CANDIDATE_URL=http://127.0.0.1:8080/mock/candidate/v1/chat/completions \
  --name shadow-llm-proxy shadow-llm-proxy
```

Use a firewall rule to allow port 8080, or put nginx/Caddy in front for HTTPS.

---

## CI/CD — auto-deploy on push

App Platform auto-deploys when `deploy_on_push: true` is set in `app.yaml` (already configured). Every push to `main` triggers a new build.

Your existing GitHub Actions CI (`.github/workflows/ci.yml`) runs tests on PRs; App Platform handles deployment separately.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Build fails on Gradle | Check build logs: `doctl apps logs <APP_ID> --type build` |
| Health check failing | Ensure `/actuator/health` returns 200; increase `initial_delay_seconds` in `app.yaml` |
| 502 on chat endpoint | Verify `LLM_PRIMARY_URL` is reachable from inside the container |
| Shadow not comparing | Check run logs for `shadow-` thread errors; confirm `LLM_CANDIDATE_URL` |
| Out of memory | Upgrade instance size from `basic-xxs` to `basic-xs` in `app.yaml` |

---

## Cost

- **basic-xxs** (~512 MB RAM): ~$5/month
- Free tier: DigitalOcean sometimes offers $200 credit for new accounts

---

## Security notes for production

- Replace mock endpoints with real LLM URLs
- Store API keys as **App Platform secrets** (encrypted env vars), not in `app.yaml`
- Restrict `/metrics` behind auth or remove it from public routes if sensitive
