#!/usr/bin/env bash
# Builds web/test-harness into a static docroot for Phase 2 verification
# (see web/scripts/verify-httpvfs-browser.mjs): the harness page, its
# esbuild bundle, sql.js-httpvfs's worker/wasm assets, and a copy of
# clojure/morph.db, all in one directory nginx can serve directly.
#
# Usage:
#   web/scripts/build-harness.sh [output-dir]
#   docker run -d --name morph-nginx-test -p 8091:8080 \
#     -v "$(pwd)/<output-dir>:/usr/share/nginx/html:ro" \
#     -v "$(pwd)/../deploy/nginx.conf:/etc/nginx/nginx.conf:ro" nginx:alpine
#   node web/scripts/verify-httpvfs-browser.mjs
set -euo pipefail

WEB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$WEB_DIR/.." && pwd)"
OUT_DIR="${1:-/tmp/morph-harness-dist}"

mkdir -p "$OUT_DIR"
"$WEB_DIR/node_modules/.bin/esbuild" "$WEB_DIR/test-harness/main.ts" \
  --bundle --format=esm --outfile="$OUT_DIR/main.js"
cp "$WEB_DIR/test-harness/index.html" "$OUT_DIR/"
cp "$WEB_DIR/node_modules/sql.js-httpvfs/dist/sqlite.worker.js" "$OUT_DIR/"
cp "$WEB_DIR/node_modules/sql.js-httpvfs/dist/sql-wasm.wasm" "$OUT_DIR/"
cp "$REPO_ROOT/clojure/morph.db" "$OUT_DIR/"

echo "built harness docroot at $OUT_DIR"
