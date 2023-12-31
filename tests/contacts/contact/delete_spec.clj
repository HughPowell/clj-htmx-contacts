(ns contacts.contact.delete-spec
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.test-lib.contacts-list :as contacts-list]
            [contacts.test-lib.test-system :as test-system]
            [contacts.test-lib.html :as html]
            [contacts.test-lib.request :as request]
            [contacts.test-lib.users :as users]))

(def ^:private contacts-list-path "/contacts")

(def ^:private sut-path-format "/contacts/%s/delete")

(defn- deleting-contact-redirects-to-contacts-list? [{:keys [status headers]}]
  (is (= 303 status))
  (is (= contacts-list-path (:location headers))))

(defn- deleted-contact-is-not-in-contacts-list? [contacts contact-id {:keys [status] :as response}]
  (is (= 200 status))
  (is (set (html/rendered-contacts response))
      (set (remove (fn [{:keys [id]}] (= contact-id id)) contacts))))

(deftest deleting-contact-deletes-contact-in-contacts-list
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/non-empty-contacts-list-generator
                handler (generators/return (test-system/construct-handler-for-users authorisation-id contacts))
                contacts-list-request (request/authorised-request-generator authorisation-id contacts-list-path)
                contact-to-delete (contacts-list/nth-contact-generator handler authorisation-id)
                delete-contact-request (request/authorised-request-generator authorisation-id
                                                                             (format sut-path-format
                                                                                     (:id contact-to-delete))
                                                                             {:request-method :post})]
    (let [delete-contact-response (test-system/make-request handler delete-contact-request)
          contacts-list-response (test-system/make-request handler contacts-list-request)]
      (deleting-contact-redirects-to-contacts-list? delete-contact-response)
      (deleted-contact-is-not-in-contacts-list? contacts (:id contact-to-delete) contacts-list-response))))

(defn- non-existent-contact-not-found? [{:keys [status]}]
  (is (= 404 status)))

(deftest deleting-non-existent-contact-fails
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/non-empty-contacts-list-generator
                handler (generators/return (test-system/construct-handler-for-users authorisation-id contacts))
                contacts-request (request/authorised-request-generator authorisation-id contacts-list-path)
                existing-contacts (contacts-list/existing-contacts-generator handler authorisation-id)
                contact-ids (generators/return (set (map :id existing-contacts)))
                id (generators/such-that
                     (fn [id]
                       (and (seq id)
                            (not (contains? contact-ids id))))
                     generators/string-alphanumeric)
                delete-request (request/authorised-request-generator authorisation-id
                                                                     (format sut-path-format id)
                                                                     {:request-method :post})]
    (let [response (test-system/make-request handler delete-request)]
      (non-existent-contact-not-found? response))))

;; TODO Make sure users can't delete others contacts

(comment
  (deleting-contact-deletes-contact-in-contacts-list)
  (deleting-non-existent-contact-fails)
  )
