#!/usr/bin/env bash
#
# cron-deploy.sh — poll GHCR for a new server image and redeploy.
#
# Model: the morphology DB is baked INTO the image, so a deploy is just
# "pull the new image and recreate the compose service" — no on-VM build,
# no data volume, no rollback snapshots.
#
# Config via environment (or ENV_FILE, sourced below):
#   IMAGE          image repo     (default: ghcr.io/perseusdlcode/pdl-morph-server)
#   IMAGE_TAG      image tag       (default: dev-latest)
#   SERVE_PORT     host port       (default: 8081)   — passed through to compose
#   CONTAINER_CMD  podman | docker (default: podman)
#   STATE_FILE     deployed-digest marker (default: deployed.digest)
#   ENV_FILE       optional file to source for the above (default: .env)
#   GHCR_USER / GHCR_TOKEN  optional; if set, logs in for private pulls
#
# Intended to run under flock every 10 minutes:
#   */10 * * * * /usr/bin/flock -n deploy.lock /path/to/cron-deploy.sh >> deploy.log 2>&1

set -euo pipefail

log() { echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*"; }

# ----- Config -------------------------------------------------------------
ENV_FILE="${ENV_FILE:-.env}"
# shellcheck disable=SC1090
[ -f "$ENV_FILE" ] && . "$ENV_FILE"

IMAGE="${IMAGE:-ghcr.io/perseusdlcode/pdl-morph-server}"
IMAGE_TAG="${IMAGE_TAG:-dev-latest}"
CONTAINER_CMD="${CONTAINER_CMD:-podman}"
SERVE_PORT="${SERVE_PORT:-8081}"
STATE_FILE="${STATE_FILE:-deployed.digest}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-perseus}"
COMPOSE_FILE="$(dirname "$0")/compose.yaml"
IMAGE_REF="${IMAGE}:${IMAGE_TAG}"

# compose.yaml reads these:
export IMAGE IMAGE_TAG SERVE_PORT

compose() { ${CONTAINER_CMD} compose -f "$COMPOSE_FILE" -p "$COMPOSE_PROJECT" "$@"; }

# ----- Optional registry login (needed only if the package is private) ----
if [ -n "${GHCR_TOKEN:-}" ] && [ -n "${GHCR_USER:-}" ]; then
  echo "$GHCR_TOKEN" | ${CONTAINER_CMD} login ghcr.io -u "$GHCR_USER" --password-stdin >/dev/null
fi

# ----- 1. Pull (idempotent; a near no-op when the digest is unchanged) -----
log "Pulling ${IMAGE_REF}..."
if ! ${CONTAINER_CMD} pull "$IMAGE_REF"; then
  log "WARN: pull failed; will retry next tick."
  exit 0
fi

# Resolve the pulled image's registry digest. Reading RepoDigests after a
# pull works for single- and multi-arch images alike, unlike parsing the
# JSON shape of 'manifest inspect'.
NEW_DIGEST=$(${CONTAINER_CMD} image inspect "$IMAGE_REF" \
  --format '{{index .RepoDigests 0}}' 2>/dev/null || echo "")
if [ -z "$NEW_DIGEST" ]; then
  log "WARN: could not resolve image digest; will retry next tick."
  exit 0
fi

# ----- 2. Skip if this digest is already deployed -------------------------
if [ -f "$STATE_FILE" ] && [ "$(cat "$STATE_FILE")" = "$NEW_DIGEST" ]; then
  log "No new image (already deployed ${NEW_DIGEST##*@})."
  exit 0
fi
log "New image detected: ${NEW_DIGEST##*@}"

# ----- 3. Recreate the service on the new image ---------------------------
log "Recreating '${COMPOSE_PROJECT}' serve..."
compose up -d --force-recreate serve

# ----- 4. Verify it comes up (healthy if a healthcheck is defined) --------
CID=$(compose ps -q serve || echo "")
if [ -n "$CID" ]; then
  for _ in $(seq 1 15); do
    STATUS=$(${CONTAINER_CMD} inspect \
      --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
      "$CID" 2>/dev/null || echo "")
    case "$STATUS" in
      healthy|running) break ;;
      unhealthy|exited|dead)
        log "ERROR: serve is '${STATUS}' after deploy. STATE_FILE NOT advanced;"
        log "       investigate, or redeploy a known-good tag (see notes below)."
        exit 1 ;;
    esac
    sleep 4
  done
fi

# ----- 5. Record the deployed digest --------------------------------------
mkdir -p "$(dirname "$STATE_FILE")"
echo "$NEW_DIGEST" > "$STATE_FILE"
log "Deploy complete. Digest recorded to ${STATE_FILE}."
