#!/usr/bin/env bash
# =============================================================================
# DigitalOcean Deploy Agent
# Automatically deploys shadow-llm-proxy to DigitalOcean App Platform.
#
# Usage:
#   cp deploy/.env.example deploy/.env   # fill in credentials
#   ./deploy/agent.sh                    # deploy
#   ./deploy/agent.sh --status           # check deployment status
#   ./deploy/agent.sh --destroy          # delete the app (careful!)
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
GENERATED_SPEC="$SCRIPT_DIR/.app.generated.yaml"
STATE_FILE="$SCRIPT_DIR/.deploy-state"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()  { echo -e "${BLUE}[agent]${NC} $*"; }
ok()   { echo -e "${GREEN}[agent]${NC} $*"; }
warn() { echo -e "${YELLOW}[agent]${NC} $*"; }
fail() { echo -e "${RED}[agent]${NC} $*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# Load configuration
# ---------------------------------------------------------------------------
load_config() {
  if [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    set -a; source "$ENV_FILE"; set +a
    log "Loaded config from deploy/.env"
  else
    warn "No deploy/.env found — using environment variables only"
  fi

  DEPLOY_STRATEGY="${DEPLOY_STRATEGY:-github}"
  DO_APP_NAME="${DO_APP_NAME:-shadow-llm-proxy}"
  DO_REGION="${DO_REGION:-nyc}"
  GITHUB_BRANCH="${GITHUB_BRANCH:-main}"
  DO_REGISTRY_NAME="${DO_REGISTRY_NAME:-shadow-llm-proxy-registry}"
  LLM_SHADOW_ENABLED="${LLM_SHADOW_ENABLED:-true}"
}

validate_config() {
  [[ -n "${DIGITALOCEAN_ACCESS_TOKEN:-}" ]] || fail "DIGITALOCEAN_ACCESS_TOKEN is required. Copy deploy/.env.example to deploy/.env and fill it in."

  export DIGITALOCEAN_ACCESS_TOKEN
  export DOCTL_ACCESS_TOKEN="$DIGITALOCEAN_ACCESS_TOKEN"

  if [[ "$DEPLOY_STRATEGY" == "github" ]]; then
    [[ -n "${GITHUB_REPO:-}" ]] || fail "GITHUB_REPO is required for github strategy (e.g. myuser/shadow-llm-proxy)"
  elif [[ "$DEPLOY_STRATEGY" == "registry" ]]; then
    command -v docker >/dev/null 2>&1 || fail "Docker is required for registry strategy but not installed"
  else
    fail "DEPLOY_STRATEGY must be 'github' or 'registry', got: $DEPLOY_STRATEGY"
  fi

  command -v doctl >/dev/null 2>&1 || fail "doctl is not installed. Install: https://docs.digitalocean.com/reference/doctl/how-to/install/"
}

# ---------------------------------------------------------------------------
# Prerequisites
# ---------------------------------------------------------------------------
check_do_auth() {
  log "Verifying DigitalOcean credentials..."
  if ! doctl account get >/dev/null 2>&1; then
    fail "DigitalOcean auth failed. Check DIGITALOCEAN_ACCESS_TOKEN in deploy/.env"
  fi
  local email
  email=$(doctl account get --format Email --no-header)
  ok "Authenticated as: $email"
}

ensure_git_repo() {
  cd "$PROJECT_ROOT"
  if [[ ! -d .git ]]; then
    log "Initializing git repository..."
    git init
    git branch -M main 2>/dev/null || true
  fi
  if ! git rev-parse HEAD >/dev/null 2>&1; then
    log "Creating initial commit..."
    git add -A
    git commit -m "Initial commit: shadow LLM proxy"
  fi
}

guard_example_env_file() {
  local example_file="$SCRIPT_DIR/.env.example"
  if [[ -f "$example_file" ]] && grep -qE '(dop_v1_[a-f0-9]{20,}|ghp_[A-Za-z0-9]{20,})' "$example_file"; then
    fail "deploy/.env.example contains API tokens. Keep secrets in deploy/.env only (gitignored), then retry."
  fi
}

github_repo_exists() {
  local remote_url="$1"
  if [[ -n "${GITHUB_TOKEN:-}" ]] && command -v gh >/dev/null 2>&1; then
    GH_TOKEN="$GITHUB_TOKEN" gh repo view "$GITHUB_REPO" >/dev/null 2>&1
    return $?
  fi
  git ls-remote "$remote_url" HEAD >/dev/null 2>&1
}

push_to_github() {
  cd "$PROJECT_ROOT"
  ensure_git_repo
  guard_example_env_file

  local remote_url="https://github.com/${GITHUB_REPO}.git"

  if [[ -n "${GITHUB_TOKEN:-}" ]]; then
    remote_url="https://${GITHUB_TOKEN}@github.com/${GITHUB_REPO}.git"
  fi

  if git remote get-url origin >/dev/null 2>&1; then
    git remote set-url origin "$remote_url"
  else
    git remote add origin "$remote_url"
  fi

  log "Pushing to GitHub: $GITHUB_REPO ($GITHUB_BRANCH)..."
  git add -A
  if ! git diff --cached --quiet 2>/dev/null; then
    git commit -m "Deploy agent: $(date -u +%Y-%m-%dT%H:%M:%SZ)" || true
  fi

  local push_err=""
  if push_err=$(git push -u origin "$GITHUB_BRANCH" 2>&1); then
    ok "Code pushed to GitHub"
    return 0
  fi

  if github_repo_exists "$remote_url"; then
    warn "GitHub repo already exists — will not create a new repo."
    echo "$push_err" >&2
    fail "Git push failed. Fix the error above (often secret scanning or auth), then rerun ./deploy/agent.sh"
  fi

  if [[ -n "${GITHUB_TOKEN:-}" ]] && command -v gh >/dev/null 2>&1; then
    warn "Repo not found — creating via gh..."
    if GH_TOKEN="$GITHUB_TOKEN" gh repo create "$GITHUB_REPO" --public --source=. --remote origin --push; then
      ok "Code pushed to GitHub"
      return 0
    fi
  fi

  echo "$push_err" >&2
  fail "Git push failed. Ensure GITHUB_REPO exists and GITHUB_TOKEN has repo scope, or create the repo manually."
}

# ---------------------------------------------------------------------------
# Spec generation
# ---------------------------------------------------------------------------
generate_github_spec() {
  cat > "$GENERATED_SPEC" <<EOF
name: ${DO_APP_NAME}
region: ${DO_REGION}

databases:
  - name: metrics-redis
    engine: REDIS
    version: "7"
    production: false

services:
  - name: api
    dockerfile_path: Dockerfile
    source_dir: /
    github:
      repo: ${GITHUB_REPO}
      branch: ${GITHUB_BRANCH}
      deploy_on_push: true

    http_port: 8080
    instance_count: 1
    instance_size_slug: basic-xxs

    health_check:
      http_path: /actuator/health
      initial_delay_seconds: 45
      period_seconds: 10
      timeout_seconds: 5
      success_threshold: 1
      failure_threshold: 5

    envs:
      - key: PORT
        value: "8080"
        scope: RUN_TIME
      - key: LLM_SHADOW_ENABLED
        value: "${LLM_SHADOW_ENABLED:-true}"
        scope: RUN_TIME
      - key: METRICS_STORE
        value: "redis"
        scope: RUN_TIME
      - key: REDIS_HOST
        value: \${metrics-redis.HOSTNAME}
        scope: RUN_TIME
      - key: REDIS_PORT
        value: \${metrics-redis.PORT}
        scope: RUN_TIME
      - key: REDIS_PASSWORD
        value: \${metrics-redis.PASSWORD}
        scope: RUN_TIME
        type: SECRET

    routes:
      - path: /
EOF
  log "Generated App Platform spec: $GENERATED_SPEC"
}

generate_registry_spec() {
  local image="registry.digitalocean.com/${DO_REGISTRY_NAME}/${DO_APP_NAME}:latest"
  cat > "$GENERATED_SPEC" <<EOF
name: ${DO_APP_NAME}
region: ${DO_REGION}

databases:
  - name: metrics-redis
    engine: REDIS
    version: "7"
    production: false

services:
  - name: api
    image:
      registry_type: DOCR
      registry: ${DO_REGISTRY_NAME}
      repository: ${DO_APP_NAME}
      tag: latest

    http_port: 8080
    instance_count: 1
    instance_size_slug: basic-xxs

    health_check:
      http_path: /actuator/health
      initial_delay_seconds: 45
      period_seconds: 10
      timeout_seconds: 5
      success_threshold: 1
      failure_threshold: 5

    envs:
      - key: PORT
        value: "8080"
        scope: RUN_TIME
      - key: LLM_SHADOW_ENABLED
        value: "${LLM_SHADOW_ENABLED:-true}"
        scope: RUN_TIME
      - key: METRICS_STORE
        value: "redis"
        scope: RUN_TIME
      - key: REDIS_HOST
        value: \${metrics-redis.HOSTNAME}
        scope: RUN_TIME
      - key: REDIS_PORT
        value: \${metrics-redis.PORT}
        scope: RUN_TIME
      - key: REDIS_PASSWORD
        value: \${metrics-redis.PASSWORD}
        scope: RUN_TIME
        type: SECRET

    routes:
      - path: /
EOF
  log "Generated registry spec (image: $image)"
}

# ---------------------------------------------------------------------------
# Registry path
# ---------------------------------------------------------------------------
ensure_registry() {
  if doctl registry get "$DO_REGISTRY_NAME" >/dev/null 2>&1; then
    log "Container registry exists: $DO_REGISTRY_NAME"
  else
    log "Creating container registry: $DO_REGISTRY_NAME..."
    doctl registry create "$DO_REGISTRY_NAME" --region "$DO_REGION"
    ok "Registry created"
  fi
}

build_and_push_image() {
  local image="registry.digitalocean.com/${DO_REGISTRY_NAME}/${DO_APP_NAME}:latest"
  cd "$PROJECT_ROOT"

  log "Logging into DigitalOcean Container Registry..."
  doctl registry login

  log "Building Docker image..."
  docker build -t "$image" .

  log "Pushing image to registry..."
  docker push "$image"
  ok "Image pushed: $image"
}

# ---------------------------------------------------------------------------
# App Platform deploy
# ---------------------------------------------------------------------------
find_existing_app_id() {
  if [[ -n "${DO_APP_ID:-}" ]]; then
    echo "$DO_APP_ID"
    return
  fi
  if [[ -f "$STATE_FILE" ]]; then
    cat "$STATE_FILE"
    return
  fi
  doctl apps list --format ID,Spec.Name --no-header 2>/dev/null \
    | awk -v name="$DO_APP_NAME" '$2 == name { print $1; exit }' || true
}

deploy_app() {
  local app_id
  app_id=$(find_existing_app_id)

  if [[ -n "$app_id" ]]; then
    log "Updating existing app: $app_id"
    if ! doctl apps update "$app_id" --spec "$GENERATED_SPEC"; then
      fail "App update failed. Inspect spec: $GENERATED_SPEC"
    fi
    ok "App update triggered"
  else
    log "Creating new App Platform app: $DO_APP_NAME"
    local output
    if ! output=$(doctl apps create --spec "$GENERATED_SPEC" --format ID --no-header); then
      fail "App create failed. Inspect spec: $GENERATED_SPEC"
    fi
    app_id=$(echo "$output" | head -1)
    ok "App created: $app_id"
  fi

  echo "$app_id" > "$STATE_FILE"
  echo "$app_id"
}

wait_for_deploy() {
  local app_id="$1"
  log "Waiting for deployment to become active (this may take 5-10 minutes)..."

  local max_attempts=60
  local attempt=0
  local phase=""
  local in_progress=""

  while [[ $attempt -lt $max_attempts ]]; do
    in_progress=$(doctl apps get "$app_id" --format InProgressDeployment.ID --no-header 2>/dev/null || true)
    if [[ -n "$in_progress" ]]; then
      phase=$(doctl apps get-deployment "$app_id" "$in_progress" --format Phase --no-header 2>/dev/null || echo "UNKNOWN")
    else
      phase=$(doctl apps get "$app_id" --format ActiveDeployment.Phase --no-header 2>/dev/null || echo "UNKNOWN")
    fi

    case "$phase" in
      ACTIVE)
        ok "Deployment is ACTIVE"
        return 0
        ;;
      ERROR|FAILED|CANCELED)
        fail "Deployment failed ($phase). Run: doctl apps logs $app_id --type build"
        ;;
      UNKNOWN)
        if [[ -z "$in_progress" ]]; then
          warn "No deployment in progress. If you expected one, check: doctl apps list-deployments $app_id"
        fi
        log "  Phase: $phase (${attempt}/${max_attempts})..."
        ;;
      *)
        log "  Phase: $phase (${attempt}/${max_attempts})..."
        ;;
    esac

    sleep 15
    attempt=$((attempt + 1))
  done

  warn "Deployment still in progress after timeout. Check: doctl apps list-deployments $app_id"
}

print_result() {
  local app_id="$1"
  local url
  url=$(doctl apps get "$app_id" --format DefaultIngress --no-header 2>/dev/null || echo "")

  echo ""
  echo "============================================"
  ok "DEPLOYMENT COMPLETE"
  echo "============================================"
  echo "  App ID:  $app_id"
  echo "  URL:     https://${url}"
  echo ""
  echo "  Verify:"
  echo "    curl -s https://${url}/actuator/health"
  echo "    curl -s -X POST https://${url}/generate \\"
  echo "      -H 'Content-Type: application/json' \\"
  echo "      -d '{\"prompt\":\"Hello\"}'"
  echo "    curl -s https://${url}/metrics"
  echo ""
  echo "  Logs:    doctl apps logs $app_id --type run --follow"
  echo "  Status:  ./deploy/agent.sh --status"
  echo "============================================"
}

# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------
cmd_deploy() {
  local skip_push=false
  if [[ "${1:-}" == "--skip-push" ]]; then
    skip_push=true
  fi

  load_config
  validate_config
  check_do_auth

  if [[ "$DEPLOY_STRATEGY" == "github" ]]; then
    log "Strategy: GitHub → App Platform"
    warn "Ensure GitHub is connected to DigitalOcean: https://cloud.digitalocean.com/account/applications/github"
    if [[ "$skip_push" == "true" ]]; then
      warn "Skipping GitHub push (--skip-push). App Platform will use code already on GitHub."
    else
      push_to_github
    fi
    generate_github_spec
  else
    log "Strategy: Container Registry → App Platform"
    ensure_registry
    build_and_push_image
    generate_registry_spec
  fi

  local app_id
  app_id=$(deploy_app)
  wait_for_deploy "$app_id"
  print_result "$app_id"
}

cmd_status() {
  load_config
  validate_config
  check_do_auth

  local app_id
  app_id=$(find_existing_app_id)
  [[ -n "$app_id" ]] || fail "No deployed app found. Run ./deploy/agent.sh first."

  doctl apps get "$app_id"
  echo ""
  local url
  url=$(doctl apps get "$app_id" --format DefaultIngress --no-header)
  ok "Live URL: https://${url}"
}

cmd_destroy() {
  load_config
  validate_config
  check_do_auth

  local app_id
  app_id=$(find_existing_app_id)
  [[ -n "$app_id" ]] || fail "No deployed app found."

  warn "This will DELETE app $app_id ($DO_APP_NAME)"
  read -rp "Type the app name to confirm: " confirm
  [[ "$confirm" == "$DO_APP_NAME" ]] || fail "Aborted"

  doctl apps delete "$app_id" --force
  rm -f "$STATE_FILE"
  ok "App deleted"
}

cmd_help() {
  cat <<EOF
DigitalOcean Deploy Agent

Usage:
  ./deploy/agent.sh              Deploy (or update) the app
  ./deploy/agent.sh --skip-push  Update App Platform without pushing to GitHub
  ./deploy/agent.sh --status     Show deployment status and URL
  ./deploy/agent.sh --destroy    Delete the app from DigitalOcean
  ./deploy/agent.sh --help       Show this help

Setup:
  1. cp deploy/.env.example deploy/.env
  2. Fill in DIGITALOCEAN_ACCESS_TOKEN and GITHUB_REPO
  3. ./deploy/agent.sh

See deploy/REQUIREMENTS.md for full list of credentials needed.
EOF
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
  case "${1:-deploy}" in
    deploy|--deploy)     cmd_deploy ;;
    --skip-push)         cmd_deploy --skip-push ;;
    --status|status)     cmd_status ;;
    --destroy|destroy)   cmd_destroy ;;
    --help|-h|help)      cmd_help ;;
    *)                   fail "Unknown command: $1. Run --help for usage." ;;
  esac
}

main "$@"
