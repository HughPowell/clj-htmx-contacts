(ns contacts.contact.edit-spec
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.contact.edit :as sut]
            [contacts.test-lib.contacts-list :as contacts-list]
            [contacts.test-lib.html :as html]
            [contacts.test-lib.request :as request]
            [contacts.test-lib.test-system :as test-system]
            [contacts.system.contacts-storage :as contacts-storage]
            [contacts.test-lib.users :as users]
            [malli.core :as malli]
            [malli.generator :as malli.generator]
            [net.cgrand.enlive-html :as enlive]))

(def ^:private sut-path-format "/contacts/%s/edit")

(def ^:private contacts-list-path "/contacts")

(defn- contact-is-returned-as-html-ok? [{:keys [status headers]}]
  (is (= 200 status))
  (is (= "text/html;charset=UTF-8" (:content-type headers))))

(defn- form-contains-contact? [contact {:keys [body]}]
  (let [rendered-contact (->> (enlive/select body [[:input (enlive/attr? :value)]])
                              (map #(get-in % [:attrs :value]))
                              (map vector [:email :first-name :last-name :phone])
                              (into {}))
        actions (->> (enlive/select body [[:form (enlive/attr? :method)]])
                     (mapcat #(enlive/attr-values % :action)))]
    (is (= (dissoc contact :id) rendered-contact))
    (is (every? #(string/includes? % (:id contact)) actions))))

(deftest renders-an-editable-contact
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/non-empty-contacts-list-generator
                handler (generators/return (test-system/construct-handler-for-user authorisation-id contacts))
                contact-to-update (contacts-list/nth-contact-generator handler authorisation-id)
                update-request (request/authorised-request-generator authorisation-id
                                                                     (format sut-path-format (:id contact-to-update)))]
    (let [response (test-system/make-request handler update-request)]
      (contact-is-returned-as-html-ok? response)
      (form-contains-contact? contact-to-update response))))


(defn- updating-contact-redirects-to-contacts-list? [{:keys [status headers]}]
  (and (is (= 303 status))
       (is (= contacts-list-path (:location headers)))))

(defn- updated-contact-is-in-contacts-list? [contacts contact {:keys [status] :as response}]
  (and (is (= 200 status))
       (is (set (html/rendered-contacts response))
           (set (conj contacts contact)))))

(deftest updating-contact-updates-contact-in-contacts-list
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/non-empty-contacts-list-generator
                handler (generators/return (test-system/construct-handler-for-user authorisation-id contacts))
                contact-to-update (contacts-list/nth-contact-generator handler authorisation-id)
                new-contact-data (malli.generator/generator sut/schema)
                save-contact-request (request/authorised-request-generator
                                       authorisation-id
                                       (format sut-path-format (:id contact-to-update))
                                       {:request-method :post
                                        :form-params    new-contact-data})
                contacts-list-request (request/authorised-request-generator authorisation-id contacts-list-path)]
    (let [save-contact-response (test-system/make-request handler save-contact-request)
          contacts-list-response (test-system/make-request handler contacts-list-request)]
      (is (updating-contact-redirects-to-contacts-list? save-contact-response))
      (is (updated-contact-is-in-contacts-list? contacts
                                                (merge contact-to-update new-contact-data)
                                                contacts-list-response)))))


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

(deftest updating-contact-with-invalid-data-returns-to-editing-screen
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/non-empty-contacts-list-generator
                handler (generators/return (test-system/construct-handler-for-user authorisation-id contacts))
                contact-to-update (contacts-list/nth-contact-generator handler authorisation-id)
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
                                             (request/authorised-request-generator
                                               authorisation-id
                                               (format sut-path-format (:id contact-to-update))))]
    (let [invalid-contact-response (test-system/make-request handler invalid-contact-request)]
      (saving-contact-results-in-client-error? invalid-contact-response)
      (original-data-is-displayed? invalid-contact invalid-contact-response)
      (errors-displayed-only-for-all-invalid-fields? invalid-contact invalid-contact-response))))

(defn- non-existent-contact-not-found? [{:keys [status]}]
  (is (= 404 status)))

(deftest updating-non-existent-contact-fails
  (checking "" [authorisation-id users/authorisation-id-generator
                contacts contacts-list/non-empty-contacts-list-generator
                handler (generators/return (test-system/construct-handler-for-user authorisation-id contacts))
                existing-contacts (contacts-list/existing-contacts-generator handler authorisation-id)
                id (generators/such-that
                     (fn [id]
                       (and (seq id)
                            (not (contains? (set (map :id existing-contacts)) id))))
                     generators/string-alphanumeric)
                contact-data (malli.generator/generator contacts-storage/existing-contact-schema)
                request (request/authorised-request-generator authorisation-id
                                                              (format sut-path-format id)
                                                              {:request-method :post
                                                               :form-params    contact-data})]
    (let [response (test-system/make-request handler request)]
      (non-existent-contact-not-found? response))))

(deftest updating-other-users-contact-fails
  (checking "" [authorisation-ids users/two-plus-authorisation-ids-generator
                owner-authorisation-id (generators/elements authorisation-ids)
                accessor-authorisation-id (generators/elements (disj (set authorisation-ids) owner-authorisation-id))
                contacts (generators/vector contacts-list/non-empty-contacts-list-generator (count authorisation-ids))
                handler (generators/return (test-system/construct-handler-for-users authorisation-ids contacts))
                owners-contacts (contacts-list/existing-contacts-generator handler owner-authorisation-id)
                owners-contact (generators/elements owners-contacts)
                contact-data (malli.generator/generator contacts-storage/existing-contact-schema)
                update-request (request/authorised-request-generator accessor-authorisation-id
                                                                     (format sut-path-format (:id owners-contact))
                                                                     {:request-method :post
                                                                      :form-params    contact-data})]
    (let [response (test-system/make-request handler update-request)]
      (is (non-existent-contact-not-found? response)))))

(comment
  (renders-an-editable-contact)
  (updating-contact-updates-contact-in-contacts-list)
  (updating-contact-with-invalid-data-returns-to-editing-screen)
  (updating-non-existent-contact-fails)
  (updating-other-users-contact-fails)
  )
