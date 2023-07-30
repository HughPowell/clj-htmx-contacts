(ns contacts.storage
  (:require [contacts.contact.schemas :as schemas]
            [malli.core :as malli]
            [malli.error :as malli.error]
            [malli.util :as malli.util])
  (:refer-clojure :exclude [update])
  (:import (java.util UUID)))

;; Schemas

(def new-contact-schema
  [:map
   schemas/first-name
   schemas/last-name
   schemas/phone
   schemas/email])

(def existing-contact-schema
  (malli.util/merge
    new-contact-schema
    [:map schemas/id]))

(defn- ids-are-unique? [contacts]
  (= (count contacts)
     (count (set (map :id contacts)))))

(def contacts-schema
  [:and
   [:set existing-contact-schema]
   [:fn ids-are-unique?]])

(defn- validate [schema data]
  (when-not (malli/validate schema data)
    (let [explanation (malli/explain schema data)]
      (throw (ex-info (str (malli.error/humanize explanation)) explanation))))
  data)

;; Persistence

(defprotocol ContactsStorage
  (retrieve* [this] [this id])
  (create* [this contact])
  (update* [this contact])
  (delete* [this id]))

(defn contacts-storage [contacts]
  (let [store (atom contacts)]
    (reify ContactsStorage
      (retrieve* [_] @store)
      (retrieve* [_ id] (first (get (group-by :id @store) id)))
      (create* [this contact]
        (let [contact' (assoc contact :id (str (UUID/randomUUID)))]
          (swap! store conj contact')
          this))
      (update* [this contact]
        (letfn [(replace [contacts contact]
                  (set (map
                         (fn [{:keys [id] :as contact'}]
                           (if (= id (:id contact))
                             contact
                             contact'))
                         contacts)))]
          (swap! store replace contact)
          this))
      (delete* [this contact-id]
        (swap! store #(set (remove (fn [{:keys [id]}] (= contact-id id)) %)))
        this))))

(defn persist [contacts-storage contacts]
  (->> contacts
       set
       (validate contacts-schema)
       (contacts-storage)))

(defn retrieve
  ([contacts-storage]
   (->> contacts-storage
        (retrieve*)
        (validate contacts-schema)))
  ([contacts-storage id]
   (->> id
        (retrieve* contacts-storage)
        (validate [:maybe existing-contact-schema]))))

(defn create [contacts-storage contact]
  (->> contact
       (validate new-contact-schema)
       (create* contacts-storage)))

(defn update [contacts-storage contact]
  (->> contact
       (validate existing-contact-schema)
       (update* contacts-storage)))

(defn delete [contacts-storage id]
  (delete* contacts-storage id))

(comment
  )
