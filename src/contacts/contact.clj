(ns contacts.contact
  (:require [clojure.string :as string]
            [contacts.lib.page :as page]
            [contacts.system.contacts-storage :as contacts-storage]
            [liberator.core :as liberator]))

;; Rendering

(defn- render [ctx {:keys [id first-name last-name phone email]}]
  (page/render
    ctx
    (list
      [:h1 (string/trim (format "%s %s" first-name last-name))]
      [:div
       [:div (format "Phone: %s" phone)]
       [:div (format "Email: %s" email)]]
      [:p
       [:a {:href (format "/contacts/%s/edit" id)} "Edit"]
       [:a {:href "/contacts"} "Back"]])))

;; HTTP Resource

(defn resource [default contacts-storage]
  (liberator/resource default
                      :allowed-methods [:get]
                      :exists? (fn [{:keys [request user]}]
                                 (if-let [contact (contacts-storage/retrieve contacts-storage
                                                                             (:user-id user)
                                                                             (get-in request [:params :id]))]
                                   [true {:contact contact}]
                                   false))
                      :handle-ok (fn [{:keys [contact]
                                       :as ctx}]
                                   (render ctx contact))))
