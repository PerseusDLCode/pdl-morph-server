(ns perseus-morph.walker.core
  "Walks a directory of canonical-{greek,latin}Lit / First1KGreek-style TEI
   XML files, tokenizing each Greek or Latin primary text and feeding the
   tokens through perseus-morph.frequencies.aggregator and
   perseus-morph.frequencies.document.

   Mirrors perseus.morph.MorphCodeAggregator's per-document loop
   (processToken / endDocument), but reads TEI files directly rather than
   from the original's Document/Chunk/Token database. Deliberately does not
   port MorphCodeAggregator#hasLemmasInStoplist: that method keys its
   perseus.util.Stoplist lookup on Lemma#toString(), which returns a
   multi-line debug dump (id/authorityName/headword/sequenceNumber/...),
   not the headword -- so it can never match an entry in a stoplist file of
   bare headwords, and hasLemmasInStoplist always returns false in
   practice. This port's every-token's-parses-carried-forward-as
   previous-parses behavior already matches that actual (if unintended)
   runtime behavior, so there is nothing to port here."
  (:require [clojure.java.io :as io]
            [clojure.string]
            [next.jdbc :as jdbc]
            [perseus-morph.frequencies.aggregator :as agg]
            [perseus-morph.frequencies.document :as doc-freq]
            [perseus-morph.language :as lang]
            [perseus-morph.walker.parses :as parses])
  (:import (java.io File)
           (javax.xml.parsers SAXParserFactory)
           (org.xml.sax Attributes InputSource)
           (org.xml.sax.helpers DefaultHandler)))

(def default-excluded-tags
  "Element names whose character content is paratextual rather than primary
   text, and so should not be tokenized: editorial notes, speaker labels
   and stage directions, section headings, bibliographic citations, and
   gaps in the source. (<milestone>, <lb>, <pb> etc. need no entry here --
   they're always empty elements, with no character content of their own.)"
  #{"note" "speaker" "stage" "head" "bibl" "label" "figure" "gap" "argument"})

(def ^:private filename-language-pattern
  "Matches the CTS canonical-corpus filename convention shared by
   canonical-greekLit, canonical-latinLit, and First1KGreek alike --
   '<work>.<text>.<source>-<langcode><version?>.xml', e.g.
   'tlg0012.tlg001.perseus-grc2.xml' or 'tlg0639.tlg001.1st1K-grc1.xml'.
   Deliberately filename-based rather than reading the TEI <text
   xml:lang=...> attribute: that attribute is frequently absent or placed
   on some other descendant (a <div type=\"translation\"> for translations,
   a <listBibl> for an embedded-language citation list, ...), so it isn't a
   reliable signal across this corpus collection, whereas every primary
   text file consistently carries its language in the filename."
  #"-([a-z]{2,3})\d*\.xml$")

(def ^:private known-language-codes
  "The ISO 639 codes guess-language-code recognizes from a corpus
   filename; anything else (translations' \"eng\", multi-language
   editions' \"mul\", ...) is treated as not Greek or Latin."
  #{"grc" "lat"})

(defn guess-language-code
  "The ISO 639 language code implied by `filename`'s CTS naming convention
   (see filename-language-pattern), or nil if it doesn't look like a Greek
   or Latin primary text at all (translations, apparatus-only files,
   __cts__.xml metadata, multi-language 'mul' editions, ...) -- the signal
   this namespace uses to skip non-Greek/Latin files without even opening
   them."
  [filename]
  (when-let [[_ code] (re-find filename-language-pattern (str filename))]
    (known-language-codes code)))

(defn- tag-text-handler
  "A SAX handler that appends every bit of character data outside
   `excluded-tags` to `buffer`, in document order. A space is appended
   whenever an excluded element starts or ends, so e.g. 'word <note>...
   </note> next' doesn't collapse into a single run-on token if the source
   has no whitespace of its own immediately around the <note>."
  ^DefaultHandler [excluded-tags ^StringBuilder buffer]
  (let [excluded-depth (volatile! 0)]
    (proxy [DefaultHandler] []
      (startElement [_uri _local-name ^String qname ^Attributes _attrs]
        (when (contains? excluded-tags qname)
          (.append buffer " ")
          (vswap! excluded-depth inc)))
      (endElement [_uri _local-name ^String qname]
        (when (contains? excluded-tags qname)
          (vswap! excluded-depth dec)
          (.append buffer " ")))
      (characters [chars start length]
        (when (zero? @excluded-depth)
          (.append buffer chars start length))))))

(def ^:private word-pattern
  "Letters and combining marks only -- punctuation, digits, and whitespace
   are all token boundaries. Elision marks (Greek ᾽/’, or the bare ASCII
   apostrophe greek.morph.unicode.xml's forms use) fall out as boundaries
   too, matching how morph.xml's own forms are recorded."
  #"[\p{L}\p{M}]+")

(defn extract-tokens
  "Parses `filename`'s TEI body, returning its primary text's tokens (a seq
   of Unicode word strings, in document order, skipping `excluded-tags`
   content). Does not consult the file's contents at all to decide *which*
   language it is -- see guess-language-code -- so this only needs calling
   once that's already confirmed to be \"greek\" or \"latin\"."
  [filename & {:keys [excluded-tags] :or {excluded-tags default-excluded-tags}}]
  (let [buffer (StringBuilder.)
        factory (SAXParserFactory/newInstance)
        sax-parser (.newSAXParser factory)]
    (with-open [stream (io/input-stream filename)]
      (.parse sax-parser (InputSource. stream) (tag-text-handler excluded-tags buffer)))
    (re-seq word-pattern (.toString buffer))))

(defn document-id
  "A surrogate document id for `filename`: the corpus file's own CTS-style
   basename (e.g. \"tlg0012.tlg001.perseus-grc2\"), used in place of the old
   Perseus catalog id ('Perseus:text:1999.01.0001') that
   perseus.document.Query resolved -- those ids are obsolete, and this port
   has no catalog to resolve them against anyway."
  [filename]
  (-> (File. (str filename)) .getName (clojure.string/replace #"\.xml$" "")))

(defn- token->form
  "Normalizes a raw Unicode corpus token into the same comparable string
   perseus-morph.loader.core stored as parses.form: per-language
   lowercasing only (lang/normalize-form's greek-lowercase, for Greek, just
   uncapitalizes a token's initial letter -- so a corpus token's accidental
   sentence-initial capital still matches a lowercase dictionary form,
   while a capitalized form like a proper noun's matches as-is). Both this
   corpus token and parses.form are genuine Unicode."
  [language-code token]
  (lang/normalize-form language-code token))

(defn process-tokens
  "Threads `tokens` through the aggregators, mirroring
   MorphCodeAggregator#processToken and WordFrequencyLoader#processToken
   together: each token's candidate parses (flattened across lemmas, since
   updateMorphCounts/addPriorCounts don't care which lemma a candidate parse
   belongs to) update the morph-count map for the token itself and the
   prior-count (bigram) map against the *previous* token's candidate parses,
   weighted by the same 1/n as update-morph-counts uses; the token's
   candidate *lemmas* (not yet flattened, and identified by lemma_id) separately
   update the document-count map, weighted 1/(distinct lemma count) the way
   WordFrequencyLoader's LEMMA strategy does. `lookup` is (fn [token]
   parses-grouped-by-lemma) -- it owns turning a raw corpus token into the
   comparable form parses.form was stored in (see token->form) as well as
   any caching, e.g. cached-lookup wrapping perseus-morph.walker.parses/get-parses,
   the way MorphCodeAggregator's `cachedParses` did -- so this function only
   has to know about tokens and their resulting parses, not encodings or the
   db.

   Returns {:morph-counts ... :prior-counts ... :document-counts ...}, ready
   for aggregator/write-morph-counts!, write-prior-counts!, and
   perseus-morph.frequencies.document/write-document-counts!."
  [language-code document-id lookup tokens]
  (:counts
   (reduce
    (fn [{:keys [previous-parses counts]} token]
      (let [lemma-groups (lookup token)
            current-parses (mapcat val lemma-groups)
            n (count current-parses)
            counts (-> counts
                       (update :morph-counts agg/update-morph-counts language-code current-parses)
                       (update :document-counts doc-freq/update-document-counts
                               document-id lemma-groups))
            counts (if (pos? n)
                     (update counts :prior-counts
                             (fn [prior-counts]
                               (reduce (fn [prior-counts current-parse]
                                         (agg/update-prior-counts prior-counts language-code
                                                                  previous-parses current-parse
                                                                  (/ 1.0 n)))
                                       prior-counts
                                       current-parses)))
                     counts)]
        {:previous-parses current-parses :counts counts}))
    {:previous-parses nil :counts {:morph-counts {} :prior-counts {} :document-counts {}}}
    tokens)))

(defn- cached-lookup
  "A (fn [token] parses-grouped-by-lemma) for process-tokens: normalizes
   `token` to its comparable parses.form (see token->form) and wraps
   perseus-morph.walker.parses/get-parses in `cache`, mirroring
   MorphCodeAggregator's `cachedParses` map (keyed there by word+languageCode
   string concatenation -- a [language-code form] vector key is equivalent).
   Unlike the original per-document java.util.HashMap, `cache` here is
   supplied by the caller and shared corpus-wide (see walk!), since
   vocabulary repeats heavily across documents and a per-file cache never
   gets to pay that off."
  [db language-code ^java.util.Map cache]
  (fn [token]
    (let [form (token->form language-code token)
          cache-key [language-code form]]
      (or (.get cache cache-key)
          (let [grouped (parses/get-parses db form language-code)]
            (.put cache cache-key grouped)
            grouped)))))

(defn compute-file-counts
  "The read-only half of processing one corpus file: skips it (returning
   nil) unless its filename says it's a Greek or Latin primary text,
   otherwise tokenizes it, looks up every token's candidate parses (via
   `cache`, see cached-lookup), and accumulates morph/prior/document
   frequency counts -- everything process-file! used to do except the
   final write. Takes its own connection (`db`) so it can run concurrently
   with other readers under SQLite's WAL mode, and with write-file-counts!
   running on the single writer connection."
  [db filename cache]
  (when-let [language-code (guess-language-code filename)]
    (let [tokens (extract-tokens filename)
          doc-id (document-id filename)
          counts (process-tokens language-code doc-id (cached-lookup db language-code cache) tokens)]
      (assoc counts
             :filename (str filename) :language-code language-code
             :document-id doc-id :token-count (count tokens)))))

(defn write-file-counts!
  "The write half of processing one corpus file: flushes `result` (as
   returned by compute-file-counts) to `db` -- mirroring
   MorphCodeAggregator#endDocument and WordFrequencyLoader#endDocument's
   per-document flush, since each corpus file already corresponds to one
   whole document (no further chunking, the way the original's Chunk model
   allowed).

   The three write-*! calls run inside one transaction rather than each
   upsert committing (and, under journal_mode=WAL, syncing) on its own --
   a document's worth of counts is the natural unit of \"this should all
   land or none of it should\", and batching them is far cheaper than one
   commit per row."
  [db {:keys [morph-counts prior-counts document-counts] :as result}]
  (jdbc/with-transaction [tx db]
    (agg/write-morph-counts! tx morph-counts)
    (agg/write-prior-counts! tx prior-counts)
    (doc-freq/write-document-counts! tx document-counts))
  (dissoc result :morph-counts :prior-counts :document-counts))

(defn process-file!
  "Processes one corpus file end-to-end: compute-file-counts followed by
   write-file-counts!, both against `db`. Used directly for single-threaded
   callers (tests, and walk!'s own per-file fallback isn't needed since
   walk! pipelines the two phases itself -- see below)."
  [db filename cache]
  (when-let [result (compute-file-counts db filename cache)]
    (write-file-counts! db result)))

(defn- reader-pool
  "Opens `n` read connections from `ds` into a blocking queue: compute-file-counts
   tasks borrow a connection with `.take` and return it with `.put`, which
   bounds actual concurrent SQLite reader connections to `n` regardless of
   how many threads pmap happens to spin up."
  ^java.util.concurrent.BlockingQueue [ds n]
  (let [queue (java.util.concurrent.LinkedBlockingQueue.)]
    (dotimes [_ n] (.put queue (jdbc/get-connection ds)))
    queue))

(defn walk!
  "Walks `dir` recursively, computing and writing frequency counts for
   every .xml file. Non-Greek/Latin files (translations, __cts__.xml
   metadata, ...) are skipped via compute-file-counts's filename check; a
   file that fails to process is logged and skipped rather than aborting
   the whole walk, since a multi-corpus directory like ../corpora is large
   enough that one malformed file shouldn't lose progress on the rest.

   `ds` is a datasource (not a single connection): walk! opens its own
   writer connection plus a small pool of `pool-size` reader connections
   (default `cores - 1`, since the main thread is also writing) from it.
   compute-file-counts -- parsing, tokenizing, and parse lookups, all
   read-only -- runs across the reader pool via `pmap`, exploiting SQLite's
   WAL mode allowing many concurrent readers alongside one writer; the main
   thread drains that lazy seq in order and calls write-file-counts! on the
   single writer connection, so writes stay serialized exactly as before."
  [ds dir & {:keys [log-every pool-size]
             :or {log-every 50
                  pool-size (max 1 (dec (.availableProcessors (Runtime/getRuntime))))}}]
  (let [xml-files (->> (file-seq (io/file dir))
                       (filter #(.isFile ^File %))
                       (filter #(clojure.string/ends-with? (.getName ^File %) ".xml")))
        cache (java.util.concurrent.ConcurrentHashMap.)
        readers (reader-pool ds pool-size)
        files-processed (volatile! 0)
        tokens-processed (volatile! 0)]
    (try
      (with-open [write-db (jdbc/get-connection ds)]
        (doseq [[file outcome]
                (pmap (fn [file]
                        (let [conn (.take readers)]
                          (try
                            [file (try (compute-file-counts conn file cache)
                                       (catch Exception e e))]
                            (finally (.put readers conn)))))
                      xml-files)]
          (try
            (cond
              (instance? Exception outcome)
              (throw outcome)

              outcome
              (let [{:keys [token-count] :as result} (write-file-counts! write-db outcome)]
                (vswap! files-processed inc)
                (vswap! tokens-processed + token-count)
                (when (zero? (mod @files-processed log-every))
                  (println (format "[%5d files, %8d tokens] %s"
                                   @files-processed @tokens-processed (:filename result))))))
            (catch Exception e
              (println "WARN: failed to process" (str file) "-" (.getMessage e))))))
      (finally
        (run! #(.close ^java.sql.Connection %) readers)))
    {:files-processed @files-processed :tokens-processed @tokens-processed}))
