(ns contacts.contacts.new
  (:require [contacts.contact :as contact]
            [contacts.page :as page]
            [contacts.request :as request]
            [hiccup.form :as form]
            [liberator.core :as liberator]
            [malli.core :as malli]
            [malli.error :as malli.error])
  (:import (java.io StringReader)
           (java.util UUID)))

;; Schemas

(def schema
  [:map
   contact/first-name
   contact/last-name
   contact/phone
   contact/email])

;; Rendering

(defn- input [name label type place-holder]
  [:p
   (form/label name label)
   [:input {:name name :id name :type type :placeholder place-holder}]
   [:span.error]])

(defn- render []
  (page/render
    (list
      (form/form-to [:post "/contacts/new"]
                    [:fieldset
                     [:legend "Contact Values"]
                     (input "email" "Email" "email" "Email")
                     (input "first-name" "First Name" "text" "First Name")
                     (input "last-name" "Last Name" "text" "Last Name")
                     (input "phone" "Phone" "text" "Phone")
                     [:button "Save"]])
      [:p [:a {:href "/contacts"} "Back"]])))

;; Persistence

(defn persist* [contacts-storage contact]
  (let [contact' (assoc contact :id (str (UUID/randomUUID)))]
    (swap! contacts-storage conj contact')
    contacts-storage))

(defn- persist [contacts-storage contact]
  (when-not (malli/validate schema contact)
    (let [explanation (malli/explain schema contact)]
      (throw (ex-info (malli.error/humanize explanation) explanation))))
  (persist* contacts-storage contact))

;; HTTP Resource

(defn resource [default contacts-storage]
  (liberator/resource default
                      :allowed-methods [:get :post]
                      :processable? (fn [{:keys [request] {:keys [request-method]} :request}]
                                      (let [request' (request/assoc-params request)
                                            contact (:params request')]
                                        (case request-method
                                          :get {:request request'}
                                          :post (if (malli/validate schema contact)
                                                  {:request request'
                                                   :contact contact}
                                                  [false
                                                   {:request           request'
                                                    :validation-errors (malli/explain schema contact)}]))))
                      :post-redirect? true
                      :location "/contacts"
                      :post! (fn [{:keys [contact]}]
                               (persist contacts-storage contact))
                      :handle-unprocessable-entity (fn [{:keys [request validation-errors]}])
                      :handle-exception (fn [ctx] (clojure.pprint/pprint ctx))
                      :handle-ok (fn [_]
                                   (render))))
