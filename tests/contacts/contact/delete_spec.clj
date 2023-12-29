(ns contacts.contact.delete-spec
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.test-lib.contact-list :as contact-list]
            [contacts.test-lib.test-system :as test-system]
            [contacts.test-lib.html :as html]
            [contacts.test-lib.request :as request]
            [contacts.system.contacts-storage :as contacts-storage]
            [malli.generator :as malli.generator]))

(def ^:private contacts-list-path "/contacts")

(def ^:private sut-path-format "/contacts/%s/delete")

(defn- deleting-contact-redirects-to-contact-list? [{:keys [status headers]}]
  (is (= 303 status))
  (is (= contacts-list-path (:location headers))))

(defn- deleted-contact-is-not-in-contacts-list? [contacts contact-id {:keys [status] :as response}]
  (is (= 200 status))
  (is (set (html/rendered-contacts response))
      (set (remove (fn [{:keys [id]}] (= contact-id id)) contacts))))

(deftest deleting-contact-deletes-contact-in-contacts-list
  (checking "" [contacts (generators/such-that seq (malli.generator/generator contacts-storage/contacts-schema))
                handler (generators/return (test-system/construct-handler contacts))
                contact-list-request (request/generator contacts-list-path)
                contact-to-delete (contact-list/nth-contact-generator handler)
                delete-contact-request (request/generator (format sut-path-format (:id contact-to-delete))
                                                          {:request-method :post})]
    (let [delete-contact-response (test-system/make-request handler delete-contact-request)
          contact-list-response (test-system/make-request handler contact-list-request)]
      (deleting-contact-redirects-to-contact-list? delete-contact-response)
      (deleted-contact-is-not-in-contacts-list? contacts (:id contact-to-delete) contact-list-response))))

(defn- non-existent-contact-not-found? [{:keys [status]}]
  (is (= 404 status)))

(deftest deleting-non-existent-contact-fails
  (checking "" [contacts (generators/such-that seq (malli.generator/generator contacts-storage/contacts-schema))
                handler (generators/return (test-system/construct-handler contacts))
                contacts-request (request/generator contacts-list-path)
                existing-contacts (contact-list/existing-contacts-generator handler)
                contact-ids (generators/return (set (map :id existing-contacts)))
                id (generators/such-that
                     (fn [id]
                       (and (seq id)
                            (not (contains? contact-ids id))))
                     generators/string-alphanumeric)
                delete-request (request/generator (format sut-path-format id)
                                                  {:request-method :post})]
    (let [response (test-system/make-request handler delete-request)]
      (non-existent-contact-not-found? response))))

(comment
  (deleting-contact-deletes-contact-in-contacts-list)
  (deleting-non-existent-contact-fails)
  )
