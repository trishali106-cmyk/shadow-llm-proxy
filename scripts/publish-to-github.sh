#!/usr/bin/env bash
# Create the GitHub repo if missing, or push updates to an existing remote.
# Requires: git, gh (GitHub CLI), and either `gh auth login` or GH_TOKEN.
set -euo pipefail

REPO_NAME="${GITHUB_REPO_NAME:-shadow-llm-proxy}"
VISIBILITY="${GITHUB_REPO_VISIBILITY:-public}"
BRANCH="${GITHUB_BRANCH:-main}"
DESCRIPTION="${GITHUB_REPO_DESCRIPTION:-Java 21 Spring Boot LLM shadow proxy with async candidate comparison}"
AUTO_COMMIT="${AUTO_COMMIT:-true}"
LOG_FILE="${GITHUB_PUBLISH_LOG:-/tmp/shadow-llm-proxy-publish.log}"

log() {
  echo "[publish-to-github] $*" | tee -a "$LOG_FILE"
}

die() {
  log "ERROR: $*"
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

require_command git
require_command gh

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
[[ -n "$ROOT" ]] || die "Not inside a git repository."
cd "$ROOT"

if [[ ! -d .git ]]; then
  die "No .git directory found in $ROOT"
fi

current_branch="$(git branch --show-current)"
if [[ "$current_branch" != "$BRANCH" ]]; then
  log "Current branch is '$current_branch' (expected '$BRANCH'). Continuing anyway."
fi

if ! gh auth status >/dev/null 2>&1; then
  if [[ -z "${GH_TOKEN:-}" ]]; then
    die "GitHub CLI is not authenticated. Run 'gh auth login' or set GH_TOKEN."
  fi
  export GH_TOKEN
fi

OWNER="${GITHUB_OWNER:-}"
if [[ -z "$OWNER" ]]; then
  OWNER="$(gh api user -q .login 2>/dev/null || true)"
fi
[[ -n "$OWNER" ]] || die "Could not resolve GitHub owner. Set GITHUB_OWNER or authenticate gh."

if [[ "$AUTO_COMMIT" == "true" ]]; then
  if ! git diff --quiet || ! git diff --cached --quiet || [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
    git add -A
    if git diff --cached --quiet; then
      log "No staged changes to commit after git add."
    else
      commit_message="${COMMIT_MESSAGE:-chore: auto-publish $(date -u +%Y-%m-%dT%H:%M:%SZ)}"
      git commit -m "$commit_message"
      log "Committed local changes: $commit_message"
    fi
  else
    log "Working tree clean; skipping auto-commit."
  fi
fi

REMOTE_URL="https://github.com/${OWNER}/${REPO_NAME}.git"
FULL_NAME="${OWNER}/${REPO_NAME}"

publish_existing_remote() {
  log "Pushing to existing origin ($FULL_NAME)..."
  git push -u origin "$BRANCH"
}

if git remote get-url origin >/dev/null 2>&1; then
  publish_existing_remote
elif gh repo view "$FULL_NAME" >/dev/null 2>&1; then
  log "Repository exists on GitHub; linking origin and pushing..."
  git remote add origin "$REMOTE_URL"
  publish_existing_remote
else
  log "Creating new GitHub repository '$FULL_NAME'..."
  if [[ "$VISIBILITY" == "private" ]]; then
    gh repo create "$REPO_NAME" \
      --private \
      --source=. \
      --remote=origin \
      --push \
      --description "$DESCRIPTION"
  else
    gh repo create "$REPO_NAME" \
      --public \
      --source=. \
      --remote=origin \
      --push \
      --description "$DESCRIPTION"
  fi
fi

log "Published successfully: https://github.com/${FULL_NAME}"
