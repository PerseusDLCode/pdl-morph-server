# perseus-morph

A Clojure replacement for `perseus.morph.ParseLoader`: loads the
`greek.morph.jsonl` / `latin.morph.jsonl` morphology data into a
**SQLite** database instead of MySQL (via Hibernate).

## Differences from the original Java `ParseLoader`

- Lemmas are created on the fly from the `lemma` field in the morph
  JSONL itself, rather than matched against a pre-populated `lemmas` table
  (which in the original system came from a separate lexicon-loading step we
  don't have data for here).
- Morphological features (`pos`, `case`, `tense`, ...) are stored as plain
  columns on `parses` rather than packed into a compact per-language
  `morph_code` string (that encoding existed to save space in MySQL; it
  doesn't matter for SQLite).
- Forms/lemmas/headwords are stored as-is, in genuine
  Unicode for every language (`form`/`expanded_form`/`headword`).

## CLI

A single entry point dispatches to subcommands:

```
clj -M -m perseus-morph.main <subcommand> [options] [args]
```

Subcommands:

| Subcommand | Description |
|---|---|
| `load` | Load a morph JSONL file into the database |
| `aggregate` | Walk a corpus directory and aggregate frequency counts |
| `ingest` | Load a TEI lexicon XML file's senses into the database |
| `help` | Print help for a subcommand |

Existing aliases (`:load`, `:aggregate`, `:ingest`) also still work.

### load

```sh
clj -M:load ../data/greek.morph.jsonl
clj -M:load ../data/latin.morph.jsonl
```

By default this writes to `./morph.db` and deletes any existing rows for the
guessed language first. Options:

```
-d, --db PATH        Path to the SQLite database file (default: morph.db)
-l, --language CODE  Language code (guessed from the filename if omitted)
-n, --no-delete       Don't delete existing parses for this language first
-h, --help
```

### aggregate

Once `morph.db` has lemmas/parses loaded (see `load` subcommand above), walk a
directory of `canonical-greekLit` / `canonical-latinLit` / `First1KGreek`-style
TEI files to populate `morph_frequencies` and `prior_frequencies`
(`perseus-morph.frequencies.aggregator`'s tables), mirroring
`perseus.morph.MorphCodeAggregator`:

```sh
clj -M:aggregate /path/to/corpora
```

By default this writes to `./morph.db`. Files that don't look like a Greek or
Latin primary text (translations, `__cts__.xml` metadata, ...) are skipped; see
`perseus-morph.walker/guess-language-code`.

Each file's own CTS-style basename (e.g. `tlg0012.tlg001.perseus-grc2`)
stands in for a document id in `document_frequencies` (see
`perseus-morph.walker/document-id`), which tracks how often each
candidate lemma occurs *within that document specifically* -- the old
Perseus catalog ids (`Perseus:text:1999.01.0001`) that
`perseus.document.Query` resolved are obsolete, and this port has no
catalog to resolve them against in any case.

Options:

```
-d, --db PATH  Path to the SQLite database file (default: morph.db)
-h, --help
```

### ingest

To ingest a lexicon, use the `ingest` subcommand. For example, to ingest
_LSJ_ (under the key `"LSJ"`), run:

```
clj -M:ingest LSJ /path/to/LSJ
```

While you can use any keys you want, check [morph.py](../src/new_morpheus/morph.py) for
the expected dictionary names.

Options:

```
-d, --db PATH   Path to the SQLite database file (default: morph.db)
-n, --no-delete  Don't delete existing senses for this lexicon first
-h, --help
```

## Tests

```sh
clj -M:test
```
