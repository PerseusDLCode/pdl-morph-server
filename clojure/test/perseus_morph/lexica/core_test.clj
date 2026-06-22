(ns perseus-morph.lexica.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [perseus-morph.lexica.core :as lexica]
            [perseus-morph.migrations :as migrations]
            [perseus-morph.sqlite :as sqlite]))

(defn- temp-db []
  (let [file (java.io.File/createTempFile "lexica-core-test" ".db")]
    (.deleteOnExit file)
    (let [db (sqlite/datasource (.getAbsolutePath file))]
      (migrations/migrate! db)
      db)))

(defn- write-temp-xml ^java.io.File [contents]
  (let [file (java.io.File/createTempFile "lexica-core-test" ".xml")]
    (.deleteOnExit file)
    (spit file contents)
    file))

(defn- query-all [db sql]
  (jdbc/execute! db [sql] {:builder-fn rs/as-unqualified-maps}))

(deftest meaning-tag-test
  (testing "lexicons known to use <g> or <l> markup get their dedicated tag"
    (is (= "g" (lexica/meaning-tag "Perseus:text:1999.04.0057")))
    (is (= "l" (lexica/meaning-tag "Perseus:text:1999.04.0059"))))

  (testing "any other lexicon id defaults to plain italics"
    (is (= "i" (lexica/meaning-tag "Perseus:text:1999.04.9999")))))

(deftest load!-test
  (let [db (temp-db)
        file (write-temp-xml
              "<entryFree key=\"mh=nis\">
                 <sense id=\"n1.1\" n=\"1\" level=\"1\">wrath, <tr>anger</tr></sense>
                 <sense id=\"n1.2\" n=\"2\">grudge</sense>
                 <sense id=\"bad-id\" n=\"3\"><tr>unreadable id</tr></sense>
               </entryFree>")]

    (testing "returns a count of senses and entries loaded"
      (is (= {:senses 3 :entries 1} (lexica/load! db "Perseus:text:1999.04.0057" file))))

    (testing "a well-formed id is split into entry_id/sense_id, and the lemma column
              holds SenseLoader's lexQuery (\"entry=\" + the entry's key), not the headword"
      (is (= {:entry_id "1" :sense_id "1" :document_id "Perseus:text:1999.04.0057"
              :lemma "entry=mh=nis" :sense "1" :level 1
              :definition "wrath, <g>anger</g>"}
             (-> (query-all db "SELECT * FROM senses WHERE sense_id = '1'")
                 first
                 (dissoc :id)))))

    (testing "a sense with no level attribute defaults level to -1, matching SenseLoader"
      (is (= -1 (:level (first (query-all db "SELECT level FROM senses WHERE sense_id = '2'"))))))

    (testing "a sense whose id doesn't match the entry.sense pattern gets entry_id/sense_id -1"
      (is (= {:entry_id "-1" :sense_id "-1"}
             (-> (query-all db "SELECT entry_id, sense_id FROM senses WHERE sense = '3'")
                 first
                 (select-keys [:entry_id :sense_id])))))

    (testing "a lettered id (homonym entry / lettered sub-sense) keeps its letter suffix"
      (let [file (write-temp-xml
                  "<entryFree key=\"lettered\">
                     <sense id=\"n14773a.9b\" n=\"9b\"><tr>lettered</tr></sense>
                   </entryFree>")]
        (lexica/load! db "Perseus:text:1999.04.0057" file)
        (is (= {:entry_id "14773a" :sense_id "9b"}
               (-> (query-all db "SELECT entry_id, sense_id FROM senses WHERE sense = '9b'")
                   first
                   (select-keys [:entry_id :sense_id]))))))))

(deftest load!-stores-full-untruncated-definitions-test
  (let [db (temp-db)
        long-text (apply str (repeat 150 "x"))
        file (write-temp-xml
              (str "<entryFree key=\"verbosus\">"
                   "<sense id=\"n1.1\" n=\"1\"><tr>" long-text "</tr></sense>"
                   "</entryFree>"))]
    (lexica/load! db "Perseus:text:1999.04.0057" file)
    (testing "definition is stored in full, unlike the legacy 100-char-capped short_definition"
      (let [definition (:definition (first (query-all db "SELECT definition FROM senses")))]
        (is (= (str "<g>" long-text "</g>") definition))))))

(deftest load!-stores-the-full-entry-test
  (let [db (temp-db)
        file (write-temp-xml
              "<entryFree key=\"mh=nis\"><orth>mh=nis</orth>
                 <sense id=\"n1.1\" n=\"1\"><tr>wrath</tr></sense>
               </entryFree>")]
    (lexica/load! db "lsj" file)
    (testing "the entry's full subtree is stored once, alongside its senses"
      (is (= [{:document_id "lsj" :key "mh=nis"
               :text "<orth>mh=nis</orth><sense id=\"n1.1\" n=\"1\"><tr>wrath</tr></sense>"}]
             (query-all db "SELECT document_id, key, text FROM entries"))))))

(deftest clear-existing!-test
  (let [db (temp-db)
        file (write-temp-xml
              "<entryFree key=\"abc\"><sense id=\"n1.1\" n=\"1\">def</sense></entryFree>")]
    (lexica/load! db "lexicon-a" file)
    (lexica/load! db "lexicon-b" file)

    (testing "clearing one lexicon's senses and entries leaves other lexicons' alone"
      (lexica/clear-existing! db "lexicon-a")
      (is (empty? (query-all db "SELECT * FROM senses WHERE document_id = 'lexicon-a'")))
      (is (empty? (query-all db "SELECT * FROM entries WHERE document_id = 'lexicon-a'")))
      (is (= 1 (count (query-all db "SELECT * FROM senses WHERE document_id = 'lexicon-b'"))))
      (is (= 1 (count (query-all db "SELECT * FROM entries WHERE document_id = 'lexicon-b'")))))))
