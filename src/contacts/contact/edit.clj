(ns contacts.contact.edit
  (:require [clojure.string :as string]
            [contacts.contact.schemas :as schemas]
            [contacts.page :as page]
            [contacts.storage :as storage]
            [hiccup.form :as form]
            [liberator.core :as liberator]
            [liberator.representation :as representation]
            [malli.core :as malli]
            [malli.error :as malli.error]))

;; Schemas

(def schema
  [:map
   schemas/first-name
   schemas/last-name
   schemas/phone
   schemas/email])

;; Rendering

(defn- input
  ([name label type place-holder value] (input name label type place-holder value nil))
  ([name label type place-holder value error]
   [:p
    (form/label name label)
    [:input {:name name :id name :type type :placeholder place-holder :value value}]
    [:span.error error]]))

(defn- ->human-readable-option-list [option-list]
  (let [suffix (string/join " or " (take-last 2 option-list))]
    (string/join ", " (conj (vec (drop-last 2 option-list)) suffix))))

(defn- render
  ([ctx contact] (render ctx contact nil))
  ([ctx contact errors]
   (page/render
     ctx
     (list
       (form/form-to
         [:post (format "/contacts/%s/edit" (:id contact))]
         [:fieldset
          [:legend "Contact Values"]
          (input "email" "Email" "email" "Email" (:email contact)
                 (when-let [errors (:email errors)]
                   (format "The email address %s." (->human-readable-option-list errors))))
          (input "first-name" "First Name" "text" "First Name" (:first-name contact))
          (input "last-name" "Last Name" "text" "Last Name" (:last-name contact))
          (input "phone" "Phone" "text" "Phone" (:phone contact)
                 (when-let [errors (:phone errors)]
                   (format "The number %s." (->human-readable-option-list errors))))
          [:button "Save"]])
       (form/form-to
         [:post (format "/contacts/%s/delete" (:id contact))]
         [:button "Delete"])
       [:p [:a {:href "/contacts"} "Back"]]))))

;; HTTP Resource

(defn resource [default contacts-storage]
  (liberator/resource default
    :allowed-methods [:get :post]
    :malformed? (fn [{:keys [request] {:keys [request-method]} :request}]
                  (let [contact (:params request)]
                    (case request-method
                      :get false
                      :post (let [updates {:new-contact contact}]
                              (if (malli/validate schema contact)
                                [false updates]
                                [true (merge
                                        updates
                                        {:validation-errors (malli/explain schema contact)})])))))
    :exists? (fn [{:keys [request]}]
               (if-let [contact (storage/retrieve contacts-storage (get-in request [:params :id]))]
                 [true {:original-contact contact}]
                 false))
    :can-post-to-missing? false
    :post! (fn [{:keys [new-contact]}]
             (storage/update contacts-storage new-contact))
    :post-redirect? true
    :location "/contacts"
    :handle-see-other (representation/ring-response
                        {:flash "Updated Contact!"})
    :handle-malformed (fn [{:keys [new-contact validation-errors] :as ctx}]
                        (representation/ring-response
                          (render ctx new-contact (malli.error/humanize validation-errors))
                          {:headers {"Content-Type" "text/html"}}))
    :handle-ok (fn [{:keys [original-contact] :as ctx}]
                 (render ctx original-contact))))
