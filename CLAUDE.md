# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`pdl-morph-server` is a FastAPI service for morphological analysis of Ancient Greek and Latin. It exposes two endpoints built on Perseus Morpheus data with pre-computed frequency and dictionary indexes for O(1) lookups.

- `/analyze` — returns JSON morphological analyses for a word form
- `/morph` — returns an HTML page with analysis, corpus frequency, and dictionary definitions (LSJ for Greek, Lewis & Short for Latin)

## Setup

Requires Python 3.11 and PDM.

```bash
# Install dependencies
pdm install

# Download large XML morphology files (~400 MB, stored in pdl-morph-server/)
python tools/setup_morph_data.py

# Build frequency and dictionary indexes (one-time, ~30–60 seconds)
python pdl-morph-server/build_indexes.py
```

## Testing

```bash
# Unit tests only (no server startup, ~3s)
pdm run pytest tests/test_betacode.py -v

# Full suite including integration tests (~30s, requires all data files)
pdm run pytest tests/ -v

# Single test
pdm run pytest tests/test_words.py::TestLatinWords::test_amor_is_noun -v
```

Integration tests (`test_api.py`, `test_frequency.py`, `test_words.py`) require the morphology indexes and the XML files. The session-scoped `client` fixture starts the full FastAPI lifespan once per `pytest` run.

**Morpheus field abbreviations** (relevant when writing word tests): Morpheus uses abbreviated values — `"sg"`/`"pl"` for number, `"nom"`/`"acc"`/`"gen"`/`"dat"`/`"abl"`/`"voc"` for case, `"masc"`/`"fem"`/`"neut"` for gender, `"pres"`/`"imperf"`/`"fut"` for tense, `"ind"`/`"subj"`/`"inf"` for mood. Lemmas for homonyms carry a `#N` suffix (e.g. `"sum#1"`, `"edo#1"`).

## Running the Server

```bash
pdm run uvicorn pdl-morph-server.main:app --reload
```

The server takes 30–60 seconds to start while loading indexes into memory.

## Architecture

### Startup

`main.py` uses FastAPI's lifespan context to load both Greek and Latin data on startup:
1. Streams `greek.morph.xml` / `latin.morph.xml` and builds in-memory dicts mapping normalized form → list of `Analysis` objects
2. Loads pre-computed frequency tables from `.pkl` files (`greek_morph_freqs.pkl`, `latin_morph_freqs.pkl`)
3. Loads dictionary indexes from `.json` files (`lsj_index.json`, `ls_index.json`)

After startup all queries are O(1) dict lookups. The trade-off is ~400–500 MB memory footprint.

### Form Normalization

- **Greek**: grave→acute accent conversion, secondary accent removal, Beta Code cleanup; supports both Unicode Greek input and Perseus Beta Code
- **Latin**: lowercase normalization only

### Frequency Keys

Frequency pickle keys are stable sorted tuples of all feature tags `(lang, form, case, degree, dialect, feature, gender, lemma, mood, number, person, pos, tense, voice)` to avoid Python hash randomization across processes.

### Key Files

| File | Role |
|---|---|
| `pdl-morph-server/main.py` | FastAPI app, endpoints, startup logic, normalization |
| `pdl-morph-server/build_indexes.py` | One-time script to build `.pkl` and `.json` index files |
| `tools/setup_morph_data.py` | Downloads large XML morphology files from Tufts Box |

### Data Files (not in git)

| File | Description |
|---|---|
| `greek.morph.xml` / `latin.morph.xml` | Morpheus morphology XML (~400 MB combined) |
| `greek_morph_freqs.pkl` / `latin_morph_freqs.pkl` | Pre-computed frequency tables |
| `lsj_index.json` / `ls_index.json` | LSJ and Lewis & Short dictionary indexes |

The XML files are excluded from git (`.gitignore`) and must be downloaded via `setup_morph_data.py`.
