(ns perseus-morph.language
  "Per-language form normalization, ported from the relevant bits of
   perseus.language.LanguageAdapter / GreekAdapter / LatinAdapter that
   perseus.morph.ParseLoader relied on."
  (:require [clojure.string]))

(def bare-word-pattern
  "Mirrors Lemma.BARE_WORD_PATTERN: strips beta-code diacritic markers to
   produce a bare (accent-free) form."
  #"[()\\/*=|+']")

(defn bare-form [s]
  (when s
    (clojure.string/replace s bare-word-pattern "")))

(defn- uncapitalize
  "Lowercases the first letter of `s`, skipping any leading non-letter
   characters (e.g. the elision apostrophe greek.morph.unicode.xml's forms
   sometimes lead with, like \"'Ελλάδος\") so a capital isn't missed just
   because it isn't literally the first character."
  [^String s]
  (if-let [i (first (filter #(Character/isLetter (.charAt s %)) (range (count s))))]
    (if (Character/isUpperCase (.charAt s i))
      (str (subs s 0 i) (Character/toLowerCase (.charAt s i)) (subs s (inc i)))
      s)
    s))

(defn- greek-lowercase [s]
  (-> s uncapitalize (clojure.string/replace "*" "")))

(defmulti to-lowercase
  "Lowercases `s` according to the conventions of `language-code` (an ISO
   639 code, e.g. \"grc\"/\"lat\"/\"ara\"), mirroring each LanguageAdapter's
   toLowerCase()."
  (fn [language-code _s] language-code))

(defmethod to-lowercase "grc" [_ s] (greek-lowercase s))
(defmethod to-lowercase :default [_ s] (clojure.string/lower-case s))

(defn match-case?
  "True if `language-code` is case-sensitive when matching forms. Mirrors
   LanguageAdapter#matchCase(); only Arabic overrides the default (false)."
  [language-code]
  (= language-code "ara"))

(defn normalize-form
  "Equivalent of:
     language.getAdapter().matchCase() ? form : language.getAdapter().toLowerCase(form)"
  [language-code form]
  (if (match-case? language-code)
    form
    (to-lowercase language-code form)))

(defn normalize-unicode
  "Lowercases `s` and strips combining diacritics via NFD decomposition,
   for the API's lookup index (lemmas.headword_normalized in
   perseus-morph.loader.schema). Expects already-composed Unicode text, not Beta Code."
  [s]
  (when s
    (-> s
        (java.text.Normalizer/normalize java.text.Normalizer$Form/NFD)
        (clojure.string/replace #"\p{M}" "")
        clojure.string/lower-case)))

(def language-name->code
  "Maps the English language names used by morph XML filenames
   (greek.morph.xml, latin.morph.xml, arabic.morph.xml) to the ISO 639
   codes used everywhere internally."
  {"greek" "grc" "latin" "lat" "arabic" "ara"})

(defn canonicalize-code
  "Maps `code` to its ISO 639 form via language-name->code, or returns it
   unchanged if it isn't a known English name (i.e. it's already an ISO
   code)."
  [code]
  (get language-name->code code code))

(def ^:private lemma-pattern #"^(\D+)(\d+)$")
(def ^:private hyphen-pattern #"^.+-(.+)$")

(defn parse-lemma-text
  "Splits raw <lemma> text into [headword sequence-number], mirroring
   ParseLoader.ParseHandler#createParses(). Strips '#' markers and reduces
   hyphenated forms (e.g. 'foo-bar1') to their final segment before checking
   for a trailing sequence number."
  [lemma-text]
  (let [cleaned (clojure.string/replace lemma-text "#" "")
        cleaned (if-let [[_ tail] (re-matches hyphen-pattern cleaned)]
                  tail
                  cleaned)]
    (if-let [[_ headword seq-str] (re-matches lemma-pattern cleaned)]
      [headword (Integer/parseInt seq-str)]
      [cleaned -1])))
