CREATE TABLE IF NOT EXISTS languages (
  code TEXT PRIMARY KEY,
  name TEXT NOT NULL
)
--;;
INSERT OR IGNORE INTO languages (code, name) VALUES ('grc', 'Greek')
--;;
INSERT OR IGNORE INTO languages (code, name) VALUES ('lat', 'Latin')
--;;
INSERT OR IGNORE INTO languages (code, name) VALUES ('ara', 'Arabic')
--;;
CREATE TABLE IF NOT EXISTS lemmas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  headword TEXT NOT NULL,
  headword_normalized TEXT,
  bare_headword TEXT,
  sequence_number INTEGER NOT NULL DEFAULT -1,
  language_code TEXT NOT NULL REFERENCES languages (code),
  UNIQUE (headword, sequence_number, language_code)
)
--;;
CREATE INDEX IF NOT EXISTS idx_lemmas_headword
  ON lemmas (headword, language_code)
--;;
CREATE INDEX IF NOT EXISTS idx_lemmas_headword_normalized
  ON lemmas (headword_normalized, language_code)
--;;
CREATE TABLE IF NOT EXISTS parses (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  lemma_id INTEGER NOT NULL REFERENCES lemmas (id) ON DELETE CASCADE,
  form TEXT NOT NULL,
  form_normalized TEXT,
  expanded_form TEXT,
  bare_form TEXT,
  part_of_speech TEXT,
  person TEXT,
  number TEXT,
  tense TEXT,
  mood TEXT,
  voice TEXT,
  gender TEXT,
  grammatical_case TEXT,
  degree TEXT,
  dialect TEXT,
  other TEXT,
  prefix TEXT,
  object TEXT,
  definite TEXT,
  possessive TEXT,
  dedup_key TEXT NOT NULL,
  UNIQUE (lemma_id, form, dedup_key)
)
--;;
CREATE INDEX IF NOT EXISTS idx_parses_form ON parses (form)
--;;
CREATE INDEX IF NOT EXISTS idx_parses_bare_form ON parses (bare_form)
--;;
CREATE INDEX IF NOT EXISTS idx_parses_form_normalized ON parses (form_normalized)
--;;
CREATE INDEX IF NOT EXISTS idx_parses_lemma_id ON parses (lemma_id)
--;;
CREATE TABLE IF NOT EXISTS document_frequencies (
  document_id TEXT NOT NULL,
  lemma_id INTEGER NOT NULL REFERENCES lemmas (id) ON DELETE CASCADE,
  weighted_frequency REAL NOT NULL DEFAULT 0,
  UNIQUE (document_id, lemma_id)
)
--;;
CREATE TABLE IF NOT EXISTS morph_frequencies (
  language_code TEXT NOT NULL REFERENCES languages (code),
  feature_key TEXT NOT NULL,
  count REAL NOT NULL DEFAULT 0,
  UNIQUE (language_code, feature_key)
)
--;;
CREATE INDEX IF NOT EXISTS idx_morph_frequencies_language
  ON morph_frequencies (language_code)
--;;
CREATE TABLE IF NOT EXISTS prior_frequencies (
  language_code TEXT NOT NULL REFERENCES languages (code),
  previous_feature_key TEXT NOT NULL,
  current_feature_key TEXT NOT NULL,
  count REAL NOT NULL DEFAULT 0,
  UNIQUE (language_code, previous_feature_key, current_feature_key)
)
--;;
CREATE INDEX IF NOT EXISTS idx_prior_frequencies_language
  ON prior_frequencies (language_code)
--;;
CREATE TABLE IF NOT EXISTS senses (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  entry_id TEXT NOT NULL DEFAULT '-1',
  sense_id TEXT NOT NULL DEFAULT '-1',
  document_id TEXT NOT NULL,
  lemma TEXT NOT NULL,
  sense TEXT,
  level INTEGER,
  definition TEXT
)
--;;
CREATE INDEX IF NOT EXISTS idx_senses_document_lemma
  ON senses (document_id, lemma)
--;;
CREATE INDEX IF NOT EXISTS idx_senses_entry_sense_document
  ON senses (entry_id, sense_id, document_id)
--;;
CREATE TABLE IF NOT EXISTS entries (
  document_id TEXT NOT NULL,
  key TEXT NOT NULL,
  text TEXT,
  UNIQUE (document_id, key)
)
