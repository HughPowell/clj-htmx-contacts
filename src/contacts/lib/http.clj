(ns contacts.lib.http
  (:require [camel-snake-kebab.core :as camel-snake-kebab]
            [clojure.string :as string]
            [java-time.api :as java-time]))

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
