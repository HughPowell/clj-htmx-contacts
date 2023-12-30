(ns contacts.test-lib.oracle
  (:require [contacts.system.auth :as auth]
            [contacts.system.contacts-storage :as contacts-storage]))

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

(defn authorization []
  (reify auth/Authorization
    (authorized? [_ _] {:authorization-id "test"})
    (handle-unauthorized [_ _])))

(comment
  )
