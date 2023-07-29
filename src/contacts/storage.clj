(ns contacts.storage
  (:require [contacts.contact :as contact]
            [malli.core :as malli]
            [malli.error :as malli.error]))

(defn- ids-are-unique? [contacts]
  (= (count contacts)
     (count (set (map :id contacts)))))

(def schema
  [:and
   [:set [:map
          contact/id
          contact/first-name
          contact/last-name
          contact/phone
          contact/email]]
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
