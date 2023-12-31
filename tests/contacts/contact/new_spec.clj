(ns contacts.contact.new-spec
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.contact.new :as sut]
            [contacts.test-lib.contacts-list :as contacts-list]
            [contacts.test-lib.test-system :as test-system]
            [contacts.test-lib.html :as html]
            [contacts.test-lib.request :as request]
            [contacts.test-lib.users :as users]
            [malli.core :as malli]
            [malli.generator :as malli.generator]
            [net.cgrand.enlive-html :as enlive]))

(def ^:private sut-path "/contacts/new")
(def ^:private contacts-list-path "/contacts")

(defn- new-contact-form-is-returned-ok? [{:keys [status]}]
  (is (= 200 status)))

(defn- new-form-is-empty? [response]
  (let [inputs-with-values (-> response
                               (:body)
                               (enlive/select [[:input (enlive/attr? :value)]]))]
    (is (empty? inputs-with-values))))

(deftest getting-a-new-contact-form-provides-an-empty-form
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/contacts-list-generator
                request (request/generator authorisation-id sut-path)]
    (let [response (-> authorisation-id
                       (test-system/construct-handler-for-user contacts)
                       (test-system/make-request request))]
      (and (is (new-contact-form-is-returned-ok? response))
           (is (new-form-is-empty? response))))))

(defn- saving-contact-redirects-to-contacts-list? [{:keys [status headers]}]
  (and (is (= 303 status))
       (is (= contacts-list-path (:location headers)))))

(defn- saved-contact-is-in-contacts-list? [contacts contact {:keys [status] :as response}]
  (and (is (= 200 status))
       (is (set (html/rendered-contacts response))
           (set (conj contacts contact)))))

(deftest adding-new-contact-adds-contact-to-contacts-list
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/contacts-list-generator
                contact (malli.generator/generator sut/schema)
                save-contact-request (request/generator authorisation-id
                                                        sut-path
                                                        {:request-method :post
                                                                            :form-params    contact})
                contacts-list-request (request/generator authorisation-id contacts-list-path)]
    (let [handler (test-system/construct-handler-for-user authorisation-id contacts)
          save-contact-response (test-system/make-request handler save-contact-request)
          contacts-list-response (test-system/make-request handler contacts-list-request)]
      (is (saving-contact-redirects-to-contacts-list? save-contact-response))
      (is (saved-contact-is-in-contacts-list? contacts contact contacts-list-response)))))

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

(deftest adding-invalid-contact-returns-to-editing-screen
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/contacts-list-generator
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
                                             (request/generator authorisation-id sut-path))]
    (let [invalid-contact-response (-> authorisation-id
                                       (test-system/construct-handler-for-user contacts)
                                       (test-system/make-request invalid-contact-request))]
      (saving-contact-results-in-client-error? invalid-contact-response)
      (original-data-is-displayed? invalid-contact invalid-contact-response)
      (errors-displayed-only-for-all-invalid-fields? invalid-contact invalid-contact-response))))

(comment
  (getting-a-new-contact-form-provides-an-empty-form)
  (adding-new-contact-adds-contact-to-contacts-list)
  (adding-invalid-contact-returns-to-editing-screen)
  )
