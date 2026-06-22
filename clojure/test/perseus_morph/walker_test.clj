(ns perseus-morph.walker-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [perseus-morph.migrations :as migrations]
            [perseus-morph.sqlite :as sqlite]
            [perseus-morph.walker.core :as walker]))

(deftest guess-language-code-test
  (testing "canonical-greekLit/canonical-latinLit/First1KGreek filenames"
    (is (= "grc" (walker/guess-language-code "tlg0012.tlg001.perseus-grc2.xml")))
    (is (= "lat" (walker/guess-language-code "phi0119.phi017.perseus-lat2.xml")))
    (is (= "grc" (walker/guess-language-code "tlg0639.tlg001.1st1K-grc1.xml"))))

  (testing "non-Greek/Latin and non-text files are nil"
    (is (nil? (walker/guess-language-code "tlg0012.tlg001.perseus-eng3.xml")))
    (is (nil? (walker/guess-language-code "tlg0643.tlg001.1st1K-mul1.xml")))
    (is (nil? (walker/guess-language-code "__cts__.xml")))))

(deftest document-id-test
  (testing "the corpus file's own basename, sans extension, stands in for the
            obsolete Perseus catalog id"
    (is (= "tlg0012.tlg001.perseus-grc2"
           (walker/document-id "tlg0012.tlg001.perseus-grc2.xml")))))

(defn- write-temp-xml ^java.io.File [contents]
  (let [file (java.io.File/createTempFile "corpus-walker-test" ".xml")]
    (.deleteOnExit file)
    (spit file contents)
    file))

(deftest extract-tokens-test
  (testing "tokenizes primary text, skipping <note> content"
    (let [file (write-temp-xml
                "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">
                   <text xml:lang=\"grc\">
                     <body>
                       <div type=\"book\" n=\"1\">
                         <l n=\"1\">μῆνιν ἄειδε <note>this is an editor's note, not text</note>θεὰ</l>
                       </div>
                     </body>
                   </text>
                 </TEI>")]
      (is (= ["μῆνιν" "ἄειδε" "θεὰ"] (walker/extract-tokens file)))))

  (testing "skips speaker labels and stage directions too"
    (let [file (write-temp-xml
                "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">
                   <text xml:lang=\"lat\">
                     <body>
                       <sp><speaker>PALAESTRA</speaker><stage>flens</stage><l>uae mihi</l></sp>
                     </body>
                   </text>
                 </TEI>")]
      (is (= ["uae" "mihi"] (walker/extract-tokens file)))))

  (testing "a note with no surrounding whitespace doesn't merge into a run-on token"
    (let [file (write-temp-xml
                "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">
                   <text xml:lang=\"lat\"><body><l>uae<note>nota</note>mihi</l></body></text>
                 </TEI>")]
      (is (= ["uae" "mihi"] (walker/extract-tokens file))))))

(deftest process-tokens-test
  (testing "morph counts accumulate per token, prior counts as bigrams between
            tokens, and document counts accumulate per candidate lemma"
    (let [noun {:part_of_speech "noun"}
          verb {:part_of_speech "verb"}
          lookup {"noun-word" {1 [noun]}
                  "verb-word" {2 [verb]}}
          {:keys [morph-counts prior-counts document-counts]}
 (walker/process-tokens "grc" "doc-a" lookup ["noun-word" "verb-word"])]
      (is (= 1.0 (get morph-counts ["grc" noun])))
      (is (= 1.0 (get morph-counts ["grc" verb])))
      (is (= 2.0 (get morph-counts ["grc" {}])))
      (is (= 1.0 (get prior-counts ["grc" noun verb])))
  (is (= 1.0 (get document-counts ["doc-a" 1])))
  (is (= 1.0 (get document-counts ["doc-a" 2])))))

  (testing "a token absent from the dictionary contributes no morph or document
            counts and breaks the bigram chain"
    (let [noun {:part_of_speech "noun"}
          lookup {"noun-word" {1 [noun]} "unknown" {}}
          {:keys [morph-counts prior-counts document-counts]}
 (walker/process-tokens "grc" "doc-a" lookup ["noun-word" "unknown" "noun-word"])]
      (is (= 2.0 (get morph-counts ["grc" noun])))
      (is (empty? prior-counts))
   (is (= 2.0 (get document-counts ["doc-a" 1]))))))

(defn- temp-db []
  (let [file (java.io.File/createTempFile "corpus-walker-test" ".db")]
    (.deleteOnExit file)
    (let [db (sqlite/datasource (.getAbsolutePath file))]
      (migrations/migrate! db)
      db)))

(defn- insert-parse! [db {:keys [headword language-code form part-of-speech]}]
  (jdbc/execute! db ["INSERT INTO lemmas (headword, sequence_number, language_code)
                      VALUES (?, -1, ?)" headword language-code])
  (let [lemma-id (:lemmas/id (jdbc/execute-one! db ["SELECT id FROM lemmas WHERE headword = ?" headword]))]
    (jdbc/execute! db ["INSERT INTO parses (lemma_id, form, part_of_speech, dedup_key)
                        VALUES (?, ?, ?, ?)"
                        lemma-id form part-of-speech part-of-speech])))

(deftest process-file!-test
  (testing "an end-to-end Greek file: tokenize, look up parses, write counts"
    (let [db (temp-db)
          file (write-temp-xml
                "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">
                   <text xml:lang=\"grc\">
                     <body><l>μῆνιν ἄειδε</l></body>
                   </text>
                 </TEI>")
          renamed (io/file (.getParent file) "tlg0012.tlg001.perseus-grc2.xml")]
      (.renameTo file renamed)
      (.deleteOnExit renamed)

      (insert-parse! db {:headword "mh=nis" :language-code "grc" :form "μῆνιν" :part-of-speech "noun"})
      (insert-parse! db {:headword "a)ei/dw" :language-code "grc" :form "ἄειδε" :part-of-speech "verb"})
      (let [result (walker/process-file! db renamed (java.util.HashMap.))]
        (is (= "grc" (:language-code result)))
        (is (= 2 (:token-count result)))
        (is (= "tlg0012.tlg001.perseus-grc2" (:document-id result)))
        (is (= 1.0 (:count (jdbc/execute-one! db ["SELECT count FROM morph_frequencies
                                                    WHERE part_of_speech = 'noun'"]
                                               {:builder-fn rs/as-unqualified-maps}))))
        (is (= 1.0 (:count (jdbc/execute-one! db ["SELECT count FROM prior_frequencies
                                                    WHERE previous_part_of_speech = 'noun'
                                                      AND current_part_of_speech = 'verb'"]
                                               {:builder-fn rs/as-unqualified-maps}))))
(is (= 1.0 (:weighted_frequency
            (jdbc/execute-one! db ["SELECT df.weighted_frequency FROM document_frequencies df
                                      JOIN lemmas l ON df.lemma_id = l.id
                                     WHERE l.headword = 'mh=nis'
                                       AND df.document_id = 'tlg0012.tlg001.perseus-grc2'"]
                                       {:builder-fn rs/as-unqualified-maps})))))))

  (testing "a non-Greek/Latin file (by filename) is skipped without writing anything"
    (let [db (temp-db)
          file (write-temp-xml "<TEI><text><body><l>hello</l></body></text></TEI>")
          renamed (io/file (.getParent file) "tlg0012.tlg001.perseus-eng3.xml")]
      (.renameTo file renamed)
      (.deleteOnExit renamed)
      (is (nil? (walker/process-file! db renamed (java.util.HashMap.)))))))

(deftest walk!-test
  (testing "walks a directory of files, computing across a reader pool and
            writing counts serially -- same end result as process-file!,
            just pipelined"
    (let [db (temp-db)
          dir (java.io.File/createTempFile "corpus-walker-test-dir" "")]
      (.delete dir)
      (.mkdir dir)
      (.deleteOnExit dir)
      (insert-parse! db {:headword "mh=nis" :language-code "grc" :form "μῆνιν" :part-of-speech "noun"})
      (insert-parse! db {:headword "a)ei/dw" :language-code "grc" :form "ἄειδε" :part-of-speech "verb"})
      (let [file-1 (io/file dir "tlg0012.tlg001.perseus-grc2.xml")
            file-2 (io/file dir "tlg0012.tlg002.perseus-grc1.xml")]
        (spit file-1 "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">
                        <text xml:lang=\"grc\"><body><l>μῆνιν ἄειδε</l></body></text>
                      </TEI>")
        (spit file-2 "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">
                        <text xml:lang=\"grc\"><body><l>μῆνιν</l></body></text>
                      </TEI>")
        (.deleteOnExit file-1)
        (.deleteOnExit file-2))
      (let [result (walker/walk! db (.getAbsolutePath dir) :pool-size 2)]
        (is (= 2 (:files-processed result)))
        (is (= 3 (:tokens-processed result)))
        (is (= 2.0 (:count (jdbc/execute-one! db ["SELECT count FROM morph_frequencies
                                                    WHERE part_of_speech = 'noun'"]
                                               {:builder-fn rs/as-unqualified-maps}))))))))
