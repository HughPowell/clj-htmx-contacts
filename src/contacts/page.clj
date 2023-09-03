(ns contacts.page
  (:require [hiccup.def :refer [defelem]]
            [hiccup.form :as form]
            [hiccup.page :as page]
            [hiccup2.core :as hiccup2]))

(defmacro html5
  "Create a HTML5 document with the supplied contents."
  [options & contents]
  (if-not (map? options)
    `(html5 {} ~options ~@contents)
    (if (options :xml?)
      `(let [options# (dissoc ~options :xml?)]
         (str
           (hiccup2/html {:mode :xml}
                         (page/xml-declaration (options# :encoding "UTF-8"))
                         (page/doctype :html5)
                         (page/xhtml-tag options# (options# :lang) ~@contents))))
      `(let [options# (dissoc ~options :xml?)]
         (str
           (hiccup2/html {:mode :html}
                         (page/doctype :html5)
                         [:html options# ~@contents]))))))

(defn render [flash content]
  (html5
    {:lang "en"}
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
      (when flash [:div.flash flash])
      content]]))

(defelem search-field
  ([name] (search-field name nil))
  ([name value] (#'form/input-field "search" name value)))

(comment
  )
