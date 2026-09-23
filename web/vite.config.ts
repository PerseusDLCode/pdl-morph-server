import { svelte } from "@sveltejs/vite-plugin-svelte";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [svelte()],
  // sql.js-httpvfs's worker/wasm are consumed as plain URLs (new
  // URL("...", import.meta.url)), not bundled inline -- see
  // src/morph/httpVfsDb.ts callers.
  worker: {
    format: "es",
  },
});
