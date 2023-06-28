(ns contacts.contacts-client
  (:require [clojure.string :as string]
            [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [clojure.test.check.properties :refer [for-all]]
            [contacts.contacts :as sut]
            [malli.generator :as malli.generator]))

(defn oracle-persist [_contacts-storage contacts]
  (vec contacts))

(defn oracle-retrieve [contacts-storage]
  contacts-storage)

(defspec contacts-are-stored-as-if-a-vector
  (for-all [contacts (malli.generator/generator sut/schema)]
    (let [contacts-storage (atom [])]
      (is (= (-> [] (oracle-persist contacts) (oracle-retrieve))
             (-> contacts-storage (sut/persist contacts) (sut/retrieve)))))))

(defn- substring-generator [s]
  (generators/let [start (generators/choose 0 (count s))
                   end (generators/choose start (count s))]
    (subs s start end)))

(defn- contact-matches-search? [contact search]
  (some #(string/includes? % search)
        (vals (select-keys contact [:first-name :last-name :phone :email]))))

(defn- all-contain [contacts search]
  (->> (sut/find contacts search)
       (every? #(contact-matches-search? % search))))

(defn- no-not-found-contain-search [contacts search]
  (->> contacts
       (remove (set (sut/find contacts search)))
       (not-any? #(contact-matches-search? % search))))

(defn- all-found-contacts-in-original-list [contacts search]
  (every? (set contacts) (sut/find contacts search)))

(defspec finds-all-contacts-that-match-search-string
  (for-all [[contacts search] (generators/let [contacts (malli.generator/generator sut/schema)
                                               string' (if (seq contacts)
                                                         (->> contacts
                                                              (mapcat vals)
                                                              (generators/elements))
                                                         (generators/return ""))
                                               search (substring-generator string')]
                                [contacts search])]
    (and (is (all-contain contacts search))
         (is (no-not-found-contain-search contacts search))
         (is (all-found-contacts-in-original-list contacts search)))))

(defspec nil-search-string-returns-all-contacts
  (for-all [contacts (malli.generator/generator sut/schema)]
    (is (= contacts (sut/find contacts nil)))))

(comment
  (finds-all-contacts-that-match-search-string)
  (nil-search-string-returns-all-contacts)
  (contacts-are-stored-as-if-a-vector))
