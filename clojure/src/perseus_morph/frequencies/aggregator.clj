(ns perseus-morph.frequencies.aggregator
  "Port of perseus.morph.MorphCodeAggregator's counting algorithm: for each
   token, every subset of its candidate parses' feature combinations gets a
   fractional frequency count (1/n per ambiguous parse), and bigram
   \"prior\" counts are accumulated between successive (non-stoplisted)
   parses. This namespace is pure accumulation + flush; it has no notion of
   a corpus or token stream, so it can be exercised against synthetic parse
   rows (see frequencies/aggregator_test.clj) before any corpus walker
   exists.

   `counts` maps, as accumulated by update-morph-counts/update-prior-counts,
   are plain Clojure maps keyed by a vector identifying the row (language
   code plus the feature submap(s) themselves, which are ordinary Clojure
   maps and so work fine as map keys), so the caller can thread state
   through a token loop with plain reduce instead of mutable state."
  (:require [clojure.string]
            [next.jdbc :as jdbc]
            [perseus-morph.features :as features]))

(defn feature-map
  "Picks the morphological feature columns off a parse row, dropping
   absent (nil) ones. Mirrors MorphCode.getFeatures, which the new schema
   makes unnecessary as a decode step since features are already plain
   columns on the row."
  [parse-row]
  (reduce (fn [m col] (if-let [v (get parse-row col)] (assoc m col v) m))
          {}
          features/feature-columns))

(defn all-submaps
  "Every subset of `features`, including the empty map. Direct port of
   getAllSubmaps, but folds in the explicit `submaps.add(new HashMap<>())`
   that MorphCodeAggregator/updateMorphCounts did at the call site, since
   every caller needs it (it's what makes morph_frequencies also track a
   per-language total token count, in the all-features-absent row)."
  [features]
  (loop [submaps #{features}
         to-expand [features]]
    (if-let [current (first to-expand)]
      (let [children (->> (keys current)
                          (map #(dissoc current %))
                          (remove submaps))]
        (recur (into submaps children) (into (rest to-expand) children)))
      (conj submaps {}))))

(defn feature-key
  "The same NOT-NULL fold used for `parses.dedup_key`, reused as the
   uniqueness key for morph_frequencies/prior_frequencies rows."
  [features]
  (features/fold-key features))

(defn update-morph-counts
  "Given a token's already-deduplicated list of candidate parse rows,
   accumulates `1 / (count parses)` into `counts` (a map of
   {[language-code feature-submap] count}) for every submap of every
   parse's features -- mirroring updateMorphCounts being called once per
   parse with parseFrequency = 1.0 / parseList.size()."
  [counts language-code parses]
  (if (empty? parses)
    counts
    (let [parse-frequency (/ 1.0 (count parses))]
      (reduce (fn [counts parse-row]
                (reduce (fn [counts submap]
                          (update counts [language-code submap] (fnil + 0.0) parse-frequency))
                        counts
                        (all-submaps (feature-map parse-row))))
              counts
              parses))))

(defn update-prior-counts
  "Bigram counting between `previous-parses` (the prior token's
   non-stoplisted candidate parses, or nil/empty if there is none yet) and
   `current-parse` (one candidate parse of the current token), weighted by
   `parse-frequency` (the same 1/n weighting used in update-morph-counts).
   Accumulates into `counts`, a map of
   {[language-code previous-feature-map current-feature-map] count}.

   Whether `previous-parses` gets updated to the current token's parses for
   the *next* call is the caller's decision (mirroring the original: a
   stoplisted token's parses are not carried forward, but the token that
   *follows* a stoplisted token still gets compared against whatever
   came before it)."
  [counts language-code previous-parses current-parse parse-frequency]
  (let [current-features (feature-map current-parse)]
    (reduce (fn [counts previous-parse]
              (let [previous-features (feature-map previous-parse)
                    k [language-code previous-features current-features]]
                (update counts k (fnil + 0.0) parse-frequency)))
            counts
            previous-parses)))

(defn- feature-row
  "A row's feature columns, with `feature_key` for uniqueness alongside
   the individual columns themselves (so the table stays queryable on a
   single feature, not just the opaque combined key)."
  [features]
  (assoc (zipmap features/feature-columns (map #(get features %) features/feature-columns))
         :feature_key (feature-key features)))

(defn write-morph-counts!
  "Upserts the accumulated morph-count map into morph_frequencies,
   mirroring writeMorphCounts's
   'INSERT ... ON DUPLICATE KEY UPDATE count=count+?'. Every row shares the
   same column set (feature-row always emits every feature column, nil or
   not), so the whole map can go through one prepared statement via
   `execute-batch!` instead of preparing + executing a fresh statement per
   row."
  [db counts]
  (when (seq counts)
    (let [rows (for [[[language-code features] freq] counts]
                 (assoc (feature-row features) :language_code language-code :count freq))
          columns (keys (first rows))
          column-names (map name columns)
          placeholders (repeat (count columns) "?")
          sql (format "INSERT INTO morph_frequencies (%s) VALUES (%s)
                       ON CONFLICT (language_code, feature_key)
                       DO UPDATE SET count = count + excluded.count"
                      (clojure.string/join ", " column-names)
                      (clojure.string/join ", " placeholders))]
      (jdbc/execute-batch! db sql (map (fn [row] (map row columns)) rows) {}))))

(defn write-prior-counts!
  "Upserts the accumulated bigram-count map into prior_frequencies,
   mirroring writePriorCounts's
   'INSERT ... ON DUPLICATE KEY UPDATE count=count+?'. As in
   write-morph-counts!, every row shares the same column set, so the whole
   map goes through one prepared statement via `execute-batch!`."
  [db counts]
  (when (seq counts)
    (let [rows (for [[[language-code previous-features current-features] freq] counts]
                 (let [previous-row (->> (feature-row previous-features)
                                         (reduce-kv #(assoc %1 (keyword (str "previous_" (name %2))) %3) {}))
                       current-row (->> (feature-row current-features)
                                        (reduce-kv #(assoc %1 (keyword (str "current_" (name %2))) %3) {}))]
                   (merge previous-row current-row
                          {:language_code language-code :count freq})))
          columns (keys (first rows))
          column-names (map name columns)
          placeholders (repeat (count columns) "?")
          sql (format "INSERT INTO prior_frequencies (%s) VALUES (%s)
                       ON CONFLICT (language_code, previous_feature_key, current_feature_key)
                       DO UPDATE SET count = count + excluded.count"
                      (clojure.string/join ", " column-names)
                      (clojure.string/join ", " placeholders))]
      (jdbc/execute-batch! db sql (map (fn [row] (map row columns)) rows) {}))))
