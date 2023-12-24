(ns user
  (:require [aero.core :as aero]
            [clojure.java.shell :as shell]
            [clojure.repl]
            [clojure.repl.deps]
            [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [com.stuartsierra.component.repl :as component.repl]
            [contacts.system.app :as app]
            [contacts.system.auth :as auth]
            [contacts.system.contacts-storage :as contacts-storage]
            [contacts.system.data-source :as data-source]
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

(defn load-secrets* []
  (sh "vlt" "login")
  (->> (sh "vlt" "secrets" "list")
       (string/split-lines)
       (rest)
       (pmap (fn [line]
               (let [secret-name (re-find #"^\w+" line)]
                 [secret-name
                  (sh "vlt" "secrets" "get" "-plaintext" secret-name)])))
       (into {})))

(defn load-secrets []
  (try
    (load-secrets*)
    (catch Exception _
      (sh "vlt" "logout")
      (load-secrets*)))
  )

(defonce secrets (load-secrets))

(defmethod aero/reader 'vault
  [_opts _tag value]
  (get secrets (str value)))

(component.repl/set-init
  (fn [_]
    (let [config (app/read-config)]
      (component/system-map
        :database-container (database-test-container/database-container-component)
        :database-credentials (component/using
                                (database-test-container/database-credentials-component)
                                {:database :database-container})
        :data-source (component/using
                       (data-source/data-source-component)
                       {:credentials :database-credentials})
        :contacts-storage (component/using
                            (contacts-storage/contacts-storage-component (malli.generator/generate contacts-storage/contacts-schema))
                            [:data-source])
        :auth (auth/auth-component config)
        :app (component/using
               (app/server-component)
               [:contacts-storage :auth])))))

(comment
  (sync-deps)
  (reset)

  (alter-var-root #'secrets (constantly (load-secrets)))
  )
