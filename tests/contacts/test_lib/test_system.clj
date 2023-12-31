(ns contacts.test-lib.test-system
  (:require [camel-snake-kebab.core :as camel-snake-kebab]
            [camel-snake-kebab.extras :as camel-snake-kebab.extras]
            [contacts.system.app :as app]
            [contacts.system.contacts-storage :as contacts-storage]
            [contacts.system.users-storage :as users-storage]
            [contacts.test-lib.oracle :as oracle]
            [net.cgrand.enlive-html :as enlive])
  (:import (java.io StringReader)))

(defn- keywordise-headers [request]
  (update request :headers #(camel-snake-kebab.extras/transform-keys camel-snake-kebab/->kebab-case-keyword %)))

(defn- parse-body [request]
  (cond-> request
    (:body request) (update :body enlive/html-snippet)))

(defn- store-data [data-storage authorisation-id contacts]
  (let [{:keys [user-id]} (users-storage/->user data-storage authorisation-id)]
    (run! (fn [contact] (contacts-storage/create data-storage user-id contact)) contacts)))

(defn construct-handler-for-user [authorisation-id contacts]
  (let [data-storage (oracle/data-storage)]
    (store-data data-storage authorisation-id contacts)
    (app/handler (oracle/authorization data-storage) data-storage)))

(defn construct-handler-for-users [authorisation-ids contacts]
  (let [data-storage (oracle/data-storage)]
    (run! (fn [[authorisation-id contacts]] (store-data data-storage authorisation-id contacts))
          (map vector authorisation-ids contacts))
    (app/handler (oracle/authorization data-storage) data-storage)))

(defn make-request [handler request]
  (let [request' (cond-> request
                   (string? (:body request)) (update :body #(StringReader. %)))]
    (-> request'
        (handler)
        (keywordise-headers)
        (parse-body))))
