(ns user
  (:require [clojure.repl]
            [clojure.repl.deps]
            [com.stuartsierra.component.repl :as component.repl]
            [contacts.system.app :as app]
            [contacts.system.contacts-storage :as contacts-storage]
            [database]
    ;; Include this to side-step a bug in refresh
            [idle.multiset.api]
            [malli.generator]
            [next.jdbc :as jdbc]
            [potemkin]
            [ragtime.next-jdbc]
            [ragtime.repl]
            [secrets]))

(potemkin/import-vars
  [clojure.repl doc]
  [clojure.repl.deps sync-deps]
  [com.stuartsierra.component.repl reset system])

(defn populate-database [system user-id]
  (run!
    (fn [contact]
      (-> system
          (get-in [:contacts-storage :contacts-storage])
          (contacts-storage/create user-id contact)))
    (malli.generator/generate contacts-storage/contacts-schema)))

(defn empty-database [system]
  (-> system
      (get-in [:data-source :data-source])
      (database/truncate-all-tables)))

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
  (reset)

  (def users (jdbc/execute! (get-in system [:data-source :data-source]) ["select user_id from users"]))
  (populate-database system (:user-id (first users)))

  (hard-reset)
  (empty-database system)
  (database/stop))
