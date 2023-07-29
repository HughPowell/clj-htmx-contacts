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

(defn persist* [contacts-storage contacts]
  (reset! contacts-storage contacts)
  contacts-storage)

(defn persist [contacts-storage contacts]
  (->> contacts
       set
       (validate contacts-schema)
       (persist* contacts-storage)))

(defn retrieve*
  ([contacts-storage] @contacts-storage)
  ([contacts-storage id] (first (get (group-by :id @contacts-storage) id))))

(defn retrieve
  ([contacts-storage]
   (->> contacts-storage
        (retrieve*)
        (validate contacts-schema)))
  ([contacts-storage id]
   (->> id
        (retrieve* contacts-storage)
        (validate [:maybe existing-contact-schema]))))

(defn create* [contacts-storage contact]
  (let [contact' (assoc contact :id (str (UUID/randomUUID)))]
    (swap! contacts-storage conj contact')
    contacts-storage))

(defn create [contacts-storage contact]
  (->> contact
       (validate new-contact-schema)
       (create* contacts-storage)))

(defn update* [contacts-storage contact]
  (let [replace (fn [contacts contact]
                  (set (map
                         (fn [{:keys [id] :as contact'}]
                           (if (= id (:id contact))
                             contact
                             contact'))
                         contacts)))]
    (swap! contacts-storage replace contact))
  contacts-storage)

(defn update [contacts-storage contact]
  (->> contact
       (validate existing-contact-schema)
       (update* contacts-storage)))

(defn delete* [contacts-storage contact-id]
  (swap! contacts-storage #(set (remove (fn [{:keys [id]}] (= contact-id id)) %)))
  contacts-storage)

(defn delete [contacts-storage id]
  (delete* contacts-storage id))

(comment
  )
