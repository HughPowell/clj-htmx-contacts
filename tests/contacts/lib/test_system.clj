(ns contacts.lib.test-system
  (:require [camel-snake-kebab.core :as camel-snake-kebab]
            [camel-snake-kebab.extras :as camel-snake-kebab.extras]
            [contacts.app :as app]
            [contacts.lib.oracle :as oracle]
            [contacts.storage-oracle]
            [net.cgrand.enlive-html :as enlive])
  (:import (java.io StringReader)))

(defn- keywordise-headers [request]
  (update request :headers #(camel-snake-kebab.extras/transform-keys camel-snake-kebab/->kebab-case-keyword %)))

(defn- parse-body [request]
  (cond-> request
    (:body request) (update :body enlive/html-snippet)))

(defn make-real-request [contacts request]
  (let [request' (cond-> request
                   (string? (:body request)) (update :body #(StringReader. %)))
        call (app/handler contacts)]
    (-> request'
        (call)
        (keywordise-headers)
        (parse-body))))

(defn make-oracle-request [contacts request]
  (oracle/fixture (make-real-request contacts request)))
