(ns contacts.contacts-storage-oracle
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is use-fixtures]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.system.contacts-storage :as contacts-storage]
            [contacts.system.users-storage :as users-storage]
            [contacts.test-lib.contacts-list :as contacts-list]
            [contacts.test-lib.database :as database]
            [contacts.test-lib.oracle :as oracle]
            [contacts.test-lib.users :as users]))

(use-fixtures :once database/postgres-fixture)

(defn index-%-per-action-generator [actions]
  (->> actions
       (filter #{:retrieve :update :delete})
       (count)
       (generators/vector
         (generators/such-that
           #(not= 1.0 %)
           (generators/double* {:NaN? false
                                :min 0
                                :max 1})))))

(defn- create-contact [sut sut-user-id oracle oracle-user-id contact]
  (contacts-storage/create sut sut-user-id contact)
  (contacts-storage/create oracle oracle-user-id contact))

(defn- find-equivalent-contact [storage user-id contact]
  (->> user-id
       (contacts-storage/retrieve storage)
       (filter (fn [contact']
                 (= (dissoc contact' :id)
                    (dissoc contact :id))))
       (first)))

(defn- rand-% [s index-%]
  (when-let [seq-s (seq s)]
    (nth seq-s (* index-% (count seq-s)))))

(defn- retrieve-contact [sut sut-user-id oracle oracle-user-id index-%]
  (when-let [oracle-contact-to-retrieve (rand-% (contacts-storage/retrieve oracle oracle-user-id) index-%)]
    (let [sut-contact-to-retrieve (find-equivalent-contact sut sut-user-id oracle-contact-to-retrieve)]
      (is (=
            (-> oracle
                (contacts-storage/retrieve oracle-user-id (:id oracle-contact-to-retrieve))
                (dissoc :id))
            (-> sut
                (contacts-storage/retrieve sut-user-id (:id sut-contact-to-retrieve))
                (dissoc :id)))))))

(defn- update-contact [sut sut-user-id oracle oracle-user-id update index-%]
  (when-let [oracle-contact-to-update (rand-% (contacts-storage/retrieve oracle oracle-user-id) index-%)]
    (contacts-storage/update oracle oracle-user-id (merge oracle-contact-to-update update))
    (let [sut-contact-to-update (find-equivalent-contact sut sut-user-id oracle-contact-to-update)]
      (contacts-storage/update sut sut-user-id (merge sut-contact-to-update update)))))

(defn- delete-contact [sut sut-user-id oracle oracle-user-id index-%]
  (when-let [oracle-contact-to-delete (rand-% (contacts-storage/retrieve oracle oracle-user-id) index-%)]
    (contacts-storage/delete oracle oracle-user-id (:id oracle-contact-to-delete))
    (let [sut-contact-to-delete (find-equivalent-contact sut sut-user-id oracle-contact-to-delete)]
      (contacts-storage/delete sut sut-user-id (:id sut-contact-to-delete)))))

(deftest ensure-contacts-storage-matches-oracle
  (checking "" [authorisation-id users/authorisation-id-generator
                actions (->> #{:create :retrieve :update :delete}
                             (generators/elements)
                             (generators/vector)
                             (generators/such-that seq))
                initial-contacts contacts-list/contacts-list-generator
                new-contacts (contacts-list/n-contacts-list-generator (count (filter #{:create} actions)))
                contact-updates (contacts-list/n-contacts-list-generator (count (filter #{:update} actions)))
                index-%s (index-%-per-action-generator actions)]
            (with-open [connection (database/reset)]
              (let [sut (contacts-storage/contacts-storage connection)
                    {sut-user-id :user-id} (users-storage/->user (users-storage/users-storage connection) authorisation-id)
                    oracle (oracle/data-storage)
                    {oracle-user-id :user-id} (users-storage/->user oracle authorisation-id)]
                (run! (fn [contact] (create-contact sut sut-user-id oracle oracle-user-id contact)) initial-contacts)
                (loop [actions actions
                       new-contacts new-contacts
                       contact-updates contact-updates
                       index-%s index-%s]
                  (when (seq actions)
                    (let [action (first actions)
                          index-% (first index-%s)]
                      (case action
                        :create (create-contact sut sut-user-id oracle oracle-user-id (first new-contacts))
                        :retrieve (retrieve-contact sut sut-user-id oracle oracle-user-id index-%)
                        :update (update-contact sut sut-user-id oracle oracle-user-id (first contact-updates) index-%)
                        :delete (delete-contact sut sut-user-id oracle oracle-user-id index-%))

                      (is (= (contacts-list/strip-ids (contacts-storage/retrieve oracle oracle-user-id))
                             (contacts-list/strip-ids (contacts-storage/retrieve sut sut-user-id))))

                      (recur
                        (rest actions)
                        (if (= action :create) (rest new-contacts) new-contacts)
                        (if (= action :update) (rest contact-updates) contact-updates)
                        (if (#{:retrieve :update :delete} action) (rest index-%s) index-%s)))))))))

(defn- ensure-stable-ids-on-create [sut user-id contact]
  (let [original-ids (set (map :id (contacts-storage/retrieve sut user-id)))]
    (contacts-storage/create sut user-id contact)
    (let [latest-ids (set (map :id (contacts-storage/retrieve sut user-id)))]
      (is (= 1 (count (set/difference latest-ids original-ids)))
          (format "Latest IDs: %s; Original IDs: %s" (str latest-ids) (str original-ids))))))

(defn- ensure-stable-ids-on-update [sut user-id update index-%]
  (when-let [original-contacts (seq (contacts-storage/retrieve sut user-id))]
    (let [contact-to-update (rand-% original-contacts index-%)]
      (contacts-storage/update sut user-id (merge contact-to-update update))
      (is (= (set (map :id original-contacts))
             (set (map :id (contacts-storage/retrieve sut user-id))))))))

(defn- ensure-stable-ids-on-delete [sut user-id index-%]
  (when-let [original-contacts (seq (contacts-storage/retrieve sut user-id))]
    (let [contact-to-delete (rand-% original-contacts index-%)]
      (contacts-storage/delete sut user-id (:id contact-to-delete))
      (is (= #{(:id contact-to-delete)}
             (set/difference (set (map :id original-contacts))
                             (set (map :id (contacts-storage/retrieve sut user-id)))))))))

(defn- ensure-ids-are-unique [sut user-id]
  (let [all-contact-ids (map :id (contacts-storage/retrieve sut user-id))]
    (is (= (sort (set all-contact-ids))
           (sort all-contact-ids)))))

(deftest ensure-ids-remain-stable
  (checking "" [authorisation-id users/authorisation-id-generator
                actions (->> #{:create :update :delete}
                             (generators/elements)
                             (generators/vector)
                             (generators/such-that seq))
                initial-contacts contacts-list/contacts-list-generator
                new-contacts (contacts-list/n-contacts-list-generator (count (filter #{:create} actions)))
                contact-updates (contacts-list/n-contacts-list-generator (count (filter #{:update} actions)))
                index-%s (index-%-per-action-generator actions)]
            (with-open [connection (database/reset)]
              (let [sut (contacts-storage/contacts-storage connection)
                    {:keys [user-id]} (users-storage/->user (users-storage/users-storage connection) authorisation-id)]
                (run! (fn [contact] (contacts-storage/create sut user-id contact)) initial-contacts)
                (loop [actions actions
                       new-contacts new-contacts
                       contact-updates contact-updates
                       index-%s index-%s]
                  (when (seq actions)

                    (let [action (first actions)
                          index-% (first index-%s)]
                      (case action
                        :create (ensure-stable-ids-on-create sut user-id (first new-contacts))
                        :update (ensure-stable-ids-on-update sut user-id (first contact-updates) index-%)
                        :delete (ensure-stable-ids-on-delete sut user-id index-%))

                      (ensure-ids-are-unique sut user-id)

                      (recur
                        (rest actions)
                        (if (= action :create) (rest new-contacts) new-contacts)
                        (if (= action :update) (rest contact-updates) contact-updates)
                        (if (#{:update :delete} action) (rest index-%s) index-%s)))))))))

(comment
  (ensure-contacts-storage-matches-oracle)
  (ensure-ids-remain-stable))
