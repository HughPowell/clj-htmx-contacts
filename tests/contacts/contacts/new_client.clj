(ns contacts.contacts.new-client
  (:require [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [clojure.test.check.properties :refer [for-all]]
            [contacts.app :as app]
            [contacts.contacts :as contacts]
            [contacts.contacts.new :as sut]
            [contacts.lib.test-system :as test-system]
            [contacts.lib.html :as html]
            [contacts.lib.request :as request]
            [net.cgrand.enlive-html :as enlive]))

(defn- new-contact-form-is-returned-ok? [contacts request]
  (let [{:keys [status]} (test-system/make-request contacts request)]
    (is (= 200 status))))

(defn- new-form-is-empty? [contacts request]
  (let [inputs-with-values (-> (test-system/make-request contacts request)
                               (:body)
                               (enlive/html-snippet)
                               (enlive/select [[:input (enlive/attr? :value)]]))]
    (is (empty? inputs-with-values))))

(defspec getting-a-new-contact-form-provides-an-empty-form
  (for-all [contacts (malli.generator/generator contacts/schema)
            request (request/generator "/contacts/new")]
    (and (is (new-contact-form-is-returned-ok? contacts request))
         (is (new-form-is-empty? contacts request)))))

(defn- saving-contact-redirects-to-contact-list? [{:keys [status headers]}]
  (and (is (= 303 status))
       (is (= "/contacts" (:location headers)))))
(defn- saved-contact-is-in-contacts-list? [contacts contact {:keys [status body]}]
  (let [snippet (enlive/html-snippet body)]
    (and (is (= 200 status))
         (is (set (html/table->map snippet [:first-name :last-name :phone :email]))
             (set (map #(dissoc % :id) (conj contacts contact)))))))

(defspec adding-new-contact-adds-contact-to-contacts-list
  (for-all [contacts (malli.generator/generator contacts/schema)
            [contact save-contact-request contact-list-request]
            (generators/let [contact (malli.generator/generator sut/schema)
                             save-contact-request (request/generator "/contacts/new"
                                                                     {:request-method :post
                                                                      :form-params    contact})
                             contact-list-request (request/generator "/contacts")]
              [contact save-contact-request contact-list-request])]
    (let [app (app/handler (contacts.app/init-contacts-storage contacts))
          save-contact-response (test-system/keyword-headers (app save-contact-request))
          contact-list-response (app contact-list-request)]
      (and
        (is (saving-contact-redirects-to-contact-list? save-contact-response))
        (is (saved-contact-is-in-contacts-list? contacts contact contact-list-response))))))

(comment
  (getting-a-new-contact-form-provides-an-empty-form)
  (adding-new-contact-adds-contact-to-contacts-list)
  )
