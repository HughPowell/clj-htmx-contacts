(ns contacts.test-lib.contacts-list
  (:require [clojure.test.check.generators :as generators]
            [contacts.system.contacts-storage :as contacts-storage]
            [contacts.test-lib.html :as html]
            [contacts.test-lib.request :as request]
            [contacts.test-lib.test-system :as test-system]
            [idle.multiset.api :as mset]
            [malli.generator]))

(def contacts-list-generator
  (->> contacts-storage/new-contact-schema
       (malli.generator/generator)
       (generators/vector)
       (generators/fmap mset/multiset)))

(def non-empty-contacts-list-generator
  (->> contacts-list-generator
       (generators/such-that seq)))

(defn n-contacts-list-generator [n]
  (let [new-contact-generator (malli.generator/generator contacts-storage/new-contact-schema)]
    (->> n
         (generators/vector new-contact-generator)
         (generators/fmap mset/multiset))))

(defn- contacts-list [handler contacts-list-request]
  (->> contacts-list-request
       (test-system/make-request handler)
       (html/rendered-contacts)))

(defn existing-contacts-generator [handler authorisation-id]
  (generators/let [contacts-list-request (request/generator authorisation-id "/contacts")]
    (generators/return (contacts-list handler contacts-list-request))))

(defn nth-contact-generator [handler authorisation-id]
  (generators/let [contacts (existing-contacts-generator handler authorisation-id)
                   contact-index (generators/large-integer* {:min 0
                                                             :max (dec (count contacts))})]
    (generators/return (nth contacts contact-index))))

(defn strip-ids [contacts]
  (->> contacts
       (map (fn [contact] (dissoc contact :id)))
       (mset/multiset)))
