(ns contacts.storage-oracle
  (:require [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [contacts.contact.new :as new]
            [contacts.lib.oracle :as oracle]
            [contacts.storage :as storage]
            [malli.generator :as malli.generator]))

(defspec contacts-integration-matches-oracle
  (for-all [contacts (malli.generator/generator storage/contacts-schema)]
    (is (= (storage/retrieve (storage/contacts-storage contacts))
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
    (let [sut-results (persist-contact (storage/contacts-storage contacts) contact)
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
    (let [sut-results (storage/retrieve (storage/contacts-storage contacts) id)
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
    (let [sut-results (update-contact (storage/contacts-storage contacts) updated-contact)
          oracle-results (update-contact (oracle/contacts-storage contacts) updated-contact)]
      (contacts-are-identical? sut-results oracle-results))))

(defn- delete-contact [storage contact-id]
  (-> storage
      (storage/delete contact-id)
      (storage/retrieve)))

(defspec delete-contact-integration-matches-oracle
  (for-all [contacts (generators/such-that seq (malli.generator/generator storage/contacts-schema))
            id (generators/fmap :id (generators/elements contacts))]
    (let [sut-results (delete-contact (storage/contacts-storage contacts) id)
          oracle-results (delete-contact (oracle/contacts-storage contacts) id)]
      (contacts-are-identical? sut-results oracle-results))))

(comment
  (contacts-integration-matches-oracle)
  (new-contact-integration-matches-oracle)
  (retrieve-contact-integration-matches-oracle)
  (persist-update-to-contact-integration-matches-oracle)
  (delete-contact-integration-matches-oracle)
  )
