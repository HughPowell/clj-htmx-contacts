(ns contacts.test-lib.database
  (:require
    [database-test-container]
    [next.jdbc :as jdbc]))

(def ^:private database (atom nil))

(defn postgres-fixture [test]
  (reset! database (database-test-container/init-database))
  (test)
  (swap! database database-test-container/stop-database))

(defn- get-connection []
  (jdbc/get-connection (database-test-container/credentials @database)))

(defn reset []
  (if @database
    (database-test-container/truncate-all-tables (get-connection))
    (reset! database (database-test-container/init-database)))
  (get-connection))
