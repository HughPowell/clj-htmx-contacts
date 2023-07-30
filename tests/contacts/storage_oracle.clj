(ns contacts.storage-oracle
  (:require [clj-test-containers.core :as test-containers]
            [clojure.test :refer [is use-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [contacts.contact.new :as new]
            [contacts.lib.oracle :as oracle]
            [contacts.storage :as storage]
            [honey.sql :as sql]
            [honey.sql.helpers :refer [truncate]]
            [malli.generator :as malli.generator]
            [next.jdbc :as jdbc])
  (:import (org.postgresql.util PSQLException)
           [org.testcontainers.containers PostgreSQLContainer]))

(def database (atom nil))

(defn credentials [database-container]
  {:dbtype   "postgresql"
   :dbname   (.getDatabaseName (:container database-container))
   :user     (.getUsername (:container database-container))
   :password (.getPassword (:container database-container))
   :host     (.getHost (:container database-container))
   :port     (get (:mapped-ports database-container) 5432)})

(defn truncate-contacts-table [database-container]
  (let [truncate (-> (truncate :contacts :if-exists)
                     (sql/format))]
    (try
      (jdbc/execute! (jdbc/get-datasource (credentials database-container)) truncate)
      (catch PSQLException _))))

(defn init-database []
  (if-not @database
    (reset! database
            (-> (test-containers/init {:container     (PostgreSQLContainer. "postgres:15.3")
                                       :exposed-ports [5432]})
                (test-containers/start!)))
    (truncate-contacts-table @database))
  @database)

(defn stop-database []
  (when @database
    (test-containers/stop! @database)
    (reset! database nil)))

(defn postgres-fixture [test]
  (init-database)
  (test)
  (stop-database))

(use-fixtures :once postgres-fixture)

(defn- drop-table-fixture [test]
  (test)
  (when @database
    (truncate-contacts-table @database)))

(use-fixtures :each drop-table-fixture)

(defspec contacts-integration-matches-oracle
  (for-all [contacts (malli.generator/generator storage/contacts-schema)]
    (is (= (storage/retrieve (storage/contacts-storage (credentials (init-database)) contacts))
           (storage/retrieve (oracle/contacts-storage contacts))))))

(defn- contacts-data-is-identical? [sut oracle]
  (is (= (set (map #(dissoc % :id) sut))
         (set (map #(dissoc % :id) oracle)))))

(defn- ids-are-unique? [contacts]
  (is (= (count contacts)
         (count (set (map :id contacts))))))

(defn- persist-contact [storage contact]
  (-> storage
      (storage/create contact)
      (storage/retrieve)))

(defspec new-contact-integration-matches-oracle
  (for-all [contacts (malli.generator/generator storage/contacts-schema)
            contact (malli.generator/generator new/schema)]
    (let [sut-results (persist-contact (storage/contacts-storage (credentials (init-database)) contacts) contact)
          oracle-results (persist-contact (oracle/contacts-storage contacts) contact)]
      (is (contacts-data-is-identical? sut-results oracle-results))
      (is (ids-are-unique? sut-results))
      (is (ids-are-unique? oracle-results)))))

(defn- contact-data-is-identical [sut oracle]
  (is (= (dissoc sut :id)
         (dissoc oracle :id))))

(defspec retrieve-contact-integration-matches-oracle
  (for-all [contacts (generators/such-that
                       seq
                       (malli.generator/generator storage/contacts-schema))
            id (generators/elements (map :id contacts))]
    (let [sut-results (storage/retrieve (storage/contacts-storage (credentials (init-database)) contacts) id)
          oracle-results (storage/retrieve (oracle/contacts-storage contacts) id)]
      (is (contact-data-is-identical sut-results oracle-results)))))

(defn- update-contact [storage updated-contact]
  (-> storage
      (storage/update updated-contact)
      (storage/retrieve)))

(defn- contacts-are-identical? [sut oracle]
  (is (= (set (map #(dissoc % :id) sut))
         (set (map #(dissoc % :id) oracle)))))

(defspec persist-update-to-contact-integration-matches-oracle
  (for-all [contacts (generators/such-that seq (malli.generator/generator storage/contacts-schema))
            id (generators/fmap :id (generators/elements contacts))
            updated-contact (generators/fmap
                              #(assoc % :id id)
                              (malli.generator/generator storage/existing-contact-schema))]
    (let [sut-results (update-contact (storage/contacts-storage (credentials (init-database)) contacts) updated-contact)
          oracle-results (update-contact (oracle/contacts-storage contacts) updated-contact)]
      (contacts-are-identical? sut-results oracle-results))))

(defn- delete-contact [storage contact-id]
  (-> storage
      (storage/delete contact-id)
      (storage/retrieve)))

(defspec delete-contact-integration-matches-oracle
  (for-all [contacts (generators/such-that seq (malli.generator/generator storage/contacts-schema))
            id (generators/fmap :id (generators/elements contacts))]
    (let [sut-results (delete-contact (storage/contacts-storage (credentials (init-database)) contacts) id)
          oracle-results (delete-contact (oracle/contacts-storage contacts) id)]
      (contacts-are-identical? sut-results oracle-results))))

(comment
  (contacts-integration-matches-oracle)
  (new-contact-integration-matches-oracle)
  (retrieve-contact-integration-matches-oracle)
  (persist-update-to-contact-integration-matches-oracle)
  (delete-contact-integration-matches-oracle)
  )
