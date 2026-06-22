(ns perseus-morph.frequencies.aggregator-test
  (:require [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [perseus-morph.frequencies.aggregator :as agg]
            [perseus-morph.migrations :as migrations]
            [perseus-morph.sqlite :as sqlite]))

(defn- temp-db
  "A scratch SQLite file (not :memory: -- next.jdbc opens a fresh connection
   per call, and an in-memory DB's contents vanish as soon as no connection
   is open), deleted once the JVM exits."
  []
  (let [file (java.io.File/createTempFile "aggregator-test" ".db")]
    (.deleteOnExit file)
    (let [db (sqlite/datasource (.getAbsolutePath file))]
      (migrations/migrate! db)
      db)))

(defn- query-one [db sql]
  (jdbc/execute-one! db [sql] {:builder-fn rs/as-unqualified-maps}))

(deftest all-submaps-test
  (testing "every subset of a 2-feature map, including the empty map"
    (is (= #{{:part_of_speech "noun" :grammatical_case "genitive"}
             {:part_of_speech "noun"}
             {:grammatical_case "genitive"}
             {}}
           (agg/all-submaps {:part_of_speech "noun" :grammatical_case "genitive"}))))

  (testing "a single feature still includes the empty map"
    (is (= #{{:part_of_speech "noun"} {}}
           (agg/all-submaps {:part_of_speech "noun"}))))

  (testing "no features is just the empty map"
    (is (= #{{}} (agg/all-submaps {})))))

(deftest update-morph-counts-test
  (testing "a single unambiguous parse increments every submap by 1.0"
    (let [parse {:part_of_speech "noun" :grammatical_case "genitive"}
          counts (agg/update-morph-counts {} "grc" [parse])]
      (is (= 1.0 (get counts ["grc" {:part_of_speech "noun" :grammatical_case "genitive"}])))
      (is (= 1.0 (get counts ["grc" {:part_of_speech "noun"}])))
      (is (= 1.0 (get counts ["grc" {:grammatical_case "genitive"}])))
      (is (= 1.0 (get counts ["grc" {}])))))

  (testing "two ambiguous parses for the same word split weight 0.5 each"
    (let [parses [{:part_of_speech "noun"} {:part_of_speech "verb"}]
          counts (agg/update-morph-counts {} "grc" parses)]
      (is (= 0.5 (get counts ["grc" {:part_of_speech "noun"}])))
      (is (= 0.5 (get counts ["grc" {:part_of_speech "verb"}])))
      ;; both parses' empty submap accumulates into the same total-count row
      (is (= 1.0 (get counts ["grc" {}])))))

  (testing "accumulates across repeated calls (successive tokens)"
    (let [parse {:part_of_speech "noun"}
          counts (-> {}
                      (agg/update-morph-counts "grc" [parse])
                      (agg/update-morph-counts "grc" [parse]))]
      (is (= 2.0 (get counts ["grc" {:part_of_speech "noun"}])))))

  (testing "an empty parse list (word not found at all) is a no-op"
    (is (= {} (agg/update-morph-counts {} "grc" [])))))

(deftest update-prior-counts-test
  (testing "accumulates a bigram between one previous and one current parse"
    (let [previous [{:part_of_speech "noun"}]
          current {:part_of_speech "verb"}
          counts (agg/update-prior-counts {} "grc" previous current 1.0)]
      (is (= 1.0 (get counts ["grc" {:part_of_speech "noun"} {:part_of_speech "verb"}])))))

  (testing "ambiguous previous parses each contribute a separate bigram"
    (let [previous [{:part_of_speech "noun"} {:part_of_speech "adjective"}]
          current {:part_of_speech "verb"}
          counts (agg/update-prior-counts {} "grc" previous current 0.5)]
      (is (= 0.5 (get counts ["grc" {:part_of_speech "noun"} {:part_of_speech "verb"}])))
      (is (= 0.5 (get counts ["grc" {:part_of_speech "adjective"} {:part_of_speech "verb"}])))))

  (testing "no previous parses (e.g. start of document, or after a stoplisted token
            whose lemma was entirely in the stoplist) contributes nothing"
    (is (= {} (agg/update-prior-counts {} "grc" [] {:part_of_speech "verb"} 1.0))))

  (testing "stoplisting is the caller's responsibility: simulating a stoplisted
            token by simply not calling update-prior-counts for it, and not
            carrying its parses forward as `previous-parses` for the next token"
    (let [tok-a {:part_of_speech "noun"}
          tok-c {:part_of_speech "verb"}
          counts (agg/update-prior-counts {} "grc" [tok-a] tok-c 1.0)]
      (is (= 1.0 (get counts ["grc" {:part_of_speech "noun"} {:part_of_speech "verb"}])))
      (is (nil? (get counts ["grc" {:part_of_speech "particle"} {:part_of_speech "verb"}]))))))

(deftest write-morph-counts!-test
  (let [db (temp-db)
        parse {:part_of_speech "noun" :grammatical_case "genitive"}
        counts (-> {}
                   (agg/update-morph-counts "grc" [parse])
                   (agg/update-morph-counts "grc" [parse]))]
    (agg/write-morph-counts! db counts)
    (testing "every submap is upserted as its own row, with both the individual
              feature columns and the folded feature_key populated"
      (is (= {:language_code "grc" :part_of_speech "noun"
              :grammatical_case "genitive" :feature_key "genitivenoun" :count 2.0}
             (query-one db "SELECT language_code, part_of_speech, grammatical_case,
                                   feature_key, count
                              FROM morph_frequencies
                             WHERE feature_key = 'genitivenoun'")))
      (is (= 2.0 (:count (query-one db "SELECT count FROM morph_frequencies WHERE feature_key = ''")))))
    (testing "writing again accumulates onto the existing row instead of duplicating it"
      (agg/write-morph-counts! db (agg/update-morph-counts {} "grc" [parse]))
      (is (= 3.0 (:count (query-one db "SELECT count FROM morph_frequencies WHERE feature_key = 'genitivenoun'"))))
      (is (= 1 (:n (query-one db "SELECT COUNT(*) AS n FROM morph_frequencies WHERE feature_key = 'genitivenoun'")))))))

(deftest write-prior-counts!-test
  (let [db (temp-db)
        counts (agg/update-prior-counts {} "grc" [{:part_of_speech "noun"}]
                                        {:part_of_speech "verb"} 1.0)]
    (agg/write-prior-counts! db counts)
    (testing "the bigram is upserted with both previous_* and current_* columns populated"
      (is (= {:language_code "grc" :previous_part_of_speech "noun"
              :current_part_of_speech "verb" :count 1.0}
             (query-one db "SELECT language_code, previous_part_of_speech,
                                   current_part_of_speech, count
                              FROM prior_frequencies"))))
    (testing "writing again accumulates onto the existing row instead of duplicating it"
      (agg/write-prior-counts! db counts)
      (is (= 2.0 (:count (query-one db "SELECT count FROM prior_frequencies"))))
      (is (= 1 (:n (query-one db "SELECT COUNT(*) AS n FROM prior_frequencies")))))))
