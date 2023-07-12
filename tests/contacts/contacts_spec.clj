(ns contacts.contacts-spec
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [contacts.contacts :as sut]
            [contacts.lib.test-system :as test-system]
            [contacts.lib.html :as html]
            [contacts.lib.request :as request]
            [idle.multiset.api :as mset]
            [malli.generator :as malli.generator]))

(def sut-path "/contacts")

(defn- request-generator
  ([path] (request/generator path))
  ([path search] (request-generator path search {}))
  ([path search opts]
   (->> {:query-params {:query search}}
        (merge opts)
        (request/generator path))))

(defn- substring-generator [s]
  (generators/let [start (generators/choose 0 (count s))
                   end (generators/choose start (count s))]
    (subs s start end)))

(defn- successful-response? [response]
  (is (= 200 (:status response))))

(defn- returns-expected-data-type? [response]
  (is (= "text/html;charset=UTF-8" (get-in response [:headers :content-type]))))

(defn- all-contacts-are-rendered? [contacts response]
  (is (= (mset/multiset (html/rendered-contacts response))
         (mset/multiset contacts))))

(defspec all-contacts-returned-when-no-query
  (for-all [contacts (malli.generator/generator sut/schema)
            request (request-generator sut-path)]
    (let [response (test-system/make-oracle-request contacts request)]
      (is (successful-response? response))
      (is (returns-expected-data-type? response))
      (is (all-contacts-are-rendered? contacts response)))))

(defn- all-contacts-that-match-are-rendered? [response search]
  (let [rendered-contacts (html/rendered-contacts response)]
    (is (every? (fn [contact]
                  (some #(string/includes? % search) (vals (dissoc contact :id))))
                rendered-contacts))))

(defn- unmatched-contacts-are-not-rendered? [contacts response search]
  (let [rendered-contacts (mset/multiset (html/rendered-contacts response))
        unrendered-contacts (remove (fn [contact] (contains? rendered-contacts contact)) contacts)]
    (is (every?
          (fn [contact] (not-any? #(string/includes? % search) (vals (dissoc contact :id))))
          unrendered-contacts))))

(defn- all-rendered-contacts-exist? [contacts response]
  (let [rendered-contacts (mset/multiset (html/rendered-contacts response))]
    (is (set/subset? rendered-contacts
                     (set contacts)))))

(defspec contacts-that-match-search-are-rendered
  (for-all [contacts (malli.generator/generator sut/schema)
            string' (if (seq contacts)
                      (generators/one-of
                        [(->> contacts
                              (mapcat vals)
                              (generators/elements))
                         generators/string-alphanumeric])
                      generators/string-alphanumeric)
            search (substring-generator string')
            request (request-generator sut-path search)]
    (let [response (test-system/make-oracle-request contacts request)]
      (is (successful-response? response))
      (is (returns-expected-data-type? response))
      (is (all-contacts-that-match-are-rendered? response search))
      (is (unmatched-contacts-are-not-rendered? contacts response search))
      (is (all-rendered-contacts-exist? contacts response)))))

(comment
  (all-contacts-returned-when-no-query)
  (contacts-that-match-search-are-rendered)
  )
