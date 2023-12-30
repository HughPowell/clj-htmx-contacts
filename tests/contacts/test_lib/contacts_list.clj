(ns contacts.test-lib.contacts-list
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as generators]
            [contacts.system.contacts-storage :as contacts-storage]
            [contacts.test-lib.html :as html]
            [contacts.test-lib.request :as request]
            [contacts.test-lib.test-system :as test-system]
            [idle.multiset.api :as mset]))

(defn new-contacts-generator [& opts]
  (generators/fmap mset/multiset
                   (apply generators/vector
                          (malli.generator/generator contacts-storage/new-contact-schema)
                          opts)))

(defn- contacts-list [handler contacts-list-request]
  (->> contacts-list-request
       (test-system/make-request handler)
       (html/rendered-contacts)))

(defn existing-contacts-generator [handler]
  (generators/let [contacts-list-request (request/generator "/contacts")]
    (generators/return (contacts-list handler contacts-list-request))))

(defn nth-contact-generator [handler]
  (generators/let [contacts (existing-contacts-generator handler)
                   contact-index (generators/large-integer* {:min 0 :max (dec (count contacts))})]
    (generators/return (nth contacts contact-index))))

(defn strip-ids [contacts]
  (->> contacts
       (map (fn [contact] (dissoc contact :id)))
       (mset/multiset)))
