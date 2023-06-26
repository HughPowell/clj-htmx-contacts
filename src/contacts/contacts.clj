(ns contacts.contacts
  (:refer-clojure :exclude [find])
  (:require [contacts.contact :as contact]
            [contacts.page :as page]
            [clojure.string :as string]
            [hiccup.element :as element]
            [hiccup.form :as form]))

;; Schema

(def schema
  [:sequential contact/schema])

;; Business logic

(defn find [contacts query]
  (if query
    (filter (fn [contact]
              (some (fn [attribute] (string/includes? attribute query))
                    (vals (select-keys contact [:first-name :last-name :phone :email]))))
            contacts)
    contacts))

;; Rendering

(def search-query-param :query)

(defn- search-form [current-search]
  (form/form-to {:class "tool-bar"} [:get "/contacts"]
                (form/label "search" "Search Term")
                (page/search-field (name search-query-param) current-search)
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

(defn render [contacts query]
  (list
    (search-form query)
    (table contacts)
    (add-contact)))

;; Persistence

(def ^:private contacts-storage (atom {}))

(defn persist [contacts]
  (reset! contacts-storage contacts))

(defn retrieve []
  @contacts-storage)
