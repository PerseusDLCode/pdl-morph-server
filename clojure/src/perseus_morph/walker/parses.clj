(ns perseus-morph.walker.parses
  "Parse lookup by surface form, replacing perseus.morph.Parse.getParses
   (word, languageCode). Used by perseus-morph.walker to find every
   candidate parse of a token."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn get-parses
  "Returns every parses row whose comparable Unicode form matches `word`
   exactly for `language-code` (matched via its lemma, since parses has no
   language_code of its own -- see perseus-morph.loader.schema), grouped by
   lemma_id into {lemma-id [parse-row ...]} — mirroring the original's
   Map<Lemma, List<Parse>>, which the prior-count bigram logic and stoplist
   check need grouped per-lemma rather than flat."
  [db word language-code]
  (let [rows (jdbc/execute! db
                            ["SELECT parses.*
                                FROM parses
                                JOIN lemmas ON parses.lemma_id = lemmas.id
                               WHERE parses.form = ?
                                 AND lemmas.language_code = ?"
                             word language-code]
                            {:builder-fn rs/as-unqualified-maps})]
    (group-by :lemma_id rows)))
