import { createDbWorker, type WorkerHttpvfs } from "sql.js-httpvfs";
import type { QueryExecResult, SqlValue } from "sql.js";
import type { MorphDb } from "./db.js";

export interface HttpVfsDbOptions {
  // URLs to the worker/wasm assets sql.js-httpvfs ships (dist/sqlite.worker.js,
  // dist/sql-wasm.wasm) -- how these resolve depends on the bundler; see
  // web/README.md once Phase 6 wires up the real app.
  workerUrl: string;
  wasmUrl: string;
  // URL to the plain (unchunked) morph.db, served by a static host that
  // supports byte-range requests (Phase 2's nginx config).
  databaseUrl: string;
  // SQLite's page size for this DB (see deploy/nginx.conf / the Clojure
  // pipeline for how this is set) -- each query fetches in multiples of
  // this many bytes.
  requestChunkSize?: number;
  maxBytesToRead?: number;
}

export interface HttpVfsDb extends MorphDb {
  worker: WorkerHttpvfs;
}

// sql.js binds named parameters by object key, where the key must include
// the same prefix character (":", here) used in the SQL text itself --
// unlike our MorphDb interface's plain (unprefixed) param names, which
// mirror the SQLAlchemy-style :name placeholders morph.py's raw SQL uses.
function prefixParams(params: Record<string, unknown>): Record<string, SqlValue> {
  const prefixed: Record<string, SqlValue> = {};
  for (const [key, value] of Object.entries(params)) {
    prefixed[`:${key}`] = (value ?? null) as SqlValue;
  }
  return prefixed;
}

function execResultsToObjects<T>(results: QueryExecResult[]): T[] {
  if (results.length === 0) {
    return [];
  }
  const { columns, values } = results[0] as QueryExecResult;
  return values.map((row) => {
    const obj: Record<string, SqlValue> = {};
    columns.forEach((col, i) => {
      obj[col] = row[i] as SqlValue;
    });
    return obj as T;
  });
}

// Browser MorphDb implementation: queries morph.db in-place over HTTP
// range requests via sql.js-httpvfs, rather than downloading the whole
// file. Satisfies the same MorphDb interface web/src/morph/db.ts's query
// functions are written against, so the query/scoring layer (Phase 3)
// needs no changes to run against this instead of the node:sqlite
// adapter used for tests.
export async function openHttpVfsDb(options: HttpVfsDbOptions): Promise<HttpVfsDb> {
  const worker = await createDbWorker(
    [
      {
        from: "inline",
        config: {
          serverMode: "full",
          requestChunkSize: options.requestChunkSize ?? 4096,
          url: options.databaseUrl,
        },
      },
    ],
    options.workerUrl,
    options.wasmUrl,
    options.maxBytesToRead,
  );

  return {
    worker,
    async all<T>(sql: string, params: Record<string, unknown> = {}): Promise<T[]> {
      const results = await worker.db.exec(sql, prefixParams(params));
      return execResultsToObjects<T>(results);
    },
    async get<T>(sql: string, params: Record<string, unknown> = {}): Promise<T | undefined> {
      const results = await worker.db.exec(sql, prefixParams(params));
      return execResultsToObjects<T>(results)[0];
    },
  };
}
