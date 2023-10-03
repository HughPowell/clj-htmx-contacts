(ns contacts.contact.delete-spec
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.test-lib.test-system :as test-system]
            [contacts.test-lib.html :as html]
            [contacts.test-lib.request :as request]
            [contacts.system.storage :as storage]
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
  (checking "" [contacts (generators/such-that seq (malli.generator/generator storage/contacts-schema))
                id (generators/fmap :id (generators/elements contacts))
                delete-contact-request (request/generator (format sut-path-format id)
                                                          {:request-method :post})
                contact-list-request (request/generator contacts-list-path)]
    (let [handler (test-system/construct-handler contacts)
          delete-contact-response (test-system/make-request handler delete-contact-request)
          contact-list-response (test-system/make-request handler contact-list-request)]
      (deleting-contact-redirects-to-contact-list? delete-contact-response)
      (deleted-contact-is-not-in-contacts-list? contacts id contact-list-response))))

(defn- non-existent-contact-not-found? [{:keys [status]}]
  (is (= 404 status)))

(deftest deleting-non-existent-contact-fails
  (checking "" [contacts (generators/such-that seq (malli.generator/generator storage/contacts-schema))
                id (generators/such-that
                     (fn [id]
                       (and (seq id)
                            (not (contains? (set (map :id contacts)) id))))
                     generators/string-alphanumeric)
                request (request/generator (format sut-path-format id)
                                           {:request-method :post})]
    (let [response (-> contacts
                       (test-system/construct-handler)
                       (test-system/make-request request))]
      (non-existent-contact-not-found? response))))

(comment
  (deleting-contact-deletes-contact-in-contacts-list)
  (deleting-non-existent-contact-fails)
  )
