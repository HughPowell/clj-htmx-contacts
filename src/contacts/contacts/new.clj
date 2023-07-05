(ns contacts.contacts.new
  (:require [contacts.page :as page]
            [hiccup.form :as form]
            [liberator.core :as liberator]))

(defn- input [name label type place-holder]
  [:p
   (form/label name label)
   [:input {:name name :id name :type type :placeholder place-holder}]
   [:span.error]])

(defn- render []
  (page/render
    (list
      (form/form-to [:post "/contacts/new"]
                    [:fieldset
                     [:legend "Contact Values"]
                     (input "email" "Email" "email" "Email")
                     (input "first-name" "First Name" "text" "First Name")
                     (input "last-name" "Last Name" "text" "Last Name")
                     (input "phone" "Phone" "text" "Phone")
                     [:button "Save"]])
      [:p [:a {:href "/contacts"} "Back"]])))

(defn resource [default]
  (liberator/resource default
                      :handle-ok (fn [_]
                                   (render))))
