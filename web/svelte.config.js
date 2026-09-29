import adapter from "@sveltejs/adapter-static";
import { vitePreprocess } from "@sveltejs/vite-plugin-svelte";

/** @type {import('@sveltejs/kit').Config} */
export default {
  preprocess: vitePreprocess(),
  kit: {
    // Built as a single-page app: every route renders client-side (see
    // src/routes/+layout.ts), and deploy/nginx.conf's `try_files ...
    // /morph/index.html` hands every path to this fallback page, so deep
    // links like /morph?word=... load straight into the router. Output
    // stays in dist/, where the Dockerfile's runtime stage expects it.
    adapter: adapter({
      pages: "dist",
      assets: "dist",
      fallback: "index.html",
    }),
    // Everything this app serves -- the page, its bundles under _app/,
    // morph.db, and images -- lives under /morph, so a reverse proxy
    // sharing a hostname with MinimumViablePerseus needs only a single
    // /morph prefix rule to route it all here.
    paths: {
      base: "/morph",
    },
  },
};
