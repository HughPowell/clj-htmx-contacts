(ns contacts.contact.new-spec
  (:require [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [contacts.app :as app]
            [contacts.contact.new :as sut]
            [contacts.contacts :as contacts]
            [contacts.lib.test-system :as test-system]
            [contacts.lib.html :as html]
            [contacts.lib.request :as request]
            [malli.core :as malli]
            [net.cgrand.enlive-html :as enlive]))

(def ^:private sut-path "/contacts/new")
(def ^:private contact-list-path "/contacts")

(defn- new-contact-form-is-returned-ok? [{:keys [status]}]
  (is (= 200 status)))

(defn- new-form-is-empty? [response]
  (let [inputs-with-values (-> response
                               (:body)
                               (enlive/select [[:input (enlive/attr? :value)]]))]
    (is (empty? inputs-with-values))))

(defspec getting-a-new-contact-form-provides-an-empty-form
  (for-all [contacts (malli.generator/generator contacts/schema)
            request (request/generator sut-path)]
    (let [response (test-system/make-oracle-request contacts request)]
      (and (is (new-contact-form-is-returned-ok? response))
           (is (new-form-is-empty? response))))))

(defn- saving-contact-redirects-to-contact-list? [{:keys [status headers]}]
  (and (is (= 303 status))
       (is (= contact-list-path (:location headers)))))

(defn- saved-contact-is-in-contacts-list? [contacts contact {:keys [status] :as response}]
  (and (is (= 200 status))
       (is (set (html/rendered-contacts response))
           (set (conj contacts contact)))))

(defspec adding-new-contact-adds-contact-to-contacts-list
  (for-all [contacts (malli.generator/generator contacts/schema)
            contact (malli.generator/generator sut/schema)
            save-contact-request (request/generator sut-path
                                                    {:request-method :post
                                                     :form-params    contact})
            contact-list-request (request/generator contact-list-path)]
    (let [contacts-storage (app/init-contacts-storage contacts)
          save-contact-response (test-system/make-real-request contacts-storage save-contact-request)
          contact-list-response (test-system/make-real-request contacts-storage contact-list-request)]
      (is (saving-contact-redirects-to-contact-list? save-contact-response))
      (is (saved-contact-is-in-contacts-list? contacts contact contact-list-response)))))

(defn- saving-contact-results-in-client-error? [{:keys [status]}]
  (is (= 400 status)))

(defn- original-data-is-displayed? [contact invalid-contact-response]
  (let [input-tags (-> invalid-contact-response
                       (:body)
                       (enlive/select [:fieldset :input]))
        rendered-contact (->> input-tags
                              (map (fn [k tag] [k (get-in tag [:attrs :value])])
                                   [:email :first-name :last-name :phone])
                              (into {}))]
    (is (= contact rendered-contact))))

(defn- errors-displayed-only-for-all-invalid-fields? [invalid-contact {:keys [body]}]
  (let [id->error (->> (enlive/select body [:fieldset #{:input :span.error}])
                       (partition 2)
                       (map (fn [[{{:keys [id]} :attrs} error]]
                              [(keyword id) (not-empty (enlive/text error))]))
                       (into {}))
        error-ids (->> invalid-contact
                       (malli/explain sut/schema)
                       (:errors)
                       (map (comp first :in))
                       (set))]
    (is (every? id->error error-ids))
    (is (every? nil? (vals (apply dissoc id->error error-ids))))))

(defspec adding-invalid-contact-returns-to-editing-screen
  (for-all [contacts (malli.generator/generator contacts/schema)
            invalid-contact (->> [:map
                                  [:first-name :string]
                                  [:last-name :string]
                                  [:phone :string]
                                  [:email :string]]
                                 (malli.generator/generator)
                                 (generators/such-that
                                   (fn [contact] (not (malli/validate sut/schema contact)))))
            invalid-contact-request (->> invalid-contact
                                         (hash-map :request-method :post :form-params)
                                         (request/generator sut-path))]
    (let [invalid-contact-response (test-system/make-oracle-request contacts invalid-contact-request)]
      (saving-contact-results-in-client-error? invalid-contact-response)
      (original-data-is-displayed? invalid-contact invalid-contact-response)
      (errors-displayed-only-for-all-invalid-fields? invalid-contact invalid-contact-response))))

(comment
  (getting-a-new-contact-form-provides-an-empty-form)
  (adding-new-contact-adds-contact-to-contacts-list)
  (adding-invalid-contact-returns-to-editing-screen)
  )
