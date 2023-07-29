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
  ([request contact] (render request contact nil))
  ([request contact errors]
   (page/render
     (:flash request)
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

;; Persistence

(defn retrieve* [contacts-storage id]
  (first (get (group-by :id @contacts-storage) id)))

(defn- retrieve [contacts-storage id]
  (let [contact (retrieve* contacts-storage id)]
    (when (malli/validate storage/contact-schema contact)
      contact)))

(defn persist* [contacts-storage contact]
  (let [replace (fn [contacts contact]
                  (set (map
                         (fn [{:keys [id] :as contact'}]
                           (if (= id (:id contact))
                             contact
                             contact'))
                         contacts)))]
    (swap! contacts-storage replace contact))
  contacts-storage)

(defn- persist [contacts-storage contact]
  (when-not (malli/validate schema contact)
    (let [explanation (malli/explain schema contact)]
      (throw (ex-info (malli.error/humanize explanation) explanation))))
  (persist* contacts-storage contact))

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
               (if-let [contact (retrieve contacts-storage (get-in request [:params :id]))]
                 [true {:original-contact contact}]
                 false))
    :can-post-to-missing? false
    :post! (fn [{:keys [new-contact]}]
             (persist contacts-storage new-contact))
    :post-redirect? true
    :location "/contacts"
    :handle-exception (fn [ctx]
                        (clojure.pprint/pprint ctx))
    :handle-see-other (representation/ring-response
                        {:flash "Updated Contact!"})
    :handle-malformed (fn [{:keys [request new-contact validation-errors]}]
                        (representation/ring-response
                          (render request new-contact (malli.error/humanize validation-errors))
                          {:headers {"Content-Type" "text/html"}}))
    :handle-ok (fn [{:keys [request original-contact]}]
                 (render request original-contact))))
