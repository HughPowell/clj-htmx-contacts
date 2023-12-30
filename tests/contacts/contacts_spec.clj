(ns contacts.contacts-spec
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.test-lib.contacts-list :as contacts-list]
            [contacts.test-lib.test-system :as test-system]
            [contacts.test-lib.html :as html]
            [contacts.test-lib.request :as request]
            [contacts.system.contacts-storage :as contacts-storage]
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
  (= 200 (:status response)))

(defn- returns-expected-data-type? [response]
  (= "text/html;charset=UTF-8" (get-in response [:headers :content-type])))

(defn- all-contacts-are-rendered? [contacts response]
  (= (mset/multiset (contacts-list/strip-ids (html/rendered-contacts response)))
     (mset/multiset contacts)))

(deftest all-contacts-returned-when-no-query
  (checking "" [contacts (generators/such-that seq (contacts-list/new-contacts-generator))
                request (request-generator sut-path)]
    (let [response (-> contacts
                       (test-system/construct-handler)
                       (test-system/make-request request))]
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

(deftest contacts-that-match-search-are-rendered
  (checking "" [contacts (malli.generator/generator contacts-storage/contacts-schema)
                handler (generators/return (test-system/construct-handler contacts))
                existing-contacts (contacts-list/existing-contacts-generator handler)
                string' (if (seq contacts)
                          (generators/one-of
                            [(->> existing-contacts
                                  (mapcat vals)
                                  (generators/elements))
                             generators/string-alphanumeric])
                          generators/string-alphanumeric)
                search (substring-generator string')
                request (request-generator sut-path search)]
    (let [response (test-system/make-request handler request)]
      (is (successful-response? response))
      (is (returns-expected-data-type? response))
      (is (all-contacts-that-match-are-rendered? response search))
      (is (unmatched-contacts-are-not-rendered? existing-contacts response search))
      (is (all-rendered-contacts-exist? existing-contacts response)))))

(comment
  (all-contacts-returned-when-no-query)
  (contacts-that-match-search-are-rendered)
  )
