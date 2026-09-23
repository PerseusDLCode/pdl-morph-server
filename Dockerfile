# ================================================================
# pdl-morph-server
#
# Three-stage build:
#   1. builder     — uses the official Clojure image to run the
#                     ingestion pipeline and produce clojure/morph.db
#   2. web-builder — builds the Svelte frontend (web/), which queries
#                     morph.db itself, in-browser, over HTTP range
#                     requests (see web/src/morph/httpVfsDb.ts)
#   3. runtime     — nginx, serving the built frontend + morph.db as
#                     static files (see deploy/nginx.conf); no
#                     application server needed, since nothing
#                     server-side runs queries anymore
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
# to produce clojure/morph.db (the file served statically in stage 3).
# ----------------------------------------------------------------
WORKDIR /app/clojure

RUN clj -M:load ../data/greek.morph.jsonl
RUN clj -M:load ../data/latin.morph.jsonl

# Lexicon keys must match what the frontend queries (web/src/morph/db.ts:
# LEXICA_BY_LANGUAGE -> "LSJ" for Greek, "lewis-short" for Latin).
RUN clj -M:ingest LSJ ${LEXICA_DIR}/LSJ_GreekUnicode
RUN clj -M:ingest "Lewis & Short" ${LEXICA_DIR}/lexica/CTS_XML_TEI/perseus/pdllex/lat/ls/lat.ls.perseus-eng2.xml
RUN clj -M:ingest "Middle Liddell" ../data/viaf66541464.001.perseus-eng1.xml

# Plain-text Logeion short defs (one-line glosses shown in the headword
# summary; see web/src/morph/db.ts SHORT_DEF_DOCUMENT_ID). Detected
# by extension, not TEI XML -- see lexica/shortdef.clj.
RUN clj -M:ingest Logeion-Greek-Shortdef ../data/ShortdefsforOKLemmas.txt
RUN clj -M:ingest Logeion-Latin-Shortdef ../data/LogeionLatinshortdefs.txt

# aggregate walks a corpus dir (skips non-primary texts itself).
RUN clj -M:aggregate ../data

# ================================================================
# Stage 2 — build the Svelte frontend
# ================================================================
FROM node:22-slim AS web-builder

RUN corepack enable

WORKDIR /app/web

COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY web/ ./
RUN pnpm build

# ================================================================
# Stage 3 — runtime: nginx serving static files, no app server
# ================================================================
FROM nginx:alpine AS runtime

COPY deploy/nginx.conf /etc/nginx/nginx.conf
COPY --from=web-builder /app/web/dist /usr/share/nginx/html
COPY --from=builder /app/clojure/morph.db /usr/share/nginx/html/morph.db

EXPOSE 8080
