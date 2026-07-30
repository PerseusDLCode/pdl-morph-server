(ns perseus-morph.lexica.shortdef
  "Loads a plain-text \"lemma <whitespace> short definition\" file (e.g. the
   Logeion short-def exports under data/) into the `senses` table, as a
   lighter-weight alternative to perseus-morph.lexica.core's TEI XML
   ingestion. Each line becomes one `senses` row, keyed the same way
   lexica.core keys entries with no lettered homonym suffix: lemma =
   \"entry=\" + the line's headword (see morph.py's lookup_senses, which
   looks up senses.lemma as \"entry=\" + headword [+ sequence_number]).
   There's no entry/sense id structure in these files (unlike the XML
   entry/sense numbering lexica.core parses out of TEI `id` attributes), so
   entry_id/sense_id are both written as \"-1\", and `sense`/`level` are left
   null."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [next.jdbc :as jdbc]))

(defn- parse-line
  "Splits a line on its first run of whitespace into [lemma definition],
   trimming both -- the Greek short-def files are tab-separated, but the
   Latin one uses runs of spaces (and even a single space in places), so
   splitting on any whitespace run rather than a literal tab handles both.
   Returns nil for blank lines or lines with no definition half."
  [line]
  (let [trimmed (str/trim line)]
    (when (seq trimmed)
      (when-let [[_ lemma definition] (re-matches #"(?s)(\S+)\s+(.*\S)" trimmed)]
        [lemma definition]))))

(defn- header-line?
  "True if `line` looks like the \"lemma\\tdef\" header row that
   ShortdefsforOKLemmas.txt (unlike the other short-def files) starts
   with."
  [line]
  (when-let [[lemma definition] (parse-line line)]
    (and (= (str/lower-case lemma) "lemma")
         (= (str/lower-case definition) "def"))))

(defn- insert-sense! [db document-id lemma definition]
  (jdbc/execute! db
                 ["INSERT INTO senses
                     (entry_id, sense_id, document_id, lemma, sense, level, definition)
                   VALUES (?, ?, ?, ?, ?, ?, ?)"
                  "-1" "-1" document-id (str "entry=" lemma) nil nil definition]))

(defn load!
  "Parses `filename`'s lines, inserting one `senses` row per non-blank line.
   `lexicon-id` is used as the senses' document_id (register it in
   morph.py's LEXICA_BY_LANGUAGE so lookup_senses actually queries it)."
  [db lexicon-id filename]
  (with-open [rdr (io/reader filename)]
    (let [lines (line-seq rdr)
          lines (if (and (seq lines) (header-line? (first lines)))
                  (rest lines)
                  lines)
          sense-count (volatile! 0)]
      (doseq [line lines]
        (when-let [[lemma definition] (parse-line line)]
          (insert-sense! db lexicon-id lemma definition)
          (vswap! sense-count inc)))
      {:senses @sense-count})))
