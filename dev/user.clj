(ns user
  (:require [clojure.repl]
            [clojure.repl.deps]
            [com.stuartsierra.component :as component]
            [com.stuartsierra.component.repl :as component.repl]
            [contacts.system.app :as app]
            [contacts.system.auth :as auth]
            [contacts.system.storage :as storage]
            [database-test-container]
    ;; Include this to side-step a bug in refresh
            [idle.multiset.api]
            [malli.generator]
            [potemkin]))

(potemkin/import-vars
  [clojure.repl doc]
  [clojure.repl.deps sync-deps]
  [com.stuartsierra.component.repl reset])

(component.repl/set-init
  (fn [_]
    (let [config (app/read-config)]
      (component/system-map
        :database-credentials (database-test-container/database-credentials-component)
        :storage (component/using
                   (storage/storage-component (malli.generator/generate storage/contacts-schema))
                   {:credentials :database-credentials})
        :auth (auth/auth-component config)
        :app (component/using (app/server-component)
                              {:contacts-storage :storage
                               :auth             :auth})))))

(comment
  (sync-deps)
  (reset)
  )
