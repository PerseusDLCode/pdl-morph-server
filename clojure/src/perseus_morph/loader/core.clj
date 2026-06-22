(ns perseus-morph.loader.core
  "Loads a greek.morph.jsonl / latin.morph.jsonl file into SQLite, replacing
   perseus.morph.ParseLoader's MySQL/Hibernate-based loading. Lemmas are
   created on the fly from the lemma field in the morph JSONL itself (rather
   than matched against a pre-populated lexicon-derived `lemmas` table, as
   the original Java code did), since we don't have that lexicon data here.

   Forms and lemmas are taken as-is from the JSONL, which is expected to
   already be genuine Unicode (e.g. greek.morph.jsonl, not the old
   Beta Code greek.morph.xml) -- there is no Beta Code-to-Unicode
   transcoding step here any more."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string]
            [next.jdbc :as jdbc]
            [perseus-morph.features :as features]
            [perseus-morph.language :as lang])
  (:import (java.io File)))

(def ^:private feature-columns features/feature-tag->column)

(defn- dedup-key
  "Folds a row's feature columns into a single NOT NULL string so the
   `parses` UNIQUE constraint can actually detect duplicates; see the
   comment on dedup_key in perseus-morph.loader.schema."
  [row]
  (features/fold-key row))

(defn guess-language-code
  "Mirrors ParseLoader.main()'s filename-based language guess: the language
   code is the text before the first '.' in the filename (e.g.
   \"greek.morph.xml\" -> \"greek\"), canonicalized to its ISO 639 code
   (e.g. \"grc\")."
  [filename]
  (-> (File. (str filename)) .getName (clojure.string/split #"\.") first
      lang/canonicalize-code))

(defn- get-or-create-lemma!
  "Returns the id of the lemma matching [headword sequence-number
   language-code], creating it if necessary. `cache` is a mutable
   java.util.HashMap used to avoid a round-trip for repeated lemmas, mirroring
   ParseLoader.ParseHandler's matchCache."
  [db cache language-code headword sequence-number]
  (let [cache-key [headword sequence-number language-code]]
    (or (.get ^java.util.HashMap cache cache-key)
        (let [bare-headword (lang/bare-form headword)
              headword-normalized (lang/normalize-unicode headword)]
          (jdbc/execute! db
                         ["INSERT OR IGNORE INTO lemmas
                (headword, headword_normalized,
                 bare_headword, sequence_number, language_code)
              VALUES (?, ?, ?, ?, ?)"
                          headword headword-normalized
                          bare-headword sequence-number language-code])
          (let [id (-> (jdbc/execute-one! db
                                          ["SELECT id FROM lemmas
                           WHERE headword = ? AND sequence_number = ?
                             AND language_code = ?"
                                           headword sequence-number language-code])
                       :lemmas/id)]
            (.put ^java.util.HashMap cache cache-key id)
            id)))))

(defn- analysis->parse-row
  [db cache language-code analysis]
  (let [form-raw (get analysis "form")
        lemma-text (get analysis "lemma")]
    (when (and form-raw lemma-text)
      (let [form (lang/normalize-form language-code form-raw)
            [headword sequence-number] (lang/parse-lemma-text lemma-text)
            lemma-id (get-or-create-lemma! db cache language-code
                                           headword sequence-number)
            expanded-form (or (get analysis "orth") form)
            bare-form (lang/bare-form form)
            ;; Lets unicode Greek search input (with or without accents)
            ;; match this row, the same way headword_normalized does for
            ;; lemmas -- see lang/normalize-unicode.
            form-normalized (lang/normalize-unicode form)
            features (reduce-kv
                      (fn [m tag column]
                        (if-let [v (get analysis tag)]
                          (assoc m column v)
                          m))
                      {}
                      feature-columns)
            row (merge {:lemma_id lemma-id
                        :form form
                        :form_normalized form-normalized
                        :expanded_form expanded-form
                        :bare_form bare-form}
                       features)]
        (assoc row :dedup_key (dedup-key row))))))

(defn- insert-parse! [db row]
  (let [columns (keys row)
        placeholders (clojure.string/join ", " (repeat (count columns) "?"))
        column-names (clojure.string/join ", " (map name columns))]
    (jdbc/execute! db
                   (into [(format "INSERT OR IGNORE INTO parses (%s) VALUES (%s)"
                                  column-names placeholders)]
                         (vals row)))))

(defn delete-by-language!
  "Deletes all lemmas for `language-code` (and, via their lemma_id's
   ON DELETE CASCADE, their now-orphaned parses and document_frequencies
   rows), mirroring ParseLoader's --delete-existing default behavior.
   Requires `db` to have foreign key enforcement on (see
   perseus-morph.sqlite/datasource), since SQLite ignores ON DELETE clauses
   when foreign key enforcement is off."
  [db language-code]
  (jdbc/execute! db ["DELETE FROM lemmas WHERE language_code = ?" language-code]))

(defn- parse-jsonl-line
  [line]
  (when-let [s (not-empty line)]
    (json/read-str s :key-fn identity)))

(defn load!
  "Streams `filename`'s JSONL records, inserting a lemma (if needed)
   and a parse row for each one, all within a single transaction (SQLite is
   much faster this way than autocommitting per row). Logs progress every
   `log-every` analyses."
  [db filename language-code & {:keys [log-every] :or {log-every 5000}}]
  (let [cache (java.util.HashMap.)
        analysis-count (volatile! 0)
        parse-count (volatile! 0)]
    (jdbc/with-transaction [tx db]
      (with-open [rdr (io/reader filename)]
        (doseq [line (line-seq rdr)]
          (when-let [analysis (parse-jsonl-line line)]
            (vswap! analysis-count inc)
            (if-let [row (analysis->parse-row tx cache language-code analysis)]
              (do (insert-parse! tx row)
                  (vswap! parse-count inc))
              (println "WARN: analysis lacks form or lemma:" analysis))
            (when (zero? (mod @analysis-count log-every))
              (println (format "[%9d analyses, %9d parses] %s"
                               @analysis-count @parse-count
                               (get analysis "form"))))))))
    {:analyses @analysis-count :parses @parse-count}))
