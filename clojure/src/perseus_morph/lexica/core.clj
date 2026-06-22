(ns perseus-morph.lexica.core
  "Loads a TEI lexicon XML file (entry/entryFree with sense children) into
   SQLite, ported from perseus.voting.SenseLoader. Each <sense> becomes one
   row in the `senses` table, keyed by its entry/sense number pair (parsed
   out of the sense's `id` attribute, e.g. \"n12.34\" -> entry 12, sense 34)
   plus the lexicon's document id. Each <entry>/<entryFree> also becomes one
   row in the `entries` table, holding its full text -- unlike `senses`,
   this isn't a SenseLoader-derived table: the original system instead read
   entries on demand from the source XML by byte offset (see
   perseus.document.Chunk.getText), which this port avoids needing to do at
   request time."
  (:require [next.jdbc :as jdbc]
            [perseus-morph.lexica.xml-parser :as xml-parser]))

(def ^:private id-pattern
  "Extends perseus.voting.VoteManager.ID_PATTERN (n(\\d+)\\.(\\d+)) to allow
   letter suffixes on either half, e.g. \"n14773a.9\" or \"n0.9a\". Lettered
   entry/sense ids distinguish homonyms and lettered sub-senses, so both
   halves are kept as strings rather than parsed to ints."
  #"n(\d+[a-zA-Z]*)\.(\d+[a-zA-Z]*)")

(def ^:private meaning-tags
  "Mirrors SenseLoaderHandler's constructor: which markup tag wraps each
   tr/gloss/hi child's text, keyed by lexicon document id. Lexicons not
   listed here default to \"i\" (plain italics)."
  {"Perseus:text:1999.04.0057" "g"
   "Perseus:text:1999.04.0058" "g"
   "Perseus:text:1999.04.0072" "g"
   "Perseus:text:1999.04.0073" "g"
   "Perseus:text:1999.04.0059" "l"
   "Perseus:text:1999.04.0060" "l"})

(defn meaning-tag [lexicon-id]
  (get meaning-tags lexicon-id "i"))

(defn- parse-ids
  "Splits a sense's `id` attribute (e.g. \"n12.34\" or \"n14773a.9b\") into
   [entry-id sense-id], or [\"-1\" \"-1\"] (with a warning) if it doesn't
   match, mirroring SenseLoader.insertSense's handling of a malformed id."
  [id]
  (if-let [[_ entry sense] (re-matches id-pattern (or id ""))]
    [entry sense]
    (do (println "WARN: Error getting IDs for" id)
        ["-1" "-1"])))

(defn clear-existing!
  "Deletes any existing senses and entries for `lexicon-id`, mirroring
   SenseLoader.clearExisting (extended to the entries table, which
   SenseLoader had no equivalent of)."
  [db lexicon-id]
  (jdbc/execute! db ["DELETE FROM senses WHERE document_id = ?" lexicon-id])
  (jdbc/execute! db ["DELETE FROM entries WHERE document_id = ?" lexicon-id]))

(defn- insert-sense! [db row]
  (jdbc/execute! db
                 ["INSERT INTO senses
                     (entry_id, sense_id, document_id, lemma, sense, level, definition)
                   VALUES (?, ?, ?, ?, ?, ?, ?)"
                  (:entry-id row) (:sense-id row) (:document-id row)
                  (:lemma row) (:sense row) (:level row) (:definition row)]))

(defn- insert-entry! [db lexicon-id key text]
  (jdbc/execute! db
                 ["INSERT INTO entries (document_id, key, text) VALUES (?, ?, ?)"
                  lexicon-id key text]))

(defn load!
  "Parses `filename`'s entries, inserting one `senses` row per <sense> and
   one `entries` row per <entry>/<entryFree>. `lexicon-id` is the lexicon's
   Perseus document id (used as both tables' document_id, and to look up
   the meaning-tag for this lexicon's senses)."
  [db lexicon-id filename]
  (let [sense-count (volatile! 0)
        entry-count (volatile! 0)]
    (xml-parser/parse-lexicon!
     filename (meaning-tag lexicon-id)
     (fn [{:keys [key id n level short-def]}]
       (let [[entry-id sense-id] (parse-ids id)]
         (insert-sense! db
                        {:entry-id entry-id
                         :sense-id sense-id
                         :document-id lexicon-id
                         :lemma (str "entry=" key)
                         :sense n
                         :level (if level (Integer/parseInt level) -1)
                         :definition short-def})
         (vswap! sense-count inc)))
     (fn [{:keys [key text]}]
       (insert-entry! db lexicon-id key text)
       (vswap! entry-count inc)))
    {:senses @sense-count :entries @entry-count}))
