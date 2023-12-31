(ns contacts.lib.page
  (:require [hiccup.page :as page]
            [hiccup2.core :as hiccup2]))

(defn render [{:keys [request logout-uri]} content]
  (str
    (hiccup2/html
      {:mode :html
       :lang "en"}
      (page/doctype :html5)
      [:html
       [:head
        [:title "Contact App"]
        (page/include-css "/public/missing.min.css")
        (page/include-css "/public/site.css")]
       [:body
        [:main
         [:header
          [:h1
           [:all-caps "contacts.app"]
           [:sub-title "A Demo Contacts Application"]]]
         (when logout-uri
           [:a {:href logout-uri} "Logout"])
         (when (:flash request) [:div.flash (:flash request)])
         content]]])))

(comment)
