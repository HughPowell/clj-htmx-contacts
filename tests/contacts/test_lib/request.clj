(ns contacts.test-lib.request
  (:require [clojure.string :as string]
            [clojure.test.check.generators :as generators]
            [malli.generator :as malli.generator]
            [meta-merge.core :as meta-merge])
  (:import (java.nio.charset Charset StandardCharsets)
           (java.net URLEncoder)))

(defn ip-address? [s]
  (try
    (let [segments (map #(Integer/parseInt %) (string/split s #"\."))]
      (and (= 4 (count segments))
           (every? (fn [segment] (<= 1 segment 255)) segments)))
    (catch Exception _ false)))

(def headers
  [:map
   ["accept" [:enum "text/html" "text/*" "*/*"]]])

(def schema
  [:map
   [:server-port [:and :int [:and [:>= 0] [:<= 65535]]]]
   [:server-name [:enum "localhost"]]
   [:remote-addr [:enum "192.168.0.1"]]
   [:scheme [:enum :http :https]]
   [:request-method [:enum :get]]
   [:protocol [:enum "HTTP/1.1" "HTTP/2"]]
   [:headers headers]])


(defn- map->url-string [m]
  (->> m
       (map (fn [[k v]]
              (format "%s=%s"
                      (name k)
                      (URLEncoder/encode ^String v
                                         ^Charset StandardCharsets/UTF_8))))
       (string/join "&")))

(defn- form-params->body [overrides]
  (let [body (->> overrides
                  (:form-params)
                  (map->url-string))]
    (-> overrides
        (assoc :body body)
        (assoc-in [:headers "content-type"] "application/x-www-form-urlencoded")
        (dissoc :form-params))))

(defn- query-params->query-string [overrides]
  (let [query-string (->> overrides
                          (:query-params)
                          (map->url-string))]
    (-> overrides
        (assoc :query-string query-string)
        (dissoc :query-params))))

(defn generator
  ([path] (generator path nil))
  ([path overrides]
   (generators/fmap
     (fn [request]
       (let [overrides' (cond-> overrides
                          (:form-params overrides) (form-params->body)
                          (:query-params overrides) (query-params->query-string))]
         (-> request
             (meta-merge/meta-merge overrides')
             (assoc :uri path))))
     (malli.generator/generator schema))))

(comment
  )