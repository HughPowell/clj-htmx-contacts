(ns contacts.contacts
  (:refer-clojure :exclude [find])
  (:require [contacts.contact.schemas :as schemas]
            [contacts.page :as page]
            [clojure.string :as string]
            [contacts.storage :as storage]
            [hiccup.element :as element]
            [hiccup.form :as form]
            [liberator.core :as liberator]))

;; Schemas

(defn- ids-are-unique? [contacts]
  (= (count contacts)
     (count (set (map :id contacts)))))

(def schema
  [:and
   [:set [:map
          schemas/id
          schemas/first-name
          schemas/last-name
          schemas/phone
          schemas/email]]
   [:fn ids-are-unique?]])

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

(defn render [request contacts query]
  (page/render
    (:flash request)
    (list
      (search-form query)
      (table contacts)
      (add-contact))))

;; HTTP Resource

(defn resource [defaults contacts-storage]
  (liberator/resource defaults
                      :handle-ok (fn [{:keys [request]}]
                                   (let [contacts (storage/retrieve contacts-storage)
                                         query (get-in request [:params :query])]
                                     (render
                                       request
                                       (find contacts query)
                                       query)))))

(comment
  )
