(ns contacts.contacts
  (:refer-clojure :exclude [find])
  (:require [contacts.lib.page :as page]
            [clojure.string :as string]
            [contacts.system.contacts-storage :as contacts-storage]
            [hiccup.element :as element]
            [hiccup.form :as form]
            [liberator.core :as liberator]))

;; Business logic

(defn- find [contacts query]
  (if query
    (filter (fn [contact]
              (some (fn [attribute] (string/includes? attribute query))
                    (vals (select-keys contact [:first-name :last-name :phone :email]))))
            contacts)
    contacts))

;; Rendering

(defn- search-form [current-search]
  (form/form-to {:class "tool-bar"} [:get "/contacts"]
                (form/label "search" "Search Term")
                (page/search-field "query" current-search)
                (form/submit-button "Search")))

(defn- table [contacts]
  [:table
   [:thead
    [:tr
     [:th "First"] [:th "Last"] [:th "Phone"] [:th "Email"]]]
   [:tbody
    (map
      (fn [{:keys [id first-name last-name phone email]}]
        [:tr
         [:td first-name]
         [:td last-name]
         [:td phone]
         [:td email]
         [:td
          (element/link-to (format "/contacts/%s/edit" (str id)) "Edit")
          (element/link-to (format "/contacts/%s" (str id)) "View")]])
      contacts)]])

(defn- add-contact []
  [:p (element/link-to "/contacts/new" "Add Contact")])

(defn render [ctx contacts query]
  (page/render
    ctx
    (list
      (search-form query)
      (table contacts)
      (add-contact))))

;; HTTP Resource

(defn resource [defaults contacts-storage]
  (liberator/resource defaults
                      :handle-ok (fn [{:keys [request user] :as ctx}]
                                   (let [contacts (contacts-storage/retrieve contacts-storage (:user-id user))
                                         query (get-in request [:params :query])]
                                     (render
                                       ctx
                                       (find contacts query)
                                       query)))))

(comment
  )
