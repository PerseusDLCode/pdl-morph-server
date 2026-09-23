import { sveltekit } from "@sveltejs/kit/vite";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [sveltekit()],
  // sql.js-httpvfs's worker/wasm are consumed as plain URLs (new
  // URL("...", import.meta.url)), not bundled inline -- see
  // src/morph/httpVfsDb.ts callers.
  worker: {
    format: "es",
  },
});
