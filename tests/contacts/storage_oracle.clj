(ns contacts.storage-oracle
  (:require [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [contacts.contact.delete :as delete]
            [contacts.contact.edit :as edit]
            [contacts.contact.new :as new]
            [contacts.lib.oracle :as oracle]
            [contacts.storage :as storage]
            [malli.generator :as malli.generator]))

(defn oracle-persist-contacts* [_contacts-storage contacts]
  (set contacts))

(defn oracle-retrieve*
  ([contacts-storage] contacts-storage)
  ([contacts-storage requested-id] (first (filter (fn [{:keys [id]}] (= requested-id id)) contacts-storage))))

(oracle/register {'storage/persist*  oracle-persist-contacts*
                  'storage/retrieve* oracle-retrieve*})

(defn- retrieve-contacts [storage contacts]
  (-> storage
      (storage/persist* contacts)
      (storage/retrieve*)))

(defspec contacts-integration-matches-oracle
  (for-all [contacts (malli.generator/generator storage/contacts-schema)]
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
      (storage/persist* contacts)
      (new/persist* contact)
      (storage/retrieve*)))

(defspec new-contact-integration-matches-oracle
  (for-all [contacts (malli.generator/generator storage/contacts-schema)
            contact (malli.generator/generator new/schema)]
    (let [sut-results (persist-contact (atom #{}) contacts contact)
          oracle-results (oracle/fixture (persist-contact #{} contacts contact))]
      (is (contacts-data-is-identical? sut-results oracle-results))
      (is (ids-are-unique? sut-results))
      (is (ids-are-unique? oracle-results)))))

(defn oracle-retrieve-contact* [contacts-storage requested-id]
  (first (filter (fn [{:keys [id]}] (= requested-id id)) contacts-storage)))

(defn- contact-data-is-identical [sut oracle]
  (is (= (dissoc sut :id)
         (dissoc oracle :id))))

(defn- retrieve-contact [storage contacts id]
  (-> storage
      (storage/persist* contacts)
      (storage/retrieve* id)))

(defspec retrieve-contact-integration-matches-oracle
  (for-all [contacts (generators/such-that
                       seq
                       (malli.generator/generator storage/contacts-schema))
            id (generators/elements (map :id contacts))]
    (let [sut-results (retrieve-contact (atom #{}) contacts id)
          oracle-results (oracle/fixture (retrieve-contact #{} contacts id))]
      (is (contact-data-is-identical sut-results oracle-results)))))

(defn oracle-edit-persist-contact* [contacts-storage updated-contact]
  (conj
    (remove (fn [{:keys [id]}] (= (:id updated-contact) id)) contacts-storage)
    updated-contact))

(oracle/register {'edit/persist*  oracle-edit-persist-contact*
                  'edit/retrieve* oracle-retrieve-contact*})

(defn- update-contact [storage contacts updated-contact]
  (-> storage
      (storage/persist* contacts)
      (edit/persist* updated-contact)
      (storage/retrieve*)))

(defn- contacts-are-identical? [sut oracle]
  (is (= (set (map #(dissoc % :id) sut))
         (set (map #(dissoc % :id) oracle)))))

(defspec persist-update-to-contact-integration-matches-oracle
  (for-all [contacts (generators/such-that seq (malli.generator/generator storage/contacts-schema))
            id (generators/fmap :id (generators/elements contacts))
            updated-contact (generators/fmap
                              #(assoc % :id id)
                              (malli.generator/generator storage/contact-schema))]
    (let [sut-results (update-contact (atom #{}) contacts updated-contact)
          oracle-results (oracle/fixture (update-contact #{} contacts updated-contact))]
      (contacts-are-identical? sut-results oracle-results))))

(defn oracle-delete-contact* [contacts-storage contact-id]
  (remove (fn [{:keys [id]}] (= contact-id id)) contacts-storage))

(oracle/register {'delete/retrieve* oracle-retrieve-contact*
                  'delete/delete*   oracle-delete-contact*})

(defn- delete-contact [storage contacts contact-id]
  (-> storage
      (storage/persist* contacts)
      (delete/delete* contact-id)
      (storage/retrieve*)))

(defspec delete-contact-integration-matches-oracle
  (for-all [contacts (generators/such-that seq (malli.generator/generator storage/contacts-schema))
            id (generators/fmap :id (generators/elements contacts))]
    (let [sut-results (delete-contact (atom #{}) contacts id)
          oracle-results (oracle/fixture (delete-contact #{} contacts id))]
      (contacts-are-identical? sut-results oracle-results))))

(comment
  (contacts-integration-matches-oracle)
  (new-contact-integration-matches-oracle)
  (retrieve-contact-integration-matches-oracle)
  (persist-update-to-contact-integration-matches-oracle)
  (delete-contact-integration-matches-oracle)
  )
