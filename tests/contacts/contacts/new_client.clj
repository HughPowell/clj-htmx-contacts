(ns contacts.contacts.new-client
  (:require [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [clojure.test.check.properties :refer [for-all]]
            [contacts.app :as app]
            [contacts.lib.request :as request]
            [net.cgrand.enlive-html :as enlive]))

(defn- new-contact-form-is-returned-ok? [contacts request]
  (let [{:keys [status]} ((app/handler contacts) request)]
    (= 200 status)))

(defn- new-form-is-empty? [contacts request]
  (let [inputs-with-values (-> ((app/handler contacts) request)
                               (:body)
                               (enlive/html-snippet)
                               (enlive/select [[:input (enlive/attr? :value)]]))]
    (empty? inputs-with-values)))

(defspec getting-a-new-contact-form-provides-an-empty-form
  (for-all [request (request/generator "/contacts/new")]
    (let [contacts #{}]
      (and (is (new-contact-form-is-returned-ok? contacts request))
           (is (new-form-is-empty? contacts request))))))

(comment
  (getting-a-new-contact-form-provides-an-empty-form)
  )
