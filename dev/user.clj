(ns user
  (:require [clojure.repl]
            [clojure.repl.deps]
            [contacts.storage-oracle :as storage-oracle]
            [next.jdbc :as jdbc]
            [potemkin]))

(potemkin/import-vars
  [clojure.repl doc]
  [clojure.repl.deps sync-deps])

(defn init-database []
  (storage-oracle/init-database))

(def stop-database storage-oracle/stop-database)

(comment
  (def data-source (jdbc/get-datasource (init-database)))
  (stop-database)

  )
