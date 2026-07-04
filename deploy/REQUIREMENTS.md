# Information Required From You

The deploy agent (`./deploy/agent.sh`) needs the credentials below. **Nothing is optional for a first deploy except where marked.**

## Quick setup

```bash
cp deploy/.env.example deploy/.env
# Edit deploy/.env with your values
chmod +x deploy/agent.sh
./deploy/agent.sh
```

---

## Required credentials

### 1. DigitalOcean API token (always required)

| Field | Where to get it |
|-------|-----------------|
| `DIGITALOCEAN_ACCESS_TOKEN` | [cloud.digitalocean.com/account/api/tokens](https://cloud.digitalocean.com/account/api/tokens) → **Generate New Token** → **Read/Write** scope |

The agent uses this via `doctl` to create/update App Platform apps, container registries, and read deployment status.

---

## Choose a deploy strategy

Set `DEPLOY_STRATEGY` in `deploy/.env`:

### Strategy A: `github` (recommended)

App Platform builds from your GitHub repo on every deploy.

| Field | Required? | Description |
|-------|-----------|-------------|
| `GITHUB_REPO` | **Yes** | `username/shadow-llm-proxy` |
| `GITHUB_TOKEN` | **Yes** (first deploy) | [github.com/settings/tokens](https://github.com/settings/tokens) → **repo** scope |
| `GITHUB_BRANCH` | No | Default: `main` |

**One-time manual step (cannot be automated):**

Connect your GitHub account to DigitalOcean:

1. Open [cloud.digitalocean.com/account/applications/github](https://cloud.digitalocean.com/account/applications/github)
2. Click **Connect GitHub Account** and authorize

Without this, App Platform cannot pull your repo.

### Strategy B: `registry`

Builds Docker locally, pushes to DigitalOcean Container Registry, deploys the image.

| Field | Required? | Description |
|-------|-----------|-------------|
| `DO_REGISTRY_NAME` | No | Default: `shadow-llm-proxy-registry` (auto-created) |
| Docker | **Yes** | Must be installed and running on your machine |

No GitHub connection needed for this path.

---

## Optional settings

| Field | Default | Description |
|-------|---------|-------------|
| `DO_APP_NAME` | `shadow-llm-proxy` | App name in DigitalOcean |
| `DO_REGION` | `nyc` | Region: `nyc`, `sfo`, `lon`, `blr`, etc. |
| `DO_APP_ID` | auto-detected | Set after first deploy to force updates to a specific app |
| `LLM_PRIMARY_URL` | mock endpoint | Production primary LLM URL |
| `LLM_CANDIDATE_URL` | mock endpoint | Production candidate LLM URL |
| `LLM_SHADOW_ENABLED` | `true` | Enable/disable shadow traffic |
| `LLM_SHADOW_SAMPLE_RATE` | `0.1` (prod) | Fraction of requests shadowed; `1.0` for demos |
| `SPRING_PROFILES_ACTIVE` | `prod` (agent) | Spring profile; controls sampling and security |
| `METRICS_STORE` | `redis` (agent) | `memory` (per instance) or `redis` (cluster totals) |
| `PROXY_API_KEY` | — | API key for `/generate` and `/metrics` in prod |

---

## What the agent does automatically

```
┌─────────────────────────────────────────────────────────────┐
│                    ./deploy/agent.sh                         │
└──────────────────────────┬──────────────────────────────────┘
                           │
         ┌─────────────────┴─────────────────┐
         │                                   │
    [github strategy]                  [registry strategy]
         │                                   │
    git init/commit                    docker build
    push to GitHub                     doctl registry login
         │                             docker push to DOCR
         └─────────────────┬─────────────────┘
                           │
              generate App Platform spec
                           │
              doctl apps create / update
                           │
              poll until deployment ACTIVE
                           │
              print live HTTPS URL + curl commands
```

---

## After first deploy

The agent saves your app ID to `deploy/.deploy-state`. Future runs of `./deploy/agent.sh` will **update** the existing app instead of creating a new one.

```bash
./deploy/agent.sh --status    # check health and URL
./deploy/agent.sh             # redeploy after code changes
./deploy/agent.sh --destroy   # delete the app
```

---

## Security checklist

- **Never commit `deploy/.env`** — it is gitignored
- Store tokens as secrets in CI, not in code
- Rotate tokens if exposed
- Use encrypted App Platform secrets for production LLM API keys

---

## Troubleshooting

| Error | Fix |
|-------|-----|
| `DIGITALOCEAN_ACCESS_TOKEN is required` | Copy `.env.example` → `.env` and fill in token |
| `DigitalOcean auth failed` | Regenerate token with Read/Write scope |
| `Git push failed` | Check `GITHUB_TOKEN` has repo scope; create repo on GitHub first |
| App stuck in BUILDING | `doctl apps logs <APP_ID> --type build` |
| Health check failing | Wait 2–3 min; check `/actuator/health` locally first |
| `/metrics` values fluctuate | Multiple instances with in-memory metrics; agent deploys Redis automatically |
| Metrics lower than request count | Expected with 10% prod sampling; set `LLM_SHADOW_SAMPLE_RATE=1.0` |
| `GitHub not connected` | Complete one-time OAuth at DO GitHub settings link above |

---

## Minimum info to send the agent (copy/paste template)

Fill this in and share with whoever runs the deploy:

```
DIGITALOCEAN_ACCESS_TOKEN=<your DO token>
DEPLOY_STRATEGY=github
GITHUB_REPO=<your-username>/shadow-llm-proxy
GITHUB_TOKEN=<your GitHub PAT with repo scope>
DO_REGION=nyc
```

Plus confirm you have connected GitHub to DigitalOcean (one-time).
