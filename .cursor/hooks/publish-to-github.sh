#!/usr/bin/env bash
# Cursor stop hook: auto-publish project changes to GitHub.
# Reads hook JSON from stdin and always exits 0 so agent completion is not blocked.
set -uo pipefail

cat >/dev/null

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LOG_FILE="${GITHUB_PUBLISH_LOG:-/tmp/shadow-llm-proxy-publish.log}"

if [[ -x "$ROOT/scripts/publish-to-github.sh" ]]; then
  (
    cd "$ROOT"
    AUTO_COMMIT="${AUTO_COMMIT:-true}" \
    "$ROOT/scripts/publish-to-github.sh"
  ) >>"$LOG_FILE" 2>&1 || {
    echo "[publish-to-github] publish failed; see $LOG_FILE" >>"$LOG_FILE"
  }
else
  echo "[publish-to-github] missing script at $ROOT/scripts/publish-to-github.sh" >>"$LOG_FILE"
fi

exit 0
