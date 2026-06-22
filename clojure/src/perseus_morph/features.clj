(ns perseus-morph.features
  "Shared definitions for the morphological feature columns that appear on
   `parses` rows (see perseus-morph.loader.schema), used both by the morph-XML
   loader (to compute `dedup_key`) and by the frequency aggregator (to
   compute the equivalent `feature_key` on morph_frequencies/prior_frequencies).
   Mirrors the feature set perseus.morph.MorphCode used to pack into a
   single morph_code string per language."
  (:require [clojure.string]))

(def feature-tag->column
  "Map of <feature-tag> -> parses column name, for every tag that can
   appear in an <analysis> besides form/lemma/orth."
  {"pos" :part_of_speech
   "person" :person
   "number" :number
   "tense" :tense
   "mood" :mood
   "voice" :voice
   "gender" :gender
   "case" :grammatical_case
   "degree" :degree
   "dialect" :dialect
   "feature" :other
   "prefix" :prefix
   "object" :object
   "definite" :definite
   "possessive" :possessive})

(def feature-columns
  "Stable order of the feature columns themselves (parses/morph_frequencies
   column names, not XML tags)."
  (vec (sort (vals feature-tag->column))))

(defn fold-key
  "Folds a row's feature columns into a single NOT NULL string, in stable
   column order, so a UNIQUE constraint across them can detect duplicates
   even though SQLite treats NULL <> NULL. An empty feature map folds to
   the empty string."
  [row]
  (clojure.string/join "" (map #(get row % "") feature-columns)))
