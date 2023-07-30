(ns contacts.lib.oracle
  (:import (contacts.storage ContactsStorage)))

(defn contacts-storage [contacts]
  (let [store (atom (-> (group-by :id contacts) (update-vals first)))
        next-id (atom 0)]
    (set-validator! store (fn [contacts] (every? (fn [[k {:keys [id]}]] (= k id)) contacts)))
    (reify ContactsStorage
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

(comment
  )
