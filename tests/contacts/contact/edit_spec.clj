(ns contacts.contact.edit-spec
  (:require [clojure.string :as string]
            [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [contacts.app :as app]
            [contacts.contact :as contact]
            [contacts.contacts :as contacts]
            [contacts.contact.edit :as sut]
            [contacts.lib.test-system :as test-system]
            [contacts.lib.html :as html]
            [contacts.lib.request :as request]
            [malli.core :as malli]
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

(defspec renders-an-editable-contact
  (for-all [contacts (generators/such-that seq (malli.generator/generator contacts/schema))
            contact (generators/elements contacts)
            request (request/generator (format sut-path-format (:id contact)))]
    (let [response (test-system/make-oracle-request contacts request)]
      (contact-is-returned-as-html-ok? response)
      (form-contains-contact? contact response))))


(defn- updating-contact-redirects-to-contact-list? [{:keys [status headers]}]
  (and (is (= 303 status))
       (is (= contacts-list-path (:location headers)))))

(defn- updated-contact-is-in-contacts-list? [contacts contact {:keys [status] :as response}]
  (and (is (= 200 status))
       (is (set (html/rendered-contacts response))
           (set (conj contacts contact)))))

(defspec updating-contact-updates-contact-in-contacts-list
  (for-all [contacts (generators/such-that seq (malli.generator/generator contacts/schema))
            id (generators/fmap :id (generators/elements contacts))
            new-contact-data (malli.generator/generator sut/schema)
            save-contact-request (request/generator (format sut-path-format id)
                                                    {:request-method :post
                                                     :form-params    new-contact-data})
            contact-list-request (request/generator contacts-list-path)]
    (let [contacts-storage (app/init-contacts-storage contacts)
          save-contact-response (test-system/make-real-request contacts-storage save-contact-request)
          contact-list-response (test-system/make-real-request contacts-storage contact-list-request)]
      (is (updating-contact-redirects-to-contact-list? save-contact-response))
      (is (updated-contact-is-in-contacts-list? contacts new-contact-data contact-list-response)))))


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

(defspec updating-contact-with-invalid-data-returns-to-editing-screen
  (for-all [contacts (generators/such-that seq (malli.generator/generator contacts/schema))
            id (generators/fmap :id (generators/elements contacts))
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
                                         (request/generator (format sut-path-format id)))]
    (let [invalid-contact-response (test-system/make-oracle-request contacts invalid-contact-request)]
      (saving-contact-results-in-client-error? invalid-contact-response)
      (original-data-is-displayed? invalid-contact invalid-contact-response)
      (errors-displayed-only-for-all-invalid-fields? invalid-contact invalid-contact-response))))

(defn- non-existent-contact-not-found? [{:keys [status]}]
  (is (= 404 status)))

(defspec updating-non-existent-contact-fails
  (for-all [contacts (generators/such-that seq (malli.generator/generator contacts/schema))
            id (generators/such-that
                 (fn [id]
                   (and (seq id)
                        (not (contains? (set (map :id contacts)) id))))
                 generators/string-alphanumeric)
            contact-data (malli.generator/generator contact/schema)
            request (request/generator (format sut-path-format id)
                                       {:request-method :post
                                        :form-params    contact-data})]
    (let [response (test-system/make-oracle-request contacts request)]
      (non-existent-contact-not-found? response))))

(comment
  (renders-an-editable-contact)
  (updating-contact-updates-contact-in-contacts-list)
  (updating-contact-with-invalid-data-returns-to-editing-screen)
  (updating-non-existent-contact-fails)
  )