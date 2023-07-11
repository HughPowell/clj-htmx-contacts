(ns contacts.contact
  (:require [clojure.string :as string]
            [contacts.page :as page]
            [contacts.request :as request]
            [liberator.core :as liberator]
            [malli.core :as malli]))

;; Schema

(def id [:id [:string {:min 1}]])
(def first-name [:first-name :string])
(def last-name [:last-name :string])
(def phone [:phone [:or
                    [:string {:error/message "should be blank" :max 0}]
                    [:re {:error/message "should be a valid phone number"} #"\+?(\d( |-)?){7,13}\d"]]])
(def email [:email [:or
                    [:string {:error/message "should be blank" :max 0}]
                    [:re {:error/message "should be a valid email address"} #"[a-z\.\+]+@[a-z\.]+"]]])

(def schema [:map
             id
             first-name
             last-name
             phone
             email])

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
                                 (let [request' (request/assoc-params request)
                                       updates {:request request'}]
                                   (if-let [contact (retrieve contacts-storage (get-in request' [:params :id]))]
                                     [true (merge updates {:contact contact})]
                                     [false updates])))
                      :handle-ok (fn [{:keys [request contact]}]
                                   (render request contact))))
