(ns contacts.contacts
  (:refer-clojure :exclude [find])
  (:require [contacts.contact :as contact]
            [contacts.page :as page]
            [contacts.request :as request]
            [clojure.string :as string]
            [hiccup.element :as element]
            [hiccup.form :as form]
            [liberator.core :as liberator]
            [malli.core :as malli]
            [malli.error :as malli.error]))

;; Schemas

(defn- ids-are-unique? [contacts]
  (= (count contacts)
     (count (set (map :id contacts)))))

(def schema
  [:and
   [:set [:map
          contact/id
          contact/first-name
          contact/last-name
          contact/phone
          contact/email]]
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

;; Persistence

(defn- validate [schema contacts]
  (when-not (malli/validate schema contacts)
    (let [explanation (malli/explain schema contacts)]
      (throw (ex-info (malli.error/humanize explanation) explanation)))))

(defn persist* [contacts-storage contacts]
  (reset! contacts-storage (set contacts))
  contacts-storage)

(defn persist [contacts-storage contacts]
  (validate schema contacts)
  (persist* contacts-storage contacts))

(defn retrieve* [contacts-storage]
  @contacts-storage)

(defn retrieve [contacts-storage]
  (let [contacts (retrieve* contacts-storage)]
    (validate schema contacts)
    contacts))

;; HTTP Resource

(defn resource [defaults contacts-storage]
  (liberator/resource defaults
                      :handle-ok (fn [{:keys [request]}]
                                   (let [contacts (retrieve contacts-storage)
                                         query (-> request
                                                   (request/assoc-params)
                                                   (get-in [:params :query]))]
                                     (render
                                       request
                                       (find contacts query)
                                       query)))))

(comment
  )
