(ns contacts.request
  (:require [camel-snake-kebab.core :as camel-snake-kebab]
            [reitit.core :as reitit]
            [ring.middleware.params :as params]))

(defn- assoc-params [request options]
  (-> request
      (params/params-request options)
      (update :params merge (:path-params (reitit/match-by-path (:router options) (:uri request))))
      (update :params update-keys camel-snake-kebab/->kebab-case-keyword)))

(defn wrap-params
  ([handler]
   (wrap-params handler {}))
  ([handler options]
   (fn
     ([request]
      (handler (assoc-params request options)))
     ([request respond raise]
      (handler (assoc-params request options) respond raise)))))
