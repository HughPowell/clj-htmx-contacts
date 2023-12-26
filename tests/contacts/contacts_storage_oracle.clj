(ns contacts.contacts-storage-oracle
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is use-fixtures]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.contact.new :as new]
            [contacts.test-lib.database :as database]
            [contacts.test-lib.oracle :as oracle]
            [contacts.system.contacts-storage :as contacts-storage]
            [malli.generator :as malli.generator]))

(use-fixtures :once database/postgres-fixture)

(defn- populate-contacts-storage [connection contacts]
  (let [contacts-storage (contacts-storage/contacts-storage connection)]
    (run!
      (fn [contact] (contacts-storage/create contacts-storage contact))
      contacts)
    contacts-storage))

(deftest mass-contacts-storage-matches-oracle
  (checking "" [contacts (malli.generator/generator contacts-storage/contacts-schema)]
    (with-open [connection (database/reset)]
      (is (= (contacts-storage/retrieve (populate-contacts-storage connection contacts))
             (contacts-storage/retrieve (oracle/contacts-storage contacts)))))))

(defn- ids-are-unique? [contacts]
  (= (count contacts)
     (count (set (map :id contacts)))))

(defn- no-contacts-have-changed [original-contacts current-contacts]
  (= 1 (count (set/difference current-contacts original-contacts))))

(defn- persist-contact [storage contact]
  (-> storage
      (contacts-storage/create contact)
      (contacts-storage/retrieve)))

(deftest storing-new-contact-matches-oracle
  (checking "" [contacts (malli.generator/generator contacts-storage/contacts-schema)
                contact (malli.generator/generator new/schema)]
    (with-open [connection (database/reset)]
      (let [sut-results (-> connection
                            (populate-contacts-storage contacts)
                            (persist-contact contact))
            oracle-results (persist-contact (oracle/contacts-storage contacts) contact)]
        (is (= (set (map #(dissoc % :id) sut-results))
               (set (map #(dissoc % :id) oracle-results))))
        (is (no-contacts-have-changed contacts sut-results))
        (is (ids-are-unique? sut-results))))))

(deftest retrieving-contact-matches-oracle
  (checking "" [contacts (generators/such-that
                           seq
                           (malli.generator/generator contacts-storage/contacts-schema))
                id (generators/elements (map :id contacts))]
    (with-open [connection (database/reset)]
      (let [sut-storage (populate-contacts-storage connection contacts)
            oracle-storage (oracle/contacts-storage contacts)]
        (is (= (contacts-storage/retrieve sut-storage id)
               (contacts-storage/retrieve oracle-storage id)))
        (is (= (contacts-storage/retrieve sut-storage)
               (contacts-storage/retrieve oracle-storage)))))))

(defn- update-contact [storage contact]
  (-> storage
      (contacts-storage/update contact)
      (contacts-storage/retrieve)))

(deftest persisting-update-to-contact-matches-oracle
  (checking "" [contacts (generators/such-that seq (malli.generator/generator contacts-storage/contacts-schema))
                contact (generators/elements contacts)
                id (generators/return (:id contact))
                updated-contact (generators/fmap
                                  #(assoc % :id id)
                                  (malli.generator/generator contacts-storage/existing-contact-schema))]
    (with-open [connection (database/reset)]
      (let [sut-storage (populate-contacts-storage connection contacts)
            oracle-storage (oracle/contacts-storage contacts)]
        (is (= (update-contact sut-storage updated-contact)
               (update-contact oracle-storage updated-contact)))))))

(defn- delete-contact [storage contact-id]
  (-> storage
      (contacts-storage/delete contact-id)
      (contacts-storage/retrieve)))

(deftest deleting-contact-matches-oracle
  (checking "" [contacts (generators/such-that seq (malli.generator/generator contacts-storage/contacts-schema))
                contact (generators/elements contacts)
                id (generators/return (:id contact))]
    (with-open [connection (database/reset)]
      (let [sut-results (delete-contact (populate-contacts-storage connection contacts) id)
            oracle-results (delete-contact (oracle/contacts-storage contacts) id)]
        (is (= sut-results oracle-results))))))

(comment
  (mass-contacts-storage-matches-oracle)
  (storing-new-contact-matches-oracle)
  (retrieving-contact-matches-oracle)
  (persisting-update-to-contact-matches-oracle)
  (deleting-contact-matches-oracle)
  )
