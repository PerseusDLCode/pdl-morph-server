import { asset } from "$app/paths";
import { openHttpVfsDb } from "./morph/httpVfsDb.js";
import type { MorphDb } from "./morph/db.js";

// sql.js-httpvfs's worker/wasm are plain assets, not ES modules -- this
// is the same `new URL(..., import.meta.url)` pattern its README shows
// for webpack5, and Vite resolves it the same way.
const workerUrl = new URL("sql.js-httpvfs/dist/sqlite.worker.js", import.meta.url);
const wasmUrl = new URL("sql.js-httpvfs/dist/sql-wasm.wasm", import.meta.url);

let dbPromise: Promise<MorphDb> | null = null;

// morph.db is a single, large, unchanging file for the lifetime of a
// page load, so one connection is opened lazily on first use and shared
// by every subsequent query.
export function getDb(): Promise<MorphDb> {
  if (dbPromise === null) {
    dbPromise = openHttpVfsDb({
      workerUrl: workerUrl.toString(),
      wasmUrl: wasmUrl.toString(),
      databaseUrl: asset("/morph.db"),
    });
  }
  return dbPromise;
}
