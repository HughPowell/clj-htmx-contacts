(ns contacts.storage-client
  (:require [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [contacts.contacts :as contacts]
            [contacts.contacts.new :as new]
            [contacts.lib.oracle :as oracle]
            [malli.generator :as malli.generator]))

(defn oracle-persist-contacts* [_contacts-storage contacts]
  (set contacts))

(defn oracle-retrieve* [contacts-storage]
  contacts-storage)

(oracle/register {'contacts/persist*  oracle-persist-contacts*
                  'contacts/retrieve* oracle-retrieve*})

(defspec contacts-integration-matches-oracle
  (for-all [contacts (malli.generator/generator contacts/schema)]
    (let [retrieve-contacts (fn [storage contacts] (-> storage
                                                       (contacts/persist* contacts)
                                                       (contacts/retrieve*)))]
      (is (= (retrieve-contacts (atom #{}) contacts)
             (oracle/fixture (retrieve-contacts #{} contacts)))))))

(def id (atom 0))

(defn oracle-persist-contact* [contacts-storage contact]
  (let [ids (set (map :id contacts-storage))]
    (loop [proposed-id (str (swap! id inc))]
      (if (contains? ids proposed-id)
        (recur (str (swap! id inc)))
        (conj contacts-storage (assoc contact :id proposed-id))))))

(oracle/register {'new/persist* oracle-persist-contact*})

(defn- contact-data-is-identical? [sut oracle]
  (is (= (set (map #(dissoc % :id) sut))
         (set (map #(dissoc % :id) oracle)))))

(defn- ids-are-unique? [contacts]
  (is (= (count contacts)
         (count (set (map :id contacts))))))

(defspec new-contact-integration-matches-oracle
  (for-all [contacts (malli.generator/generator contacts/schema)
            contact (malli.generator/generator new/schema)]
    (let [persist-contact (fn [storage contacts contact] (-> storage
                                                             (contacts/persist* contacts)
                                                             (new/persist* contact)
                                                             (contacts/retrieve*)))
          sut-results (persist-contact (atom #{}) contacts contact)
          oracle-results (oracle/fixture (persist-contact #{} contacts contact))]
      (and (is (contact-data-is-identical? sut-results oracle-results))
           (is (ids-are-unique? sut-results))
           (is (ids-are-unique? oracle-results))))))

(comment
  (contacts-integration-matches-oracle)
  (new-contact-integration-matches-oracle))
