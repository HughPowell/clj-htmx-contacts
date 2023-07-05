(ns contacts.lib.request
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

(defn generator
  ([path] (generator path nil))
  ([path overrides]
   (generators/fmap
     (fn [request]
       (-> request
           (meta-merge/meta-merge overrides)
           (assoc :uri path)))
     (malli.generator/generator schema))))

(defn map->query-string [m]
  (->> m
       (map (fn [[k v]]
              (format "%s=%s"
                      (name k)
                      (URLEncoder/encode ^String v
                                         ^Charset StandardCharsets/UTF_8))))
       (string/join "&")))

(defn create
  ([path] (create path nil))
  ([path search]
   (let [base-request {:server-port    80
                       :server-name    "localhost"
                       :remote-addr    "192.168.0.1"
                       :uri            path
                       :scheme         :https
                       :request-method :get
                       :protocol       "HTTP/1.1"
                       :headers        {"accept" "text/html"}}]
     (if search
       (assoc base-request :query-string (format "query=%s" (URLEncoder/encode ^String search
                                                                               ^Charset StandardCharsets/UTF_8)))
       base-request))))
