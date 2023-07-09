(ns contacts.contacts.new-client
  (:require [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [clojure.test.check.properties :refer [for-all]]
            [contacts.app :as app]
            [contacts.contacts :as contacts]
            [contacts.contacts.new :as new]
            [contacts.contacts.new :as sut]
            [contacts.lib.test-system :as test-system]
            [contacts.lib.html :as html]
            [contacts.lib.request :as request]
            [malli.core :as malli]
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

(defn- saving-contact-results-in-client-error? [{:keys [status]}]
  (is (= 400 status)))

(defn- original-data-is-displayed? [contact invalid-contact-response]
  (let [input-tags (-> invalid-contact-response
                       (:body)
                       (enlive/html-snippet)
                       (enlive/select [:fieldset :input]))
        rendered-contact (->> input-tags
                              (map (fn [k tag] [k (get-in tag [:attrs :value])])
                                   [:email :first-name :last-name :phone])
                              (into {}))]
    (is (= contact rendered-contact))))

(defn- errors-displayed-only-for-invalid-fields? [invalid-contact invalid-contact-response]
  (let [input-and-error-tags (-> invalid-contact-response
                                 (:body)
                                 (enlive/html-snippet)
                                 (enlive/select [:fieldset #{:input :span.error}]))
        error-ids (->> invalid-contact
                       (malli/explain new/schema)
                       (:errors)
                       (map (comp first :in))
                       (set))
        id->error (->> input-and-error-tags
                       (partition 2)
                       (map (fn [[{{:keys [id]} :attrs} error]]
                              [(keyword id) (not-empty (enlive/text error))]))
                       (into {}))]
    (is (every? id->error error-ids))
    (is (every? nil? (vals (apply dissoc id->error error-ids))))))

(defspec adding-invalid-contact-returns-to-editing-screen
  (for-all [contacts (malli.generator/generator contacts/schema)
            [invalid-contact invalid-contact-request]
            (generators/let [invalid-contact (->> [:map
                                                   [:first-name :string]
                                                   [:last-name :string]
                                                   [:phone :string]
                                                   [:email :string]]
                                                  (malli.generator/generator)
                                                  (generators/such-that
                                                    (fn [contact] (not (malli/validate new/schema contact)))))
                             invalid-contact-request (->> invalid-contact
                                                          (hash-map :request-method :post :form-params)
                                                          (request/generator "/contacts/new"))]
              [invalid-contact invalid-contact-request])]
    (let [invalid-contact-response (test-system/make-request contacts invalid-contact-request)]
      (and
        (is (saving-contact-results-in-client-error? invalid-contact-response))
        (is (original-data-is-displayed? invalid-contact invalid-contact-response))
        (is (errors-displayed-only-for-invalid-fields? invalid-contact invalid-contact-response))))))

(comment
  (getting-a-new-contact-form-provides-an-empty-form)
  (adding-new-contact-adds-contact-to-contacts-list)
  (adding-invalid-contact-returns-to-editing-screen)
  )
