(ns contacts.storage-oracle
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is use-fixtures]]
            [clojure.test.check.generators :as generators]
            [database-test-container]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.contact.new :as new]
            [contacts.test-lib.oracle :as oracle]
            [contacts.system.storage :as storage]
            [malli.generator :as malli.generator]))

(defn postgres-fixture [test]
  (database-test-container/init-database)
  (test)
  (database-test-container/stop-database))

(use-fixtures :once postgres-fixture)

(defn- drop-table-fixture [test]
  (test)
  (database-test-container/truncate-contacts-table))

(use-fixtures :each drop-table-fixture)

(deftest mass-contacts-storage-matches-oracle
  (checking "" [contacts (malli.generator/generator storage/contacts-schema)]
    (is (= (storage/retrieve (storage/contacts-storage (database-test-container/init-database) contacts))
           (storage/retrieve (oracle/contacts-storage contacts))))))

(defn- ids-are-unique? [contacts]
  (= (count contacts)
     (count (set (map :id contacts)))))

(defn- no-contacts-have-changed [original-contacts current-contacts]
  (= 1 (count (set/difference current-contacts original-contacts))))

(defn- persist-contact [storage contact]
  (-> storage
      (storage/create contact)
      (storage/retrieve)))

(deftest storing-new-contact-matches-oracle
  (checking "" [contacts (malli.generator/generator storage/contacts-schema)
                contact (malli.generator/generator new/schema)]
    (let [sut-results (-> (database-test-container/init-database)
                          (storage/contacts-storage contacts)
                          (persist-contact contact))
          oracle-results (persist-contact (oracle/contacts-storage contacts) contact)]
      (is (= (set (map #(dissoc % :id) sut-results))
             (set (map #(dissoc % :id) oracle-results))))
      (is (no-contacts-have-changed contacts sut-results))
      (is (ids-are-unique? sut-results)))))

(deftest retrieving-contact-matches-oracle
  (checking "" [contacts (generators/such-that
                           seq
                           (malli.generator/generator storage/contacts-schema))
                id (generators/elements (map :id contacts))]
    (let [sut-storage (storage/contacts-storage (database-test-container/init-database) contacts)
          oracle-storage (oracle/contacts-storage contacts)]
      (is (= (storage/retrieve sut-storage id)
             (storage/retrieve oracle-storage id)))
      (is (= (storage/retrieve sut-storage)
             (storage/retrieve oracle-storage))))))

(defn- update-contact [storage contact]
  (-> storage
      (storage/update contact)
      (storage/retrieve)))

(deftest persisting-update-to-contact-matches-oracle
  (checking "" [contacts (generators/such-that seq (malli.generator/generator storage/contacts-schema))
                contact (generators/elements contacts)
                id (generators/return (:id contact))
                updated-contact (generators/fmap
                                  #(assoc % :id id)
                                  (malli.generator/generator storage/existing-contact-schema))]
    (let [sut-storage (storage/contacts-storage (database-test-container/init-database) contacts)
          oracle-storage (oracle/contacts-storage contacts)]
      (is (= (update-contact sut-storage updated-contact)
             (update-contact oracle-storage updated-contact))))))

(defn- delete-contact [storage contact-id]
  (-> storage
      (storage/delete contact-id)
      (storage/retrieve)))

(deftest deleting-contact-matches-oracle
  (checking "" [contacts (generators/such-that seq (malli.generator/generator storage/contacts-schema))
                contact (generators/elements contacts)
                id (generators/return (:id contact))]
    (let [sut-results (delete-contact (storage/contacts-storage (database-test-container/init-database) contacts) id)
          oracle-results (delete-contact (oracle/contacts-storage contacts) id)]
      (is (= sut-results oracle-results)))))

(comment
  (mass-contacts-storage-matches-oracle)
  (storing-new-contact-matches-oracle)
  (retrieving-contact-matches-oracle)
  (persisting-update-to-contact-matches-oracle)
  (deleting-contact-matches-oracle)
  )
