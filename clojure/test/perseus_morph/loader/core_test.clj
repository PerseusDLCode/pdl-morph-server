(ns perseus-morph.loader.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [perseus-morph.frequencies.document :as doc-freq]
            [perseus-morph.loader.core :as loader]
            [perseus-morph.migrations :as migrations]
            [perseus-morph.sqlite :as sqlite]))

(defn- temp-db []
  (let [file (java.io.File/createTempFile "loader-core-test" ".db")]
    (.deleteOnExit file)
    (let [db (sqlite/datasource (.getAbsolutePath file))]
      (migrations/migrate! db)
      db)))

(defn- insert-lemma! [db headword language-code]
  (jdbc/execute! db
                 ["INSERT INTO lemmas (headword, sequence_number, language_code)
                   VALUES (?, -1, ?)"
                  headword language-code])
  (:lemmas/id (jdbc/execute-one! db ["SELECT id FROM lemmas WHERE headword = ?" headword])))

(defn- insert-parse! [db lemma-id form]
  (jdbc/execute! db
                 ["INSERT INTO parses (lemma_id, form, dedup_key) VALUES (?, ?, ?)"
                  lemma-id form form]))

(defn- query-count [db sql & params]
  (:n (jdbc/execute-one! db (into [sql] params) {:builder-fn rs/as-unqualified-maps})))

(deftest delete-by-language!-test
  (testing "deleting a language's lemmas cascades to their parses and
            document_frequencies rows, via lemma_id's ON DELETE CASCADE,
            but leaves other languages' rows untouched"
    (let [db (temp-db)
          grc-lemma (insert-lemma! db "luw/w" "grc")
          lat-lemma (insert-lemma! db "facio" "lat")]
      (insert-parse! db grc-lemma "lu/w")
      (insert-parse! db lat-lemma "facio")
      (doc-freq/write-document-counts! db {["doc-a" grc-lemma] 1.0
                                            ["doc-a" lat-lemma] 1.0})

      (loader/delete-by-language! db "grc")

      (is (= 0 (query-count db "SELECT COUNT(*) AS n FROM lemmas WHERE language_code = ?" "grc")))
      (is (= 0 (query-count db "SELECT COUNT(*) AS n FROM parses WHERE lemma_id = ?" grc-lemma)))
      (is (= 0 (query-count db "SELECT COUNT(*) AS n FROM document_frequencies WHERE lemma_id = ?" grc-lemma)))

      (is (= 1 (query-count db "SELECT COUNT(*) AS n FROM lemmas WHERE language_code = ?" "lat")))
      (is (= 1 (query-count db "SELECT COUNT(*) AS n FROM parses WHERE lemma_id = ?" lat-lemma)))
      (is (= 1 (query-count db "SELECT COUNT(*) AS n FROM document_frequencies WHERE lemma_id = ?" lat-lemma))))))
