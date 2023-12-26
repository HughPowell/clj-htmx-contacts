(ns database-test-container
  (:require [clj-test-containers.core :as test-containers]
            [clojure.test :refer :all]
            [contacts.system.data-migrations :as data-migrations]
            [honey.sql :as sql]
            [honey.sql.helpers :as sql.helpers]
            [next.jdbc :as jdbc]
            [ragtime.next-jdbc]
            [ragtime.repl])
  (:import (org.testcontainers.containers PostgreSQLContainer)))

(defn credentials [database-container]
  {:dbtype   "postgresql"
   :dbname   (.getDatabaseName (:container database-container))
   :user     (.getUsername (:container database-container))
   :password (.getPassword (:container database-container))
   :host     (.getHost (:container database-container))
   :port     (get (:mapped-ports database-container) 5432)})

(defn truncate-all-tables [data-source]
  (when data-source
    (run!
      (fn [sql] (jdbc/execute! data-source (sql/format sql)))
      [(sql.helpers/truncate :contacts)
       (sql.helpers/truncate :users)])))

(defn init-database []
  (let [database (-> (test-containers/init {:container     (PostgreSQLContainer. "postgres:15.3")
                                            :exposed-ports [5432]})
                     (test-containers/start!))]
    (ragtime.repl/migrate {:datastore  (ragtime.next-jdbc/sql-database (credentials database))
                           :migrations data-migrations/data-store-migrations})
    database))

(defn stop-database [database]
  (when database
    (test-containers/stop! database))
  nil)
