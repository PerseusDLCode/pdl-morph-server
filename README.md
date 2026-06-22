# new-morpheus

HTTP API for morphological lookups, reading from `morph.db` (the SQLite
database produced by the Clojure ingestion pipeline in `../clojure`).

## Setup

Requires the `morph.db` file to exist at `../clojure/morph.db` (run the
Clojure ingestion pipeline first if it doesn't), migrated to the current
schema (all subcommands — `load`, `aggregate`, `ingest` — call
`migrations/migrate!` automatically; see `clojure/README.md`) and with
lexica ingested via `clj -M:ingest` so the `senses`/`entries` tables
backing the API response are populated.

```sh
uv sync
```

## Running

Dev server (autoreload, binds to `127.0.0.1`):

```sh
uv run new-morpheus-dev
```

Production server (binds to `0.0.0.0`, multiple workers):

```sh
uv run new-morpheus
```

Both respect `PORT` (default `8000`); the production server also respects
`WEB_CONCURRENCY` (default `4`) for worker count.

## Tests

```sh
uv run pytest
```

## API

`GET /morph?word=<word>&language=<language_code>&document_id=<optional>&prior_word=<optional>`

Returns the candidate lemmas and parses for `word`. `word` should be Unicode
(e.g. `μῆνιν`), the encoding `parses.form` is now stored in for every
language; Beta Code (e.g. `mh=nin`) is still accepted as a fallback for
Greek -- if the Unicode lookup comes up empty, it's retried once converted
to Unicode via the `beta_code` package (see `morph.lookup_parses`). Each
lemma includes its `senses` (short, per-sense glosses from the ingested
lexicon, e.g. LSJ for Greek) and `entries` (the full text of that lexicon's
whole dictionary article for the lemma, so the UI doesn't need to re-read
the source XML for a complete view). If `document_id` is given, each lemma
also includes its `document_frequency` (weighted frequency of that lemma
within the given document), or `null` if none is recorded. `document_id`
and `prior_word` (the preceding word in the text, also Unicode) also feed
disambiguation: whichever candidate parse scores highest once corpus-wide
form frequency, in-document lemma frequency, and prior-word bigram
frequency are averaged together gets `is_winner: true`.

Example: looking up μῆνιν (`mh=nin`), the first word of the *Iliad*
(tlg0012.tlg001.perseus-grc2:1.1) -- document_id is whole-document only, so
the API has no notion of "line 1.1" beyond it being a word in that document:

```sh
curl -G "http://127.0.0.1:8000/morph" \
  --data-urlencode "word=μῆνιν" \
  --data-urlencode "language=grc" \
  --data-urlencode "document_id=tlg0012.tlg001.perseus-grc2"
```
