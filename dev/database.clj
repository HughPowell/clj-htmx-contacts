(ns database
  "This is here so we can reset the 'user' namespace without having to spin up a new database."
  (:require [database-test-container]))

(def database (database-test-container/init-database))

(def credentials (database-test-container/credentials database))
(defn stop []
  (alter-var-root #'database database-test-container/stop-database))

(defn reset []
  (stop)
  (alter-var-root #'database (fn [_] (database-test-container/init-database))))

(def truncate-all-tables database-test-container/truncate-all-tables)
