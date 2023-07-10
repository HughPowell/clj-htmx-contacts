(ns contacts.storage-oracle
  (:require [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [contacts.contact :as contact]
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

(defn- retrieve-contacts [storage contacts]
  (-> storage
      (contacts/persist* contacts)
      (contacts/retrieve*)))

(defspec contacts-integration-matches-oracle
  (for-all [contacts (malli.generator/generator contacts/schema)]
    (is (= (retrieve-contacts (atom #{}) contacts)
           (oracle/fixture (retrieve-contacts #{} contacts))))))

(def id (atom 0))

(defn oracle-persist-contact* [contacts-storage contact]
  (let [ids (set (map :id contacts-storage))]
    (loop [proposed-id (str (swap! id inc))]
      (if (contains? ids proposed-id)
        (recur (str (swap! id inc)))
        (conj contacts-storage (assoc contact :id proposed-id))))))

(oracle/register {'new/persist* oracle-persist-contact*})

(defn- contacts-data-is-identical? [sut oracle]
  (is (= (set (map #(dissoc % :id) sut))
         (set (map #(dissoc % :id) oracle)))))

(defn- ids-are-unique? [contacts]
  (is (= (count contacts)
         (count (set (map :id contacts))))))

(defn- persist-contact [storage contacts contact]
  (-> storage
      (contacts/persist* contacts)
      (new/persist* contact)
      (contacts/retrieve*)))

(defspec new-contact-integration-matches-oracle
  (for-all [contacts (malli.generator/generator contacts/schema)
            contact (malli.generator/generator new/schema)]
    (let [sut-results (persist-contact (atom #{}) contacts contact)
          oracle-results (oracle/fixture (persist-contact #{} contacts contact))]
      (is (contacts-data-is-identical? sut-results oracle-results))
      (is (ids-are-unique? sut-results))
      (is (ids-are-unique? oracle-results)))))

(defn oracle-retrieve-contact* [contacts-storage requested-id]
  (first (filter (fn [{:keys [id]}] (= requested-id id)) contacts-storage)))

(oracle/register {'contact/retrieve* oracle-retrieve-contact*})

(defn- contact-data-is-identical [sut oracle]
  (is (= (dissoc sut :id)
         (dissoc oracle :id))))

(defn- retrieve-contact [storage contacts id]
  (-> storage
      (contacts/persist* contacts)
      (contact/retrieve* id)))

(defspec retrieve-contact-integration-matches-oracle
  (for-all [contacts (generators/such-that
                       seq
                       (malli.generator/generator contacts/schema))
            id (generators/elements (map :id contacts))]
    (let [sut-results (retrieve-contact (atom #{}) contacts id)
          oracle-results (oracle/fixture (retrieve-contact #{} contacts id))]
      (is (contact-data-is-identical sut-results oracle-results)))))

(comment
  (contacts-integration-matches-oracle)
  (new-contact-integration-matches-oracle)
  (retrieve-contact-integration-matches-oracle)
  )
