(ns database-test-container
  (:require [clj-test-containers.core :as test-containers]
            [clojure.test :refer :all]
            [honey.sql :as sql]
            [honey.sql.helpers :as sql.helpers]
            [next.jdbc :as jdbc])
  (:import (org.postgresql.util PSQLException)
           (org.testcontainers.containers PostgreSQLContainer)))

(def database (atom nil))

(defn credentials [database-container]
  {:dbtype   "postgresql"
   :dbname   (.getDatabaseName (:container database-container))
   :user     (.getUsername (:container database-container))
   :password (.getPassword (:container database-container))
   :host     (.getHost (:container database-container))
   :port     (get (:mapped-ports database-container) 5432)})

(defn truncate-contacts-table []
  (when @database
    (let [truncate (-> (sql.helpers/truncate :contacts :if-exists)
                       (sql/format))]
      (try
        (jdbc/execute! (jdbc/get-datasource (credentials @database)) truncate)
        (catch PSQLException _)))))

(defn init-database []
  (if-not @database
    (reset! database
            (-> (test-containers/init {:container     (PostgreSQLContainer. "postgres:15.3")
                                       :exposed-ports [5432]})
                (test-containers/start!)))
    (truncate-contacts-table))
  (credentials @database))

(defn stop-database []
  (when @database
    (test-containers/stop! @database)
    (reset! database nil)))
