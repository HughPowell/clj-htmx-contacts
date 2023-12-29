(ns contacts.test-lib.test-system
  (:require [camel-snake-kebab.core :as camel-snake-kebab]
            [camel-snake-kebab.extras :as camel-snake-kebab.extras]
            [contacts.system.app :as app]
            [contacts.system.contacts-storage :as contacts-storage]
            [contacts.test-lib.oracle :as oracle]
            [net.cgrand.enlive-html :as enlive])
  (:import (java.io StringReader)))

(defn- keywordise-headers [request]
  (update request :headers #(camel-snake-kebab.extras/transform-keys camel-snake-kebab/->kebab-case-keyword %)))

(defn- parse-body [request]
  (cond-> request
    (:body request) (update :body enlive/html-snippet)))

(defn construct-handler [contacts]
  (let [contacts-storage (oracle/contacts-storage)]
    (run! (fn [contact] (contacts-storage/create contacts-storage contact)) contacts)
    (app/handler (oracle/authorization) contacts-storage)))

(defn make-request [handler request]
  (let [request' (cond-> request
                   (string? (:body request)) (update :body #(StringReader. %)))]
    (-> request'
        (handler)
        (keywordise-headers)
        (parse-body))))
