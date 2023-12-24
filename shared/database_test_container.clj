(ns database-test-container
  (:require [clj-test-containers.core :as test-containers]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [honey.sql :as sql]
            [honey.sql.helpers :as sql.helpers]
            [next.jdbc :as jdbc])
  (:import (org.postgresql.util PSQLException)
           (org.testcontainers.containers PostgreSQLContainer)))

(defn credentials [database-container]
  {:dbtype   "postgresql"
   :dbname   (.getDatabaseName (:container database-container))
   :user     (.getUsername (:container database-container))
   :password (.getPassword (:container database-container))
   :host     (.getHost (:container database-container))
   :port     (get (:mapped-ports database-container) 5432)})

(defn truncate-contacts-table [database]
  (when database
    (let [truncate (-> (sql.helpers/truncate :contacts :if-exists)
                       (sql/format))]
      (try
        (jdbc/execute! (jdbc/get-datasource (credentials database)) truncate)
        (catch PSQLException _)))))

(defn init-database []
  (-> (test-containers/init {:container     (PostgreSQLContainer. "postgres:15.3")
                             :exposed-ports [5432]})
      (test-containers/start!)))

(defn stop-database [database]
  (when database
    (test-containers/stop! database))
  nil)

(defrecord DatabaseContainerComponent []
  component/Lifecycle
  (start [component]
    (let [container (init-database)]
      (assoc component :database container)))
  (stop [component]
    (update component :database stop-database)))

(defn database-container-component []
  (map->DatabaseContainerComponent {}))

(defrecord DatabaseCredentialsComponent [database]
  component/Lifecycle
  (start [component]
    (assoc component :credentials (credentials (:database database))))
  (stop [component]
    (assoc component :credentials nil)))

(defn database-credentials-component []
  (map->DatabaseCredentialsComponent {}))
