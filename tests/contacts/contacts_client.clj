(ns contacts.contacts-client
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [clojure.test.check.properties :refer [for-all]]
            [contacts.app :as app]
            [contacts.contacts :as sut]
            [contacts.lib.html :as html]
            [contacts.lib.oracle :as oracle]
            [contacts.lib.request :as request]
            [idle.multiset.api :as mset]
            [net.cgrand.enlive-html :as enlive]
            [malli.generator :as malli.generator]))

(defn oracle-persist* [_contacts-storage contacts]
  (set contacts))

(defn oracle-retrieve* [contacts-storage]
  contacts-storage)

(oracle/register {'sut/persist*  oracle-persist*
                  'sut/retrieve* oracle-retrieve*})

(defspec contacts-integration-matches-oracle
  (for-all [contacts (malli.generator/generator sut/schema)]
    (let [contacts-storage (atom #{})]
      (is (= (-> #{} (oracle-persist* contacts) (oracle-retrieve*))
             (-> contacts-storage (sut/persist* contacts) (sut/retrieve*)))))))

(def sut-path "/contacts")

(defn- request-generator
  ([path] (request/generator path))
  ([path search] (request-generator path search {}))
  ([path search opts]
   (->> {:query-string (request/map->query-string {:query search})}
        (merge opts)
        (request/generator path))))

(defn- substring-generator [s]
  (generators/let [start (generators/choose 0 (count s))
                   end (generators/choose start (count s))]
    (subs s start end)))

(defn- successful-response? [contacts request]
  (oracle/fixture
    (let [response ((app/handler contacts) request)]
      (= 200 (:status response)))))

(defn- returns-expected-data-type?
  ([contacts request]
   (oracle/fixture
     (let [response ((app/handler contacts) request)]
       (= "text/html;charset=UTF-8" (get-in response [:headers "Content-Type"]))))))

(defn- rendered-contacts [response]
  (-> response
      (:body)
      (enlive/html-snippet)
      (html/table->map [:first-name :last-name :phone :email])))

(defn- all-contacts-are-rendered? [contacts request]
  (oracle/fixture
    (let [response ((app/handler contacts) request)]
      (=
        (set (rendered-contacts response))
        (set (map #(dissoc % :id) contacts))))))

(defspec all-contacts-returned-when-no-query
  (for-all [contacts (malli.generator/generator sut/schema)
            request (request-generator sut-path)]
    (and (is (successful-response? contacts request))
         (is (returns-expected-data-type? contacts request))
         (is (all-contacts-are-rendered? contacts request)))))

(defn- all-contacts-that-match-are-rendered? [contacts request search]
  (oracle/fixture
    (let [response ((app/handler contacts) request)
          rendered-contacts (rendered-contacts response)]
      (every? (fn [contact]
                (some #(string/includes? % search) (vals contact)))
              rendered-contacts))))

(defn- unmatched-contacts-are-not-rendered? [contacts request search]
  (oracle/fixture
    (let [response ((app/handler contacts) request)
          rendered-contacts (-> response
                                (:body)
                                (enlive/html-snippet)
                                (html/table->map [:first-name :last-name :phone :email])
                                (set))
          unrendered-contacts (remove (fn [contact] (contains? rendered-contacts (dissoc contact :id))) contacts)]
      (every?
        (fn [contact] (not-any? #(string/includes? % search) (vals (dissoc contact :id))))
        unrendered-contacts))))

(defn- all-rendered-contacts-exist? [contacts request]
  (oracle/fixture
    (let [response ((app/handler contacts) request)
          rendered-contacts (mset/multiset (rendered-contacts response))]
      (set/subset? rendered-contacts
                   (set (map #(dissoc % :id) contacts))))))

(defspec contacts-that-match-search-are-rendered
  (for-all [[contacts request search] (generators/let [contacts (malli.generator/generator sut/schema)
                                                       string' (if (seq contacts)
                                                                 (generators/one-of
                                                                   [(->> contacts
                                                                         (mapcat vals)
                                                                         (generators/elements))
                                                                    generators/string-alphanumeric])
                                                                 generators/string-alphanumeric)
                                                       search (substring-generator string')
                                                       request (request-generator sut-path search)]
                                        [contacts request search])]
    (and (is (successful-response? contacts request))
         (is (returns-expected-data-type? contacts request))
         (is (all-contacts-that-match-are-rendered? contacts request search))
         (is (unmatched-contacts-are-not-rendered? contacts request search))
         (is (all-rendered-contacts-exist? contacts request)))))

(comment
  (contacts-integration-matches-oracle)
  (all-contacts-returned-when-no-query)
  (contacts-that-match-search-are-rendered)
  )
