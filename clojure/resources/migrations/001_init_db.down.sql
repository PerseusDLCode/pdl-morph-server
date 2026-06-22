DROP TABLE IF EXISTS entries
--;;
DROP INDEX IF EXISTS idx_senses_entry_sense_document
--;;
DROP INDEX IF EXISTS idx_senses_document_lemma
--;;
DROP TABLE IF EXISTS senses
--;;
DROP INDEX IF EXISTS idx_prior_frequencies_language
--;;
DROP TABLE IF EXISTS prior_frequencies
--;;
DROP INDEX IF EXISTS idx_morph_frequencies_language
--;;
DROP TABLE IF EXISTS morph_frequencies
--;;
DROP TABLE IF EXISTS document_frequencies
--;;
DROP INDEX IF EXISTS idx_parses_lemma_id
--;;
DROP INDEX IF EXISTS idx_parses_bare_form
--;;
DROP INDEX IF EXISTS idx_parses_form_normalized
--;;
DROP INDEX IF EXISTS idx_parses_form
--;;
DROP TABLE IF EXISTS parses
--;;
DROP INDEX IF EXISTS idx_lemmas_headword_normalized
--;;
DROP INDEX IF EXISTS idx_lemmas_headword
--;;
DROP TABLE IF EXISTS lemmas
--;;
DROP TABLE IF EXISTS languages
