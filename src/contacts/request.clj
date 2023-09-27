(ns contacts.request
  (:require [camel-snake-kebab.core :as camel-snake-kebab]
            [clojure.string :as string]
            [java-time.api :as java-time]
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

(defn ->cookie-expires-time [since-epoch]
  (let [as-gmt (-> since-epoch
                   (java-time/as :millis)
                   (java-time/instant)
                   (java-time/zoned-date-time "GMT"))]
    (java-time/format :rfc-1123-date-time as-gmt)))

(defn construct-url
  ([request]
   (cond-> (str (name (:scheme request)) "://" (:server-name request))
     (:server-port request) (str ":" (:server-port request))
     (:uri request) (str (:uri request))
     (seq (:query-string request)) (str "?" (:query-string request))))
  ([uri query-params]
   (->> query-params
        (map (fn [[k v]] (format "%s=%s" (camel-snake-kebab/->snake_case_string k) v)))
        (string/join "&")
        (format "%s?%s" uri))))
