# Plan: Parallelizing `frequencies.aggregate`

`aggregate.clj` currently takes ~5 hours to walk the full corpus. Profiling
a 10-file sample (see method below) found the time split as: 51% in-memory
count compute (`process-tokens`/`all-submaps`), 25% `write-prior-counts!`,
12% `get-parses` SQL lookups, 6% `write-morph-counts!`, <1% everything else
(tokenizing, `write-document-counts!`).

Each phase below is independently measurable and ordered from lowest to
highest risk/complexity — land and re-measure before moving to the next.

## Phase 0 — Corpus-wide parse cache (no threading, do this first)

`walker/core.clj`'s `cached-lookup` builds a fresh `HashMap` per file
(`process-file!` calls it per-file). Vocabulary repeats heavily across
documents, so this cache never pays off across files.

- Change `cached-lookup` to accept an externally-supplied cache instead of
  creating its own.
- In `walk!`, create one cache (a `java.util.concurrent.ConcurrentHashMap`,
  since later phases will read it from multiple threads) and pass it down
  for the whole walk.
- Pure refactor, safe under single-threaded execution today — re-run the
  profiling harness before/after to confirm the win independent of
  everything else below.

## Phase 1 — Batch the frequency-table writes

`write-morph-counts!` / `write-prior-counts!` / `write-document-counts!`
(in `frequencies/aggregator.clj` and `frequencies/document.clj`) call
`jdbc/execute!` once per row, each one preparing a statement from scratch.
`write-prior-counts!` is the most expensive single stage (25% of wall time)
because of the previous×current feature cross-product.

- Convert each to build one `PreparedStatement` per write-call and reuse it
  via `addBatch`/`executeBatch` (or `next.jdbc/execute-batch!`), instead of
  one `execute!` per row.
- Independent of parallelism; should shrink that 25%/6%/<1% slice directly.
  Re-measure.

## Phase 2 — Split compute from writes, parallelize compute across files

SQLite allows one writer but many concurrent readers under WAL — the
existing setup (`PRAGMA journal_mode=WAL`) already enables this; we're
just not exploiting it.

- Split `process-file!` in `walker/core.clj` into:
  - `compute-file-counts` — SAX parse, tokenize, parse lookups (Phase 0
    cache), `process-tokens`. Read-only; takes its own connection.
  - `write-file-counts!` — the existing transactional write logic
    (`agg/write-morph-counts!`, `agg/write-prior-counts!`,
    `doc-freq/write-document-counts!`), unchanged, run only on the
    main/writer connection.
- In `walk!`: create a small fixed pool of read connections (start at
  `cores - 1`, since the main thread is also writing), and run
  `compute-file-counts` over `xml-files` via `pmap` (or an
  `ExecutorService` if `pmap`'s eager chunking turns out to overrun memory
  on large files). The main thread drains the resulting lazy seq in order
  and calls `write-file-counts!` on the single existing write connection —
  same per-file logging/try-catch as today, just decoupled from compute.

## Phase 3 — Verify and tune

- Re-run the profiling harness (same `with-redefs`-based timing approach
  used for the investigation) on a fixed file sample after each phase, not
  just at the end — confirms which phase actually moved the needle.
- Watch for: SQLite "database is locked" (shouldn't occur — only one write
  connection); GC pressure from `all-submaps` map churn under concurrency
  (more reader threads = more concurrent garbage; may need to size the
  pool down if this becomes the new bottleneck); pmap's lazy-seq
  backpressure if writes can't keep up with compute.
- Pick final pool size empirically rather than guessing.

## Profiling method (for re-measuring)

Wrap the relevant functions (`walker/extract-tokens`, `parses/get-parses`,
`walker/process-tokens`, `agg/write-morph-counts!`,
`agg/write-prior-counts!`, `doc-schema/write-document-counts!`) with
`with-redefs` and `System/nanoTime` timers, against a copy of `morph.db`
and a small representative subset of corpus files (a couple of author
directories is enough — keep directory structure intact rather than
flattening files into one dir, since filenames collide across corpora).
