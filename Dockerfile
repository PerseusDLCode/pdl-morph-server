# ================================================================
# pdl-morph-server
#
# Two-stage build:
#   1. builder  — uses the official Clojure image to run the
#                 ingestion pipeline and produce clojure/morph.db
#   2. runtime  — lean Python image that serves the live FastAPI
#                 app (uv run new-morpheus) against that DB
# ================================================================

# ================================================================
# Stage 1 — build the SQLite morphology database with Clojure
# ================================================================
FROM clojure:temurin-21-tools-deps-bookworm-slim AS builder

# git + ca-certificates are needed to clone the lexica repos below.
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# ----------------------------------------------------------------
# Lexica data (cloned into SEPARATE dirs — the ingest paths below
# expect /lexica/lexica and /lexica/LSJ_GreekUnicode).
# ----------------------------------------------------------------
ARG LEXICA_DIR=/lexica
RUN git clone --depth 1 https://github.com/PerseusDL/lexica.git ${LEXICA_DIR}/lexica
RUN git clone --depth 1 https://github.com/gcelano/LSJ_GreekUnicode.git ${LEXICA_DIR}/LSJ_GreekUnicode

# ----------------------------------------------------------------
# Clojure sources + morph data.
# The .jsonl.tar archives contain bare filenames, so extract them
# INTO data/ (that's where `../data/*.jsonl` resolves from clojure/).
# ----------------------------------------------------------------
COPY clojure/ clojure/
COPY data/ data/

RUN tar -xf data/greek.morph.jsonl.tar -C data
RUN tar -xf data/latin.morph.jsonl.tar -C data

# ----------------------------------------------------------------
# Run the ingestion pipeline. Each `clj -M:<alias>` writes to
# ./morph.db in the working dir, so run everything from clojure/
# to produce clojure/morph.db (the path the server reads at runtime).
# ----------------------------------------------------------------
WORKDIR /app/clojure

RUN clj -M:load ../data/greek.morph.jsonl
RUN clj -M:load ../data/latin.morph.jsonl

# Lexicon keys must match what the server queries (src/new_morpheus/morph.py:
# LEXICA_BY_LANGUAGE -> "LSJ" for Greek, "lewis-short" for Latin).
RUN clj -M:ingest LSJ ${LEXICA_DIR}/LSJ_GreekUnicode
RUN clj -M:ingest "Lewis & Short" ${LEXICA_DIR}/lexica/CTS_XML_TEI/perseus/pdllex/lat/ls/lat.ls.perseus-eng2.xml

# aggregate walks a corpus dir (skips non-primary texts itself).
RUN clj -M:aggregate ../data

# ================================================================
# Stage 2 — runtime: the live FastAPI server
# ================================================================
FROM python:3.12-slim AS runtime

# git is required because uv resolves kodon-py from a git source
# (see [tool.uv.sources] in pyproject.toml); curl + ca-certificates
# are for installing uv.
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# ----------------------------------------------------------------
# uv
# ----------------------------------------------------------------
RUN curl -LsSf https://astral.sh/uv/install.sh | sh
ENV PATH="/root/.local/bin:${PATH}"

WORKDIR /app

# ----------------------------------------------------------------
# Python dependencies (cached layer — only re-runs when the
# manifests change).
# ----------------------------------------------------------------
COPY pyproject.toml README.md uv.lock ./

ENV UV_PYTHON=3.13
RUN uv sync --no-dev --no-install-project

# ----------------------------------------------------------------
# Source code + install the project (registers new-morpheus*).
# ----------------------------------------------------------------
COPY src/ src/
RUN uv sync --no-dev

# ----------------------------------------------------------------
# The morphology DB built in stage 1. db.py reads it from
# <repo root>/clojure/morph.db, i.e. /app/clojure/morph.db.
# ----------------------------------------------------------------
COPY --from=builder /app/clojure/morph.db clojure/morph.db

# ----------------------------------------------------------------
# Runtime — production server (no autoreload). `serve` reads PORT
# from the environment; 5000 matches the dev deployment convention.
# ----------------------------------------------------------------
ENV PORT=5000
EXPOSE 5000
CMD ["uv", "run", "new-morpheus"]
