(ns user
  (:require [aero.core :as aero]
            [clojure.java.shell :as shell]
            [clojure.repl]
            [clojure.repl.deps]
            [clojure.string :as string]
            [com.stuartsierra.component.repl :as component.repl]
            [contacts.system.app :as app]
            [contacts.system.contacts-storage :as contacts-storage]
            [database-test-container]
    ;; Include this to side-step a bug in refresh
            [idle.multiset.api]
            [malli.generator]
            [potemkin]))

(potemkin/import-vars
  [clojure.repl doc]
  [clojure.repl.deps sync-deps]
  [com.stuartsierra.component.repl reset])

(defn sh [& args]
  (let [{:keys [exit out err] :as response} (apply shell/sh args)]
    (if (zero? exit)
      (string/trimr out)
      (throw (ex-info (if (seq err) err out) response)))))

(defn load-secrets []
  (sh "vlt" "login")
  (->> (sh "vlt" "secrets" "list")
       (string/split-lines)
       (rest)
       (pmap (fn [line]
               (let [secret-name (re-find #"^\w+" line)]
                 [secret-name
                  (sh "vlt" "secrets" "get" "-plaintext" secret-name)])))
       (into {})))

(defn secrets []
  (try
    (load-secrets)
    (catch Exception _
      (sh "vlt" "logout")
      (load-secrets))))

(defn vault-reader [secrets]
  (defmethod aero/reader 'vault
    [_opts _tag value]
    (get secrets (str value))))

(defn stop-database [database]
  (database-test-container/stop-database database))

(defn populate-database [system]
  (run!
    (fn [contact]
      (contacts-storage/create
        (:contacts-storage (:contacts-storage system))
        contact))
    (malli.generator/generate contacts-storage/contacts-schema)))

(defn empty-database [database]
  (database-test-container/truncate-contacts-table database))

(defn init-system [database]
  (component.repl/set-init
    (fn [_]
      (-> (app/read-config)
          (assoc :database (database-test-container/credentials database))
          (app/system-map)))))

(comment
  (sync-deps)
  (reset)

  (do
    (def database (database-test-container/init-database))
    (def secrets (secrets))
    (vault-reader secrets)
    (init-system database))

  (populate-database component.repl/system)
  (empty-database database)

  (stop-database)
)
