(ns perseus-morph.main
  "Consolidated CLI entry point dispatching to subcommands. Replaces the
   separate -main namespaces in perseus-morph.loader.load,
   perseus-morph.frequencies.aggregate, and perseus-morph.lexica.ingest.

   Usage:
     clj -M -m perseus-morph.main load      [options] <morph jsonl file>
     clj -M -m perseus-morph.main aggregate  [options] <corpus dir>
     clj -M -m perseus-morph.main ingest     [options] <lexicon id> <lexicon xml file>
     clj -M -m perseus-morph.main help       [subcommand]"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [next.jdbc :as jdbc]
            [perseus-morph.lexica.core :as lexica]
            [perseus-morph.loader.core :as loader]
            [perseus-morph.migrations :as migrations]
            [perseus-morph.sqlite :as sqlite]
            [perseus-morph.walker.core :as walker])
  (:gen-class))

;; ---------------------------------------------------------------------------
;; Subcommand option definitions
;; ---------------------------------------------------------------------------

(def ^:private load-options
  [["-d" "--db PATH" "Path to the SQLite database file"
    :default "morph.db"]
   ["-l" "--language CODE" "Language code (guessed from the filename if omitted)"]
   ["-n" "--no-delete" "Don't delete existing parses for this language first"]
   ["-h" "--help" "Print this message"]])

(def ^:private aggregate-options
  [["-d" "--db PATH" "Path to the SQLite database file"
    :default "morph.db"]
   ["-h" "--help" "Print this message"]])

(def ^:private ingest-options
  [["-d" "--db PATH" "Path to the SQLite database file"
    :default "morph.db"]
   ["-n" "--no-delete" "Don't delete existing senses for this lexicon first"]
   ["-h" "--help" "Print this message"]])

;; ---------------------------------------------------------------------------
;; Help text
;; ---------------------------------------------------------------------------

(def ^:private help-text
  "Usage: clj -M -m perseus-morph.main <subcommand> [options] [args]

Subcommands:
  load       Load a morph JSONL file into the database.
  aggregate  Walk a corpus directory and aggregate frequency counts.
  ingest     Load a TEI lexicon XML file's senses into the database.
  help       Print this message or help for a specific subcommand.

Run `clj -M -m perseus-morph.main help <subcommand>` for subcommand details.")

(def ^:private subcommand-summaries
  {"load"      "clj -M -m perseus-morph.main load [options] <morph jsonl file>"
   "aggregate" "clj -M -m perseus-morph.main aggregate [options] <corpus dir>"
   "ingest"    "clj -M -m perseus-morph.main ingest [options] <lexicon id> <lexicon xml file>"})

;; ---------------------------------------------------------------------------
;; Per-subcommand dispatch
;; ---------------------------------------------------------------------------

(defn- xml-files
  [path]
  (let [file (io/file path)]
    (if (.isDirectory file)
      (->> (.listFiles file)
           (filter #(str/ends-with? (str/lower-case (.getName %)) ".xml"))
           (sort-by #(.getName %)))
      [file])))

(defn- run-load
  [args]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts args load-options)]
    (cond
      (:help options)
      (println (get subcommand-summaries "load") "\n\nOptions:\n" summary)

      errors
      (do (println "Error(s):\n" (str/join "\n" errors))
          (System/exit 1))

      (not= (count arguments) 1)
      (do (println (get subcommand-summaries "load"))
          (println "Options:\n" summary)
          (System/exit 1))

      :else
      (let [filename (first arguments)
            language-code (or (:language options) (loader/guess-language-code filename))
            db (sqlite/datasource (:db options))]
        (migrations/migrate! db)
        (when-not (:no-delete options)
          (println "Deleting existing parses for" language-code)
          (loader/delete-by-language! db language-code))
        (println "Loading" filename "as language" language-code "into" (:db options))
        (let [result (loader/load! db filename language-code)]
          (println "Done:" result))))))

(defn- run-aggregate
  [args]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts args aggregate-options)]
    (cond
      (:help options)
      (println (get subcommand-summaries "aggregate") "\n\nOptions:\n" summary)

      errors
      (do (println "Error(s):\n" (str/join "\n" errors))
          (System/exit 1))

      (not= (count arguments) 1)
      (do (println (get subcommand-summaries "aggregate"))
          (println "Options:\n" summary)
          (System/exit 1))

      :else
      (let [dir (first arguments)
            ds (sqlite/datasource (:db options))]
        (with-open [db (jdbc/get-connection ds)]
          (jdbc/execute! db ["PRAGMA journal_mode=WAL"])
          (migrations/migrate! db))
        (println "Walking" dir "into" (:db options))
        (let [result (walker/walk! ds dir)]
          (println "Done:" result))))))

(defn- run-ingest
  [args]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts args ingest-options)]
    (cond
      (:help options)
      (println (get subcommand-summaries "ingest") "\n\nOptions:\n" summary)

      errors
      (do (println "Error(s):\n" (str/join "\n" errors))
          (System/exit 1))

      (not= (count arguments) 2)
      (do (println (get subcommand-summaries "ingest"))
          (println "Options:\n" summary)
          (System/exit 1))

      :else
      (let [[lexicon-id filename] arguments
            db (sqlite/datasource (:db options))]
        (migrations/migrate! db)
        (when-not (:no-delete options)
          (println "Deleting existing senses for" lexicon-id)
          (lexica/clear-existing! db lexicon-id))
        (let [files (xml-files filename)
              total (reduce (fn [acc file]
                              (println "Loading" (str file) "as lexicon" lexicon-id "into" (:db options))
                              (let [{:keys [senses]} (lexica/load! db lexicon-id file)]
                                (+ acc senses)))
                            0
                            files)]
          (println "Done:" {:senses total}))))))

;; ---------------------------------------------------------------------------
;; Entry point
;; ---------------------------------------------------------------------------

(defn- print-help
  ([] (println help-text))
  ([subcommand]
   (case subcommand
     "load" (run-load ["--help"])
     "aggregate" (run-aggregate ["--help"])
     "ingest" (run-ingest ["--help"])
     (println (str "Unknown subcommand: " subcommand "\n") help-text))))

(defn -main
  [& args]
  (if (empty? args)
    (do (println help-text) (System/exit 1))
    (let [[subcommand & sub-args] args]
      (case subcommand
        "load"      (run-load sub-args)
        "aggregate" (run-aggregate sub-args)
        "ingest"    (run-ingest sub-args)
        "help"      (print-help (first sub-args))
        (do (println (str "Unknown subcommand: " subcommand "\n"))
            (println help-text)
            (System/exit 1))))))
