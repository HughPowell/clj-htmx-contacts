(ns contacts.request
  (:require [camel-snake-kebab.core :as camel-snake-kebab]
            [ring.middleware.params :as params]))

(defn assoc-query-params [request]
  (-> request
      (params/assoc-query-params "UTF-8")
      (update :params update-keys camel-snake-kebab/->kebab-case-keyword)))
