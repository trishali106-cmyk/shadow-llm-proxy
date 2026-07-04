# GitHub Auto-Publish Guide

This project can automatically publish to GitHub: **create a new repository if one does not exist**, or **push updates** to an existing remote.

Use this guide for one-time setup and day-to-day publishing.

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [One-Time Setup](#one-time-setup)
4. [Publish Manually](#publish-manually)
5. [Automatic Agent Publish](#automatic-agent-publish)
6. [Configuration](#configuration)
7. [Verify Publish](#verify-publish)
8. [Troubleshooting](#troubleshooting)
9. [Legacy Manual Options](#legacy-manual-options)

---

## Overview

| Component | Location | Purpose |
|-----------|----------|---------|
| Publish script | `scripts/publish-to-github.sh` | Create repo or push updates |
| Cursor hook | `.cursor/hooks.json` | Auto-publish when agent session ends |
| Hook wrapper | `.cursor/hooks/publish-to-github.sh` | Runs script from hook safely |
| Agent rule | `.cursor/rules/github-auto-publish.mdc` | Tells agent to publish after work |
| Token template | `scripts/github.env.example` | Optional non-interactive auth config |
| Hook log | `/tmp/shadow-llm-proxy-publish.log` | Debug output when hook publish fails |

### What the publish script does

1. Checks GitHub authentication (`gh auth` or `GH_TOKEN`)
2. **Auto-commits** local changes (respects `.gitignore`) unless `AUTO_COMMIT=false`
3. If `origin` exists → **pushes** to GitHub
4. If repo exists on GitHub but no remote → **adds remote** and pushes
5. If repo does not exist → **creates** `shadow-llm-proxy` and pushes

Default target: `https://github.com/<your-username>/shadow-llm-proxy` on branch `main`.

---

## Prerequisites

- **git** — repository initialized locally (already done in this project)
- **GitHub CLI** (`gh`) — [install guide](https://cli.github.com/)
- **GitHub account** with permission to create repositories
- **Authentication** — `gh auth login` or `GH_TOKEN` (see below)

Check tools:

```bash
git --version
gh --version
```

---

## One-Time Setup

### Step 1 — Make scripts executable

```bash
cd /workspaces/shadow-llm-proxy
chmod +x scripts/publish-to-github.sh .cursor/hooks/publish-to-github.sh
```

### Step 2 — Authenticate with GitHub

Pick **one** method.

#### Option A: Interactive login (easiest for local dev)

```bash
gh auth login
```

Follow the prompts (GitHub.com → HTTPS → browser or token).

Verify:

```bash
gh auth status
gh api user -q .login
```

#### Option B: Token for agents / hooks / CI (non-interactive)

1. Create a [GitHub personal access token](https://github.com/settings/tokens) with **repo** scope.
2. Export it in your shell:

```bash
export GH_TOKEN=ghp_your_token_here
```

#### Option C: Persistent config file (gitignored)

```bash
cp scripts/github.env.example scripts/github.env
```

Edit `scripts/github.env`:

```bash
GH_TOKEN=ghp_your_token_here
# GITHUB_OWNER=your-github-username
# GITHUB_REPO_NAME=shadow-llm-proxy
# GITHUB_REPO_VISIBILITY=public
```

Load before publishing:

```bash
set -a && source scripts/github.env && set +a
```

> `scripts/github.env` is in `.gitignore` — never commit tokens.

### Step 3 — Confirm local git state

```bash
git status
git branch --show-current   # should be main
git log --oneline -3
```

### Step 4 — (Optional) Set a private repository

```bash
export GITHUB_REPO_VISIBILITY=private
```

---

## Publish Manually

### First publish (creates repo + pushes)

```bash
./scripts/publish-to-github.sh
```

Expected result:

- New repo at `https://github.com/<you>/shadow-llm-proxy`
- All local commits pushed to `main`
- `origin` remote configured

### Subsequent publishes (push updates only)

After making changes:

```bash
./scripts/publish-to-github.sh
```

The script will auto-commit any uncommitted changes, then push.

### Publish without auto-commit

```bash
AUTO_COMMIT=false ./scripts/publish-to-github.sh
```

### Publish with a custom commit message

```bash
COMMIT_MESSAGE="feat: add shadow debug logging" ./scripts/publish-to-github.sh
```

---

## Automatic Agent Publish

Two mechanisms publish without you running the script manually.

### 1. Cursor `stop` hook

File: `.cursor/hooks.json`

When a Cursor agent session ends, `.cursor/hooks/publish-to-github.sh` runs the publish script.

- Hook timeout: 120 seconds
- Failures are logged, not shown in chat
- Check log: `/tmp/shadow-llm-proxy-publish.log`

```bash
tail -f /tmp/shadow-llm-proxy-publish.log
```

> **Requires `GH_TOKEN` or prior `gh auth login`** in the environment Cursor uses. Interactive `gh auth login` in a terminal may not apply to hook subprocesses — use `GH_TOKEN` for reliable hook publish.

### 2. Cursor agent rule

File: `.cursor/rules/github-auto-publish.mdc`

Instructs the agent to run `./scripts/publish-to-github.sh` after implementation work when auto-publish is enabled.

---

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `GITHUB_REPO_NAME` | `shadow-llm-proxy` | Repository name on GitHub |
| `GITHUB_REPO_VISIBILITY` | `public` | `public` or `private` |
| `GITHUB_BRANCH` | `main` | Branch to push |
| `GITHUB_OWNER` | autodetected | GitHub username or org (from `gh api user`) |
| `GITHUB_REPO_DESCRIPTION` | project description | New repo description |
| `AUTO_COMMIT` | `true` | Commit local changes before push |
| `COMMIT_MESSAGE` | `chore: auto-publish <timestamp>` | Custom commit message |
| `GITHUB_PUBLISH_LOG` | `/tmp/shadow-llm-proxy-publish.log` | Hook log file path |
| `GH_TOKEN` | — | GitHub token for non-interactive auth |

### Examples

**Private repo under a specific owner:**

```bash
GITHUB_OWNER=my-org \
GITHUB_REPO_VISIBILITY=private \
./scripts/publish-to-github.sh
```

**Different repo name:**

```bash
GITHUB_REPO_NAME=my-shadow-proxy ./scripts/publish-to-github.sh
```

---

## Verify Publish

```bash
# Remote configured?
git remote -v

# Commits pushed?
git log --oneline -3
git status

# Repo exists on GitHub?
gh repo view

# Open in browser
gh repo view --web
```

Confirm on GitHub:

- All commits are visible on `main`
- Files match local project (source, docs, scripts, `.cursor/` config)
- No secrets committed (`deploy/.env`, `scripts/github.env` should NOT appear)

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `GitHub CLI is not authenticated` | Run `gh auth login` or `export GH_TOKEN=...` |
| `Could not resolve GitHub owner` | Set `GITHUB_OWNER=your-username` |
| `Permission denied` on script | `chmod +x scripts/publish-to-github.sh` |
| Hook runs but nothing pushed | Check `/tmp/shadow-llm-proxy-publish.log`; set `GH_TOKEN` for hooks |
| `repository already exists` but no remote | Script should auto-link; or run `git remote add origin https://github.com/USER/REPO.git` |
| Push rejected (non-fast-forward) | Pull/rebase first: `git pull --rebase origin main` |
| Wrong branch | `git checkout main` or set `GITHUB_BRANCH` |
| Accidentally committed secrets | Do not push; remove from history; rotate the token |

### Debug publish manually

```bash
AUTO_COMMIT=false ./scripts/publish-to-github.sh
```

### Re-authenticate gh

```bash
gh auth logout
gh auth login
```

---

## Legacy Manual Options

If you prefer not to use the script:

### Create new repo and push

```bash
gh auth login
gh repo create shadow-llm-proxy --public --source=. --remote=origin --push \
  --description "Java 21 Spring Boot LLM shadow proxy with async candidate comparison"
```

### Push to existing repo

```bash
git remote add origin https://github.com/YOUR_USER/shadow-llm-proxy.git
git push -u origin main
```

### First-time git init (fresh clone)

```bash
git init -b main
git add -A
git commit -m "Initial commit: shadow LLM proxy service."
git remote add origin https://github.com/YOUR_USER/shadow-llm-proxy.git
git push -u origin main
```

---

## Quick Reference

```bash
# Full first-time flow
chmod +x scripts/publish-to-github.sh .cursor/hooks/publish-to-github.sh
gh auth login
./scripts/publish-to-github.sh
gh repo view --web

# Or with token (for agents/hooks)
cp scripts/github.env.example scripts/github.env
# edit scripts/github.env
set -a && source scripts/github.env && set +a
./scripts/publish-to-github.sh
```

See also: [BUILD.md](BUILD.md) (all build/run commands), [README.md](README.md) (project overview).
