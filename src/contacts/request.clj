(ns contacts.request
  (:require [camel-snake-kebab.core :as camel-snake-kebab]
            [ring.middleware.params :as params]))

(defn assoc-params [request]
  (-> request
      (params/params-request)
      (update :params merge (:path-params request))
      (update :params update-keys camel-snake-kebab/->kebab-case-keyword)))
