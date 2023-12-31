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
            [contacts.test-lib.users :as users]
            [idle.multiset.api :as mset]))

(def sut-path "/contacts")

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
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/non-empty-contacts-list-generator
                request (request/authorised-request-generator authorisation-id sut-path)]
    (let [response (-> authorisation-id
                       (test-system/construct-handler-for-users contacts)
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
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/contacts-list-generator
                handler (generators/return (test-system/construct-handler-for-users authorisation-id contacts))
                existing-contacts (contacts-list/existing-contacts-generator handler authorisation-id)
                string' (if (seq contacts)
                          (generators/one-of
                            [(->> existing-contacts
                                  (mapcat vals)
                                  (generators/elements))
                             generators/string-alphanumeric])
                          generators/string-alphanumeric)
                search (substring-generator string')
                request (request/authorised-request-generator authorisation-id
                                                              sut-path
                                                              {:query-params {:query search}})]
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
