(ns contacts.contact
  (:require [clojure.string :as string]
            [contacts.contact.schemas :as schemas]
            [contacts.page :as page]
            [liberator.core :as liberator]
            [malli.core :as malli]))

;; Schema

(def schema [:map
             schemas/id
             schemas/first-name
             schemas/last-name
             schemas/phone
             schemas/email])

;; Rendering

(defn- render [request {:keys [id first-name last-name phone email]}]
  (page/render
    (:flash request)
    (list
      [:h1 (string/trim (format "%s %s" first-name last-name))]
      [:div
       [:div (format "Phone: %s" phone)]
       [:div (format "Email: %s" email)]]
      [:p
       [:a {:href (format "/contacts/%s/edit" id)} "Edit"]
       [:a {:href "/contacts"} "Back"]])))

;; Persistence

(defn retrieve* [contacts-storage id]
  (first (get (group-by :id @contacts-storage) id)))

(defn- retrieve [contacts-storage id]
  (let [contact (retrieve* contacts-storage id)]
    (when (malli/validate schema contact)
      contact)))

;; HTTP Resource

(defn resource [default contacts-storage]
  (liberator/resource default
                      :allowed-methods [:get]
                      :exists? (fn [{:keys [request]}]
                                 (if-let [contact (retrieve contacts-storage (get-in request [:params :id]))]
                                   [true {:contact contact}]
                                   false))
                      :handle-ok (fn [{:keys [request contact]}]
                                   (render request contact))))
