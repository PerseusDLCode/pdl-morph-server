# pdl-morph-web

The morphological lookup app: the query/scoring logic (`src/morph/`), the TEI-XML-to-JSON parser (`src/tei/`), and the Svelte frontend (`src/App.svelte` + `src/components/`) all run in the browser, querying `morph.db` in place over HTTP range requests via `sql.js-httpvfs` (`src/morph/httpVfsDb.ts`) instead of through a server. There is no application server -- see `deploy/nginx.conf` for how `morph.db` and this package's build output are served in production.

## Dev setup

```sh
pnpm install
mkdir -p public && ln -sf ../../clojure/morph.db public/morph.db
pnpm dev
```

Vite's dev server serves `public/morph.db` with `Accept-Ranges`/`206` support natively, so no separate static-file server is needed for local development (see `deploy/nginx.conf` for the production equivalent).

## Scripts

- `pnpm test` — unit tests (Vitest).
- `pnpm typecheck` — `tsc` + `svelte-check`.
- `pnpm build` — production build (`dist/`), consumed by `Dockerfile`'s runtime stage.
- `pnpm build:harness` / `pnpm verify:httpvfs` — builds and range-request-verifies a throwaway static page against a real browser (Playwright), independent of the full app.
- `pnpm e2e-check` — drives the real app in a real browser against a running `pnpm dev` server.
