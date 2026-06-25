(ns perseus-morph.lexica.xml-parser
  "Parser for TEI dictionary XML (entry/entryFree elements containing sense
   children), ported from perseus.voting.SenseLoader$SenseLoaderHandler.
   Unlike that SAX handler (and this namespace's own earlier SAX-based
   version), this parses the whole file into a tree via clojure.xml/parse
   and walks it with a zipper -- these lexicon files aren't large enough to
   need streaming, and a tree lets sense extraction handle *nested* <sense>
   elements correctly (a sense's own text, separate from any sub-senses'),
   where a single-pass SAX handler with no element stack could only
   conflate them. (clojure.xml/parse drops whitespace-only text nodes
   between sibling elements, so reconstructed entry text won't always have
   the exact whitespace the source did -- acceptable here.)

   `parse-lexicon!` invokes `on-sense` once per <sense>, anywhere in an
   entry's subtree, with {:key :id :n :level :short-def}, where :key is the
   enclosing entry's `key` attribute and :short-def is that sense's *own*
   text (tr/gloss/hi children wrapped in `meaning-tag`; nested sub-senses'
   text excluded, since those get their own on-sense calls); non-meaning
   elements (cit, quote, bibl, foreign, etc.) are serialized with their
   full TEI tag structure instead of being stripped. Returns
   \"[no specified meaning]\" if the sense has no text of its own. It also
   invokes `on-entry` once per entry, anywhere in the document, with
   {:key :text}, where :text is a serialization of the entry's full
   subtree (every descendant tag, attribute, and text node) -- captured
   independently of on-sense, so the whole entry is available for display
   without re-reading the source XML at request time."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.xml :as xml]
            [clojure.zip :as zip])
  (:import (javax.xml.parsers SAXParserFactory)
           (org.xml.sax InputSource)))

(defn- tag-name [node]
  (str/lower-case (name (:tag node))))

(defn- entry? [node]
  (and (map? node) (contains? #{"entry" "entryfree"} (tag-name node))))

(defn- sense? [node]
  (and (map? node) (= (tag-name node) "sense")))

(defn- meaning-element? [node]
  (and (map? node) (contains? #{"tr" "gloss" "hi"} (tag-name node))))

(declare serialize)

(defn- descendant-elements
  "Every element (map) node in `node`'s subtree, in document order, not
   including `node` itself."
  [node]
  (mapcat (fn [child]
            (when (map? child)
              (cons child (descendant-elements child))))
          (:content node)))

(defn- own-text
  "`node`'s own text: every character and tr/gloss/hi-wrapped descendant,
   except inside a nested <sense> (that subtree gets its own on-sense call,
   via the top-level walk in parse-lexicon!, so its text shouldn't also be
   folded into this node's). Non-meaning elements (cit, quote, bibl, etc.)
   are serialized with their full TEI tag structure via `serialize`, so
   they are preserved in the definition rather than stripped."
  [node meaning-tag]
  (letfn [(walk [n]
            (cond
              (string? n) n
              (sense? n) ""
              (meaning-element? n) (str "<" meaning-tag ">"
                                         (apply str (map walk (:content n)))
                                         "</" meaning-tag ">")
              (map? n) (serialize n)
              :else ""))]
    (apply str (map walk (:content node)))))

(defn- escape-text [^String s]
  (-> s
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- escape-attr [^String s]
  (-> (escape-text s)
      (str/replace "\"" "&quot;")))

(defn- serialize
  "A node's subtree, reconstructed as XML text (tags, attributes in
   alphabetical order for determinism, and escaped character data) -- not
   necessarily byte-identical to the source (attribute order and
   inter-element whitespace may not match), but a faithful, self-contained
   rendering of its content."
  [node]
  (cond
    (string? node) (escape-text node)
    (map? node) (let [tag (name (:tag node))
                      attrs (apply str (for [[k v] (sort-by key (:attrs node))]
                                          (str " " (name k) "=\"" (escape-attr v) "\"")))]
                  (str "<" tag attrs ">"
                       (apply str (map serialize (:content node)))
                       "</" tag ">"))
    :else ""))

(defn- entry-text [entry-node]
  (apply str (map serialize (:content entry-node))))

(defn- entry-nodes
  "Every entry/entryFree element anywhere in the parsed document, in
   document order, found by walking a zipper rather than assuming entries
   are direct children of the root (TEI dictionaries commonly wrap them in
   <body>/<div1>/etc)."
  [root]
  (loop [loc (zip/xml-zip root)
         found []]
    (if (zip/end? loc)
      found
      (let [node (zip/node loc)]
        (recur (zip/next loc)
               (if (entry? node) (conj found node) found))))))

(defn- sense-row [entry-key meaning-tag sense-node]
  (let [{:keys [id n level]} (:attrs sense-node)
        text (own-text sense-node meaning-tag)]
    {:key entry-key
     :id id
     :n n
     :level level
     :short-def (if (str/blank? text) "[no specified meaning]" text)}))

(defn- startparse-no-dtd
  "A clojure.xml/parse `startparse` function that skips resolving external
   DTD entities (e.g. Perseus's PersDict.dtd) -- these files' internal DTD
   subsets pull them in purely to declare additional markup, none of which
   this parser needs, so there's no reason parsing should depend on network
   access to perseus.tufts.edu/tei-c.org."
  [^InputSource source content-handler]
  (let [reader (.getXMLReader (.newSAXParser (SAXParserFactory/newInstance)))]
    (.setFeature reader "http://apache.org/xml/features/nonvalidating/load-external-dtd" false)
    (.setContentHandler reader content-handler)
    (.parse reader source)))

(defn parse-lexicon!
  "Parses `filename` (a path or java.io.File), invoking `on-sense` with
   every sense's {:key :id :n :level :short-def} and `on-entry` with every
   entry's {:key :text}, both in document order."
  [filename meaning-tag on-sense on-entry]
  (with-open [stream (io/input-stream filename)]
    (let [root (xml/parse (InputSource. stream) startparse-no-dtd)]
      (doseq [entry (entry-nodes root)]
        (let [key (:key (:attrs entry))]
          (doseq [sense (filter sense? (descendant-elements entry))]
            (on-sense (sense-row key meaning-tag sense)))
          (on-entry {:key key :text (entry-text entry)}))))))
