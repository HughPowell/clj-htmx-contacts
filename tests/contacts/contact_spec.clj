(ns contacts.contact-spec
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.test-lib.contacts-list :as contacts-list]
            [contacts.test-lib.request :as request]
            [contacts.test-lib.test-system :as test-system]
            [contacts.test-lib.users :as users]
            [net.cgrand.enlive-html :as enlive]))

(def ^:private sut-path-format "/contacts/%s")

(defn- successfully-returns-html? [{:keys [status headers]}]
  (is (= 200 status))
  (is (= "text/html;charset=UTF-8" (:content-type headers))))

(defn- contact-is-rendered? [contact {:keys [body]}]
  (let [full-name (str (first (enlive/select body [:main :> :h1 enlive/text])))
        [phone-number email] (map (fn [s] (str (second (string/split s #": "))))
                                  (enlive/select body [:main :> :div :> :div enlive/text]))
        edit-link (get-in (first (enlive/select body [:main :p :> :a])) [:attrs :href])]
    (is (string/starts-with? full-name (:first-name contact)))
    (is (string/ends-with? full-name (:last-name contact)))
    (is (= (:phone contact) phone-number))
    (is (= (:email contact) email))
    (is (string/includes? edit-link (:id contact)))))

(deftest retrieving-a-contact-displays-it
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/non-empty-contacts-list-generator
                handler (generators/return (test-system/construct-handler-for-user authorisation-id contacts))
                contact (contacts-list/nth-contact-generator handler authorisation-id)
                request (request/generator authorisation-id (format sut-path-format (:id contact)))]
            (let [response (test-system/make-request handler request)]
              (is (successfully-returns-html? response))
              (is (contact-is-rendered? contact response)))))

(defn- not-found-html? [{:keys [status headers]}]
  (is (= "text/html;charset=UTF-8" (:content-type headers)))
  (is (= 404 status)))

(deftest non-existent-contact-not-found
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/contacts-list-generator
                handler (generators/return (test-system/construct-handler-for-user authorisation-id contacts))
                existing-contacts (contacts-list/existing-contacts-generator handler authorisation-id)
                id (generators/such-that
                     (fn [id]
                       (and
                         (seq id)
                         (not (contains? (set (map :id existing-contacts)) id))))
                     generators/string-alphanumeric)
                request (request/generator authorisation-id (format sut-path-format id))]
            (let [response (test-system/make-request handler request)]
              (is (not-found-html? response)))))

(deftest other-users-contact-not-found
  (checking "" [authorisation-ids users/two-plus-authorisation-ids-generator
                owner-authorisation-id (generators/elements authorisation-ids)
                accessor-authorisation-id (generators/elements (disj (set authorisation-ids) owner-authorisation-id))
                contacts (generators/vector contacts-list/non-empty-contacts-list-generator (count authorisation-ids))
                handler (generators/return (test-system/construct-handler-for-users authorisation-ids contacts))
                owners-contacts (contacts-list/existing-contacts-generator handler owner-authorisation-id)
                owners-contact (generators/elements owners-contacts)
                request (request/generator accessor-authorisation-id
                                           (format sut-path-format (:id owners-contact)))]
            (let [response (test-system/make-request handler request)]
              (is (not-found-html? response)))))

(comment
  (retrieving-a-contact-displays-it)
  (non-existent-contact-not-found)
  (other-users-contact-not-found))
