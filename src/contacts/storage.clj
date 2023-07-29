(ns contacts.storage
  (:require [contacts.contact.schemas :as schemas]
            [malli.core :as malli]
            [malli.error :as malli.error]))

(def contact-schema
  [:map
   schemas/id
   schemas/first-name
   schemas/last-name
   schemas/phone
   schemas/email])

(defn- ids-are-unique? [contacts]
  (= (count contacts)
     (count (set (map :id contacts)))))

(def contacts-schema
  [:and
   [:set contact-schema]
   [:fn ids-are-unique?]])

(defn- validate [schema contacts]
  (when-not (malli/validate schema contacts)
    (let [explanation (malli/explain schema contacts)]
      (throw (ex-info (malli.error/humanize explanation) explanation)))))

(defn persist* [contacts-storage contacts]
  (reset! contacts-storage (set contacts))
  contacts-storage)

(defn persist [contacts-storage contacts]
  (validate contacts-schema contacts)
  (persist* contacts-storage contacts))

(defn retrieve*
  ([contacts-storage] @contacts-storage)
  ([contacts-storage id] (first (get (group-by :id @contacts-storage) id))))

(defn retrieve
  ([contacts-storage]
   (let [contacts (retrieve* contacts-storage)]
     (validate contacts-schema contacts)
     contacts))
  ([contacts-storage id]
   (let [contact (retrieve* contacts-storage id)]
     (when (malli/validate contact-schema contact)
       contact))))
