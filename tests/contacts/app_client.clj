(ns contacts.app-client
  (:require [clojure.string :as string]
            [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [clojure.test.check.properties :refer [for-all]]
            [contacts.app :as app]
            [contacts.lib.app :as lib.app]
            [contacts.lib.request :as request]
            [reitit.core :as reitit]))

(defn- unsupported-media-type-response? [contacts request]
  (let [{:keys [status]} (lib.app/make-call contacts request)]
    (is (= 406 status))))

(defspec non-text-html-content-type-not-supported
  (for-all [request (generators/let [content-type (generators/such-that
                                                    (fn [content-type]
                                                      (not (#{"text/html" "text/*" "*/*"} (string/trim content-type))))
                                                    generators/string-alphanumeric)]
                      (request/generator "/" {:headers {"accept" content-type}}))]
    (is (unsupported-media-type-response? #{} request))))

;; test for not found
(defn- non-existant-route-returns-not-found? [contacts request]
  (let [{:keys [status]} (lib.app/make-call contacts request)]
    (is (= 404 status))))

(defspec not-found-returned-for-unknown-paths
  (for-all [request (generators/let [path (generators/such-that
                                            (fn [path]
                                              (let [existing-routes (->> (app/router #{})
                                                                         (reitit/routes)
                                                                         (map first)
                                                                         (set))]
                                                (not (contains? existing-routes path))))
                                            generators/string-alphanumeric)]
                      (request/generator path))]
    (is (non-existant-route-returns-not-found? #{} request))))

;; spec for uncaught exceptions

(comment
  (non-text-html-content-type-not-supported)
  (not-found-returned-for-unknown-paths)
  )
