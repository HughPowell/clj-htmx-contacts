(ns contacts.contacts-storage-oracle
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is use-fixtures]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.test-lib.contacts-list :as contacts-list]
            [contacts.test-lib.database :as database]
            [contacts.test-lib.oracle :as oracle]
            [contacts.system.contacts-storage :as contacts-storage]))

(use-fixtures :once database/postgres-fixture)

(defn index-%-per-action-generator [actions]
  (->> actions
       (filter #{:retrieve :update :delete})
       (count)
       (generators/vector
         (generators/such-that
           #(not= 1.0 %)
           (generators/double* {:NaN? false :min 0 :max 1})))))

(defn- create-contact [sut oracle contact]
  (contacts-storage/create sut contact)
  (contacts-storage/create oracle contact))

(defn- find-equivalent-contact [storage contact]
  (->> storage
       (contacts-storage/retrieve)
       (filter (fn [contact']
                 (= (dissoc contact' :id)
                    (dissoc contact :id))))
       (first)))

(defn- rand-% [s index-%]
  (when-let [seq-s (seq s)]
    (nth seq-s (* index-% (count seq-s)))))

(defn- retrieve-contact [sut oracle index-%]
  (when-let [oracle-contact-to-retrieve (rand-% (contacts-storage/retrieve oracle) index-%)]
    (let [sut-contact-to-retrieve (find-equivalent-contact sut oracle-contact-to-retrieve)]
      (is (=
            (dissoc (contacts-storage/retrieve oracle (:id oracle-contact-to-retrieve)) :id)
            (dissoc (contacts-storage/retrieve sut (:id sut-contact-to-retrieve)) :id))))))

(defn- update-contact [sut oracle update index-%]
  (when-let [oracle-contact-to-update (rand-% (contacts-storage/retrieve oracle) index-%)]
    (contacts-storage/update oracle (merge oracle-contact-to-update update))
    (let [sut-contact-to-update (find-equivalent-contact sut oracle-contact-to-update)]
      (contacts-storage/update sut (merge sut-contact-to-update update)))))

(defn- delete-contact [sut oracle index-%]
  (when-let [oracle-contact-to-delete (rand-% (contacts-storage/retrieve oracle) index-%)]
    (contacts-storage/delete oracle (:id oracle-contact-to-delete))
    (let [sut-contact-to-delete (find-equivalent-contact sut oracle-contact-to-delete)]
      (contacts-storage/delete sut (:id sut-contact-to-delete)))))

(deftest ensure-contacts-storage-matches-oracle
  (checking "" [actions (->> #{:create :retrieve :update :delete}
                             (generators/elements)
                             (generators/vector)
                             (generators/such-that seq))
                initial-contacts (contacts-list/new-contacts-generator)
                new-contacts (contacts-list/new-contacts-generator (count (filter #{:create} actions)))
                contact-updates (contacts-list/new-contacts-generator (count (filter #{:update} actions)))
                index-%s (index-%-per-action-generator actions)]
    (with-open [connection (database/reset)]
      (let [sut (contacts-storage/contacts-storage connection)
            oracle (oracle/contacts-storage)]
        (run! (fn [contact] (create-contact sut oracle contact)) initial-contacts)
        (loop [actions actions
               new-contacts new-contacts
               contact-updates contact-updates
               index-%s index-%s]
          (when (seq actions)
            (let [action (first actions)
                  index-% (first index-%s)]
              (case action
                :create (create-contact sut oracle (first new-contacts))
                :retrieve (retrieve-contact sut oracle index-%)
                :update (update-contact sut oracle (first contact-updates) index-%)
                :delete (delete-contact sut oracle index-%))

              (is (= (contacts-list/strip-ids (contacts-storage/retrieve oracle))
                     (contacts-list/strip-ids (contacts-storage/retrieve sut))))

              (recur
                (rest actions)
                (if (= action :create) (rest new-contacts) new-contacts)
                (if (= action :update) (rest contact-updates) contact-updates)
                (if (#{:retrieve :update :delete} action) (rest index-%s) index-%s)))))))))

(defn- ensure-stable-ids-on-create [sut contact]
  (let [original-ids (set (map :id (contacts-storage/retrieve sut)))]
    (contacts-storage/create sut contact)
    (let [latest-ids (set (map :id (contacts-storage/retrieve sut)))]
      (is (= 1 (count (set/difference latest-ids original-ids)))
          (format "Latest IDs: %s; Original IDs: %s" (str latest-ids) (str original-ids))))))

(defn- ensure-stable-ids-on-update [sut update index-%]
  (when-let [original-contacts (seq (contacts-storage/retrieve sut))]
    (let [contact-to-update (rand-% original-contacts index-%)]
      (contacts-storage/update sut (merge contact-to-update update))
      (is (= (set (map :id original-contacts))
             (set (map :id (contacts-storage/retrieve sut))))))))

(defn- ensure-stable-ids-on-delete [sut index-%]
  (when-let [original-contacts (seq (contacts-storage/retrieve sut))]
    (let [contact-to-delete (rand-% original-contacts index-%)]
      (contacts-storage/delete sut (:id contact-to-delete))
      (is (= #{(:id contact-to-delete)}
             (set/difference (set (map :id original-contacts))
                             (set (map :id (contacts-storage/retrieve sut)))))))))

(defn- ensure-ids-are-unique [sut]
  (let [all-contact-ids (map :id (contacts-storage/retrieve sut))]
    (is (= (sort (set all-contact-ids))
           (sort all-contact-ids)))))

(deftest ensure-ids-remain-stable
  (checking "" [actions (->> #{:create :update :delete}
                             (generators/elements)
                             (generators/vector)
                             (generators/such-that seq))
                initial-contacts (contacts-list/new-contacts-generator)
                new-contacts (contacts-list/new-contacts-generator (count (filter #{:create} actions)))
                contact-updates (contacts-list/new-contacts-generator (count (filter #{:update} actions)))
                index-%s (index-%-per-action-generator actions)]
    (with-open [connection (database/reset)]
      (let [sut (contacts-storage/contacts-storage connection)]
        (run! (fn [contact] (contacts-storage/create sut contact)) initial-contacts)
        (loop [actions actions
               new-contacts new-contacts
               contact-updates contact-updates
               index-%s index-%s]
          (when (seq actions)

            (let [action (first actions)
                  index-% (first index-%s)]
              (case action
                :create (ensure-stable-ids-on-create sut (first new-contacts))
                :update (ensure-stable-ids-on-update sut (first contact-updates) index-%)
                :delete (ensure-stable-ids-on-delete sut index-%))

              (ensure-ids-are-unique sut)

              (recur
                (rest actions)
                (if (= action :create) (rest new-contacts) new-contacts)
                (if (= action :update) (rest contact-updates) contact-updates)
                (if (#{:update :delete} action) (rest index-%s) index-%s)))))))))


(comment
  (ensure-contacts-storage-matches-oracle)
  (ensure-ids-remain-stable)
  )
