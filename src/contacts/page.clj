(ns contacts.page
  (:require [hiccup.def :refer [defelem]]
            [hiccup.form :as form]
            [hiccup.page :as page]))

(defn render [content]
  (page/html5
    {:lang "en"}
    [:head
     [:title "Contact App"]
     (page/include-css "/public/missing.min.css")]
    [:body
     [:main
      [:header
       [:h1
        [:all-caps "contacts.app"]
        [:sub-title "A Demo Contacts Application"]]]
      content]]))

(defelem search-field
  ([name] (search-field name nil))
  ([name value] (#'form/input-field "search" name value)))

(comment
  (render nil))
