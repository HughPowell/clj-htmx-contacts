(ns user
  (:require [clojure.repl]
            [clojure.repl.deps]
            [com.stuartsierra.component.repl :as component.repl]
            [contacts.system.app :as app]
            [contacts.system.contacts-storage :as contacts-storage]
            [contacts.system.users-storage :as users-storage]
            [database]
    ;; Include this to side-step a bug in refresh
            [idle.multiset.api]
            [malli.generator]
            [potemkin]
            [ragtime.next-jdbc]
            [ragtime.repl]
            [secrets]))

(potemkin/import-vars
  [clojure.repl doc]
  [clojure.repl.deps sync-deps]
  [com.stuartsierra.component.repl reset system])

(defn populate-database [system authorisation-id]
  (run!
    (fn [contact]
      (let [{:keys [user-id]} (users-storage/->user (get-in system [:users-storage :users-storage]) authorisation-id)]
        (-> system
            (get-in [:contacts-storage :contacts-storage])
            (contacts-storage/create user-id contact))))
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
  (populate-database system "")

  (hard-reset)
  (empty-database system)
  (database/stop))
