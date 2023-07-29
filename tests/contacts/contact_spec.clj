(ns contacts.contact-spec
  (:require [clojure.string :as string]
            [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [contacts.lib.request :as request]
            [contacts.lib.test-system :as test-system]
            [contacts.storage :as storage]
            [malli.generator :as malli.generator]
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

(defspec retrieving-a-contact-displays-it
  (for-all [contacts (generators/such-that seq (malli.generator/generator storage/contacts-schema))
            contact (generators/elements contacts)
            request (request/generator (format sut-path-format (:id contact)))]
    (let [response (test-system/make-oracle-request contacts request)]
      (is (successfully-returns-html? response))
      (is (contact-is-rendered? contact response)))))

(defn- not-found-html? [{:keys [status headers]}]
  (is (= "text/html;charset=UTF-8" (:content-type headers)))
  (is (= 404 status)))

(defspec non-existent-contact-not-found
  (for-all [contacts (malli.generator/generator storage/contacts-schema)
            id (generators/such-that
                 (fn [id]
                   (and
                     (seq id)
                     (not (contains? (set (map :id contacts)) id))))
                 generators/string-alphanumeric)
            request (request/generator (format sut-path-format id))]
    (let [response (test-system/make-oracle-request contacts request)]
      (is (not-found-html? response)))))

(comment
  (retrieving-a-contact-displays-it)
  (non-existent-contact-not-found)
  )
