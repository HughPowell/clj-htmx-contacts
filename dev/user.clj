(ns user
  (:require [clojure.repl]
            [clojure.repl.deps]
            [com.stuartsierra.component.repl :as component.repl]
            [contacts.system.app :as app]
            [contacts.system.contacts-storage :as contacts-storage]
            [contacts.system.data-migrations :as data-migrations]
            [database]
    ;; Include this to side-step a bug in refresh
            [idle.multiset.api]
            [malli.generator]
            [potemkin]
            [ragtime.repl]
            [ragtime.next-jdbc]
            [secrets]))

(potemkin/import-vars
  [clojure.repl doc]
  [clojure.repl.deps sync-deps]
  [com.stuartsierra.component.repl reset system])

(defn populate-database [system]
  (ragtime.repl/migrate {:datastore  (-> system
                                         (get-in [:data-source :data-source])
                                         (ragtime.next-jdbc/sql-database))
                         :migrations data-migrations/data-store-migrations})
  (run!
    (fn [contact]
      (-> system
          (get-in [:contacts-storage :contacts-storage])
          (contacts-storage/create contact)))
    (malli.generator/generate contacts-storage/contacts-schema)))

(defn empty-database [system]
  (-> system
      (get-in [:data-source :data-source])
      (database/truncate-contacts-table)))

(component.repl/set-init
  (fn [_]
    (-> (app/read-config)
        (assoc :database database/credentials)
        (app/system-map))))

(defn hard-reset []
  (database/reset)
  (secrets/reset)
  (reset))

(comment
  ;; Make sure to run populate database before accessing the system for the first time
  (reset)
  (populate-database system)

  (empty-database system)
  )
