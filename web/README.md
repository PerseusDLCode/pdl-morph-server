# pdl-morph-web

The morphological lookup app: the query/scoring logic (`src/morph/`), the TEI-XML-to-JSON parser (`src/tei/`), and the SvelteKit frontend (`src/routes/` + `src/components/`) all run in the browser, querying `morph.db` in place over HTTP range requests via `sql.js-httpvfs` (`src/morph/httpVfsDb.ts`) instead of through a server. There is no application server -- see `deploy/nginx.conf` for how `morph.db` and this package's build output are served in production.

## Dev setup

```sh
pnpm install
mkdir -p static && ln -sf ../../clojure/morph.db static/morph.db
pnpm dev
```

Vite's dev server serves `static/morph.db` with `Accept-Ranges`/`206` support natively, so no separate static-file server is needed for local development (see `deploy/nginx.conf` for the production equivalent).

## Routes

The app is a SvelteKit single-page app (`@sveltejs/adapter-static` with an `index.html` fallback; nginx's `try_files` routes every path to it). As in the old Python app, a lookup is addressable by URL:

```
/morph?word=λαμβάνω&language=grc[&document_id=...&prior_word=...]
```

`/` redirects to `/morph`, keeping any query string.

## Scripts

- `pnpm test` — unit tests (Vitest).
- `pnpm typecheck` — `tsc` + `svelte-check`.
- `pnpm build` — production build (`dist/`), consumed by `Dockerfile`'s runtime stage.
- `pnpm build:harness` / `pnpm verify:httpvfs` — builds and range-request-verifies a throwaway static page against a real browser (Playwright), independent of the full app.
- `pnpm e2e-check` — drives the real app in a real browser against a running `pnpm dev` server.
