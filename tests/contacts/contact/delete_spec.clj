(ns contacts.contact.delete-spec
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

(def ^:private contacts-list-path "/contacts")

(def ^:private sut-path-format "/contacts/%s/delete")

(defn- deleting-contact-redirects-to-contact-list? [{:keys [status headers]}]
  (is (= 303 status))
  (is (= contacts-list-path (:location headers))))

(defn- deleted-contact-is-not-in-contacts-list? [contacts contact-id {:keys [status] :as response}]
  (is (= 200 status))
  (is (set (html/rendered-contacts response))
      (set (remove (fn [{:keys [id]}] (= contact-id id)) contacts))))

(defspec deleting-contact-deletes-contact-in-contacts-list
  (for-all [contacts (generators/such-that seq (malli.generator/generator contacts/schema))
            id (generators/fmap :id (generators/elements contacts))
            delete-contact-request (request/generator (format sut-path-format id)
                                                      {:request-method :post})
            contact-list-request (request/generator contacts-list-path)]
    (let [contacts-storage (app/init-contacts-storage contacts)
          delete-contact-response (test-system/make-real-request contacts-storage delete-contact-request)
          contact-list-response (test-system/make-real-request contacts-storage contact-list-request)]
      (deleting-contact-redirects-to-contact-list? delete-contact-response)
      (deleted-contact-is-not-in-contacts-list? contacts id contact-list-response))))

(defn- non-existent-contact-not-found? [{:keys [status]}]
  (is (= 404 status)))

(defspec deleting-non-existent-contact-fails
  (for-all [contacts (generators/such-that seq (malli.generator/generator contacts/schema))
            id (generators/such-that
                 (fn [id]
                   (and (seq id)
                        (not (contains? (set (map :id contacts)) id))))
                 generators/string-alphanumeric)
            request (request/generator (format sut-path-format id)
                                       {:request-method :post})]
    (let [response (test-system/make-oracle-request contacts request)]
      (non-existent-contact-not-found? response))))

(comment
  (deleting-contact-deletes-contact-in-contacts-list)
  (deleting-non-existent-contact-fails)
  )
