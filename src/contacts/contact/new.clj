(ns contacts.contact.new
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
  ([name label type place-holder] (input name label type place-holder nil nil))
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
  ([request] (render request nil nil))
  ([request contact errors]
   (page/render
     (:flash request)
     (list
       (form/form-to
         [:post "/contacts/new"]
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
       [:p [:a {:href "/contacts"} "Back"]]))))

;; HTTP Resource

(defn resource [default contacts-storage]
  (liberator/resource default
    :allowed-methods [:get :post]
    :malformed? (fn [{:keys [request] {:keys [request-method]} :request}]
                  (let [contact (:params request)]
                    (case request-method
                      :get false
                      :post (let [updates {:contact contact}]
                              (if (malli/validate schema contact)
                                [false updates]
                                [true (merge
                                        updates
                                        {:validation-errors (malli/explain schema contact)})])))))
    :post-redirect? true
    :location "/contacts"
    :post! (fn [{:keys [contact]}]
             (storage/create contacts-storage contact))
    :handle-see-other (representation/ring-response
                        {:flash "New Contact Created!"})
    :handle-malformed (fn [{:keys [request contact validation-errors]}]
                        (representation/ring-response
                          (render request contact (malli.error/humanize validation-errors))
                          {:headers {"Content-Type" "text/html"}}))
    :handle-ok (fn [{:keys [request]}]
                 (render request))))
