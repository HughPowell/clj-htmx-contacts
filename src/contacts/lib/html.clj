(ns contacts.lib.html
  (:require [hiccup.def :refer [defelem]]
            [hiccup.form :as form]))

(defelem search-field
  ([name] (search-field name nil))
  ([name value] (#'form/input-field "search" name value)))
