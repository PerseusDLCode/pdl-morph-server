(ns perseus-morph.migrations
  "Applies the SQL migrations under resources/migrations (loaded via
   Ragtime) to a SQLite database. The schema lives entirely in those
   .sql files now."
  (:require [ragtime.core :as ragtime]
            [ragtime.next-jdbc :as ragtime-jdbc]))

(defn migrate!
  "Applies every migration under resources/migrations not yet recorded
   against `datasource` (Ragtime tracks applied ids in its own
   `ragtime_migrations` table), in filename order. Idempotent and safe to
   call on every process startup, including against a brand-new database."
  [datasource]
  (let [migrations (ragtime-jdbc/load-resources "migrations")
        store (ragtime-jdbc/sql-database datasource)]
    (ragtime/migrate-all store (ragtime/into-index migrations) migrations)))
