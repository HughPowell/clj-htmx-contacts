(ns contacts.contact.edit-spec
  (:require [clojure.string :as string]
            [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [contacts.app :as app]
            [contacts.contacts :as contacts]
            [contacts.contact.edit :as sut]
            [contacts.lib.test-system :as test-system]
            [contacts.lib.html :as html]
            [contacts.lib.request :as request]
            [malli.core :as malli]
            [net.cgrand.enlive-html :as enlive]))

(def ^:private sut-path-format "/contacts/%s/edit")

(defn- contact-is-returned-as-html-ok? [{:keys [status headers]}]
  (is (= 200 status))
  (is (= "text/html;charset=UTF-8" (:content-type headers))))

(defn- form-contains-contact? [contact {:keys [body]}]
  (let [rendered-contact (->> (enlive/select body [[:input (enlive/attr? :value)]])
                              (map #(get-in % [:attrs :value]))
                              (map vector [:email :first-name :last-name :phone])
                              (into {}))
        actions (->> (enlive/select body [[:form (enlive/attr? :method)]])
                     (mapcat #(enlive/attr-values % :action)))]
    (is (= (dissoc contact :id) rendered-contact))
    (is (every? #(string/includes? % (:id contact)) actions))))

(defspec renders-an-editable-contact
  (for-all [contacts (generators/such-that seq (malli.generator/generator contacts/schema))
            contact (generators/elements contacts)
            request (request/generator (format sut-path-format (:id contact)))]
    (let [response (test-system/make-oracle-request contacts request)]
      (contact-is-returned-as-html-ok? response)
      (form-contains-contact? contact response))))

(comment
  (renders-an-editable-contact)
  )