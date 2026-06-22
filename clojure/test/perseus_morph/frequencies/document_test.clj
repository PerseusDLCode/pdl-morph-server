(ns perseus-morph.frequencies.document-test
  (:require [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [perseus-morph.frequencies.document :as doc-freq]
            [perseus-morph.migrations :as migrations]
            [perseus-morph.sqlite :as sqlite]))

(defn- temp-db
  "document_frequencies.lemma_id references lemmas (id) ON DELETE CASCADE,
   and foreign key enforcement is on, so a real lemmas table (and, for the
   write test, a real lemma row) must exist for inserts to succeed."
  []
  (let [file (java.io.File/createTempFile "document-freq-test" ".db")]
    (.deleteOnExit file)
    (let [db (sqlite/datasource (.getAbsolutePath file))]
      (migrations/migrate! db)
      db)))

(defn- query-one [db sql]
  (jdbc/execute-one! db [sql] {:builder-fn rs/as-unqualified-maps}))

(deftest update-document-counts-test
  (testing "an unambiguous token (one candidate lemma) counts as a full occurrence"
    (let [lemma-groups {1 [{:part_of_speech "verb"}]}
          counts (doc-freq/update-document-counts {} "doc-a" lemma-groups)]
      (is (= 1.0 (get counts ["doc-a" 1])))))

  (testing "an ambiguous token (two candidate lemmas) splits weight 0.5 each,
            regardless of how many parses each lemma has"
    (let [lemma-groups {1 [{:part_of_speech "verb"}]
                        2 [{:part_of_speech "noun"} {:part_of_speech "noun"}]}
          counts (doc-freq/update-document-counts {} "doc-a" lemma-groups)]
      (is (= 0.5 (get counts ["doc-a" 1])))
      (is (= 0.5 (get counts ["doc-a" 2])))))

  (testing "accumulates across repeated calls (successive tokens)"
    (let [lemma-groups {1 [{:part_of_speech "verb"}]}
          counts (-> {}
                     (doc-freq/update-document-counts "doc-a" lemma-groups)
                     (doc-freq/update-document-counts "doc-a" lemma-groups))]
      (is (= 2.0 (get counts ["doc-a" 1])))))

  (testing "a token absent from the dictionary (no candidate lemmas) is a no-op"
    (is (= {} (doc-freq/update-document-counts {} "doc-a" {})))))

(deftest write-document-counts!-test
  (let [db (temp-db)
        _ (jdbc/execute! db ["INSERT INTO lemmas (headword, sequence_number, language_code)
                              VALUES ('facio', -1, 'lat')"])
        lemma-groups {1 [{:part_of_speech "verb"}]}
        counts (-> {}
                   (doc-freq/update-document-counts "doc-a" lemma-groups)
                   (doc-freq/update-document-counts "doc-a" lemma-groups))]
    (doc-freq/write-document-counts! db counts)
    (testing "the lemma's weighted frequency is written, scoped to document and lemma"
      (is (= {:document_id "doc-a" :lemma_id 1 :weighted_frequency 2.0}
             (query-one db "SELECT document_id, lemma_id, weighted_frequency
                              FROM document_frequencies"))))
    (testing "writing again accumulates onto the existing row instead of duplicating it"
      (doc-freq/write-document-counts! db (doc-freq/update-document-counts {} "doc-a" lemma-groups))
      (is (= 3.0 (:weighted_frequency (query-one db "SELECT weighted_frequency FROM document_frequencies"))))
      (is (= 1 (:n (query-one db "SELECT COUNT(*) AS n FROM document_frequencies")))))))
