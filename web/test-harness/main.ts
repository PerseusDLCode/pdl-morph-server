// Throwaway page (Phase 2 verification): proves sql.js-httpvfs actually
// issues partial-content range requests against a plain morph.db served
// by nginx (deploy/nginx.conf), rather than downloading the whole file,
// and that a real query through the Phase 3 query layer returns correct
// results when run against that range-fetched DB. Not part of the
// shipped app -- Phase 6 replaces this with the real Svelte frontend.
import { openHttpVfsDb } from "../src/morph/httpVfsDb.js";
import { buildMorphResponse } from "../src/morph/index.js";

const output = document.querySelector("#output") as HTMLPreElement;

function log(line: string): void {
  output.textContent += `${line}\n`;
}

async function main(): Promise<void> {
  log("opening morph.db over HTTP range requests...");
  const db = await openHttpVfsDb({
    workerUrl: "./sqlite.worker.js",
    wasmUrl: "./sql-wasm.wasm",
    databaseUrl: "/morph.db",
  });
  log("opened.");

  const before = await db.worker.worker.bytesRead;
  const response = await buildMorphResponse(db, "λόγος", "grc");
  const after = await db.worker.worker.bytesRead;

  log(`bytesRead for one lookup: ${after - before} (full file is ~3.4GB)`);
  log(JSON.stringify(response, null, 2));
}

main().catch((err) => log(`ERROR: ${err instanceof Error ? err.stack : String(err)}`));
