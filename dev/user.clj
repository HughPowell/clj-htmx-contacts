(ns user
  (:require [contacts.storage-oracle :as storage-oracle]
            [next.jdbc :as jdbc]))

(defn init-database []
  (storage-oracle/init-database))

(def stop-database storage-oracle/stop-database)

(comment
  (def data-source (jdbc/get-datasource (init-database)))
  (stop-database)

  )
