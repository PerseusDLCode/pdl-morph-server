# pdl-morph-server

A client-side morphological lookup tool: all querying, disambiguation
scoring, and TEI-dictionary rendering happens in the browser, against
`morph.db` (the SQLite database produced by the Clojure ingestion
pipeline in `clojure/`) queried in place over HTTP range requests. There
is no application server -- see `web/README.md` for the frontend
(Svelte + `sql.js-httpvfs`) and `deploy/nginx.conf` for how `morph.db` and
the built frontend are served in production.

## Building the database

See `clojure/README.md` for the `load`/`aggregate`/`ingest` pipeline that
produces `clojure/morph.db`. `Dockerfile` runs the full pipeline (stage 1)
and the frontend build (stage 2) to produce the deployed image (stage 3,
nginx).

## Running the frontend locally

```sh
cd web
pnpm install
mkdir -p public && ln -sf ../../clojure/morph.db public/morph.db
pnpm dev
```

See `web/README.md` for details and other scripts (tests, typecheck,
parity-checking against a reference server, browser-based verification).

## `scripts/`

Standalone one-off data-prep scripts (Beta Code -> Unicode conversion for
the morph/lexicon source XML consumed by the Clojure pipeline). Not part
of the runtime app; `uv sync` installs their two dependencies
(`beta-code`, `lxml`).
