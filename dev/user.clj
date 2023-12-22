(ns user
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.repl]
            [clojure.repl.deps]
            [contacts.system.app :as app]
            [contacts.system.auth :as auth]
            [contacts.system.storage :as storage]
            [database-test-container]
            [malli.generator]
            [potemkin])
  (:import (org.eclipse.jetty.server Server)))

(potemkin/import-vars
  [clojure.repl doc]
  [clojure.repl.deps sync-deps])

(defn read-config []
  (-> "config.edn"
      (io/resource)
      (aero/read-config)))

(defn init-database []
  (database-test-container/init-database))

(defn populate-database [database]
  (storage/contacts-storage database (malli.generator/generate storage/contacts-schema)))

(def stop-database database-test-container/stop-database)

(defn start-server [contacts-storage auth]
  (app/start-server contacts-storage auth))

(defn stop-server [system]
  (.stop ^Server system))

(def system (atom nil))

(defn start-app []
  (let [database (populate-database (init-database))
        auth (-> (read-config)
                 (:auth)
                 (auth/auth0-authorization))]
    (reset! system {:database database
                    :server   (start-server database auth)})))

(defn stop-app []
  (stop-database (:database @system))
  (stop-server (:server @system))
  (reset! system nil))

(defn reset-app []
  (when @system
    (stop-app))
  (start-app))


(comment

  )
