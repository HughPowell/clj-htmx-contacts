(ns contacts.test-lib.oracle
  (:require [contacts.system.auth :as auth]
            [contacts.system.contacts-storage :as contacts-storage]
            [contacts.system.users-storage :as users-storage]))

(defn- user-keys-match-user-ids [users]
  (every? (fn [[user-key {:keys [user-id]}]] (= user-key user-id)) users))

(defn- contact-keys-match-contact-ids [contacts]
  (every? (fn [[contact-key {:keys [id]}]] (= contact-key id)) contacts))

(defn data-storage []
  (let [store (atom {})]
    (set-validator! store (fn [users]
                            (and (user-keys-match-user-ids users)
                                 (->> users
                                      (vals)
                                      (map :contacts)
                                      (every? contact-keys-match-contact-ids)))))
    (reify
      contacts-storage/ContactsStorage
      (contacts-storage/retrieve* [_ user-id]
        (-> @store
            (get-in [user-id :contacts])
            (vals)
            (set)))
      (contacts-storage/retrieve* [_ user-id contact-id]
        (get-in @store [user-id :contacts contact-id]))
      (contacts-storage/create* [this user-id contact]
        (loop [proposed-id (str (random-uuid))]
          (if (contains? (:contacts (get @store user-id)) proposed-id)
            (recur (str (random-uuid)))
            (swap! store update-in [user-id :contacts] assoc proposed-id (assoc contact :id proposed-id))))
        this)
      (contacts-storage/update* [this user-id contact]
        (swap! store update-in [user-id :contacts] assoc (:id contact) contact)
        this)
      (contacts-storage/delete* [this user-id contact-id]
        (swap! store update-in [user-id :contacts] dissoc contact-id)
        this)
      users-storage/UsersStorage
      (users-storage/->user* [_ authorisation-id]
        (if-let [user (first (filter (fn [user] (= (:authorisation-id user) authorisation-id)) (vals @store)))]
          user
          (loop [proposed-id (str (random-uuid))]
            (if (contains? @store proposed-id)
              (recur (str (random-uuid)))
              (let [new-user {:authorisation-id authorisation-id :user-id proposed-id}]
                (swap! store assoc proposed-id new-user)
                new-user))))))))

(defn authorization [users-storage]
  (reify auth/Authorization
    (authorized? [_ {{:keys [authorisation-id]} :request}]
      (when authorisation-id
        {:user (users-storage/->user users-storage authorisation-id)}))
    (handle-unauthorized [_ _])))

(comment
  )
