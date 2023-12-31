(ns contacts.test-lib.oracle
  (:require [contacts.system.auth :as auth]
            [contacts.system.contacts-storage :as contacts-storage]
            [contacts.system.users-storage :as users-storage]))

(defn contacts-storage []
  (let [store (atom {})
        next-id (atom 0)]
    (set-validator! store (fn [contacts] (every? (fn [[k {:keys [id]}]] (= k id)) contacts)))
    (reify contacts-storage/ContactsStorage
      (retrieve* [_] (set (vals @store)))
      (retrieve* [_ id] (get @store id))
      (create* [this contact]
        (loop [proposed-id (str (swap! next-id inc))]
          (when-not
            (try
              (swap! store (fn [contacts]
                             (when (contains? contacts proposed-id)
                               (throw (ex-info (format "%s already exists as a key" proposed-id)
                                               {:key  proposed-id
                                                :keys (keys contacts)})))
                             (assoc contacts proposed-id (assoc contact :id proposed-id))))
              (catch Exception _))
            (recur (str (swap! next-id inc)))))
        this)
      (update* [this contact]
        (swap! store assoc (:id contact) contact) this)
      (delete* [this id]
        (swap! store dissoc id) this))))

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
      contacts-storage/ByUserContactsStorage
      (contacts-storage/retrieve-for-user* [_ user-id]
        (-> @store
            (get-in [user-id :contacts])
            (vals)
            (set)))
      (contacts-storage/retrieve-for-user* [_ user-id contact-id]
        (get-in @store [user-id :contacts contact-id]))
      (contacts-storage/create-for-user* [this user-id contact]
        (loop [proposed-id (str (random-uuid))]
          (if (contains? (:contacts (get @store user-id)) proposed-id)
            (recur (str (random-uuid)))
            (swap! store update-in [user-id :contacts] assoc proposed-id (assoc contact :id proposed-id))))
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
      (if users-storage
        {:user (users-storage/->user users-storage authorisation-id)}
        true))
    (handle-unauthorized [_ _])))

(comment
  )
