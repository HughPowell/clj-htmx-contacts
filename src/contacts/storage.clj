(ns contacts.storage
  (:require [contacts.contact.schemas :as schemas]
            [malli.core :as malli]
            [malli.error :as malli.error]))

(defn- ids-are-unique? [contacts]
  (= (count contacts)
     (count (set (map :id contacts)))))

(def schema
  [:and
   [:set [:map
          schemas/id
          schemas/first-name
          schemas/last-name
          schemas/phone
          schemas/email]]
   [:fn ids-are-unique?]])

(defn- validate [schema contacts]
  (when-not (malli/validate schema contacts)
    (let [explanation (malli/explain schema contacts)]
      (throw (ex-info (malli.error/humanize explanation) explanation)))))

(defn persist* [contacts-storage contacts]
  (reset! contacts-storage (set contacts))
  contacts-storage)

(defn persist [contacts-storage contacts]
  (validate schema contacts)
  (persist* contacts-storage contacts))

(defn retrieve* [contacts-storage]
  @contacts-storage)

(defn retrieve [contacts-storage]
  (let [contacts (retrieve* contacts-storage)]
    (validate schema contacts)
    contacts))
