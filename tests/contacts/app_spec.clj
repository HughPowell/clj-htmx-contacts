(ns contacts.app-spec
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.app :as app]
            [contacts.lib.test-system :as test-system]
            [contacts.lib.request :as request]
            [reitit.core :as reitit]))

(defn- unsupported-media-type-response? [{:keys [status]}]
  (is (= 406 status)))

(deftest non-text-html-content-type-not-supported
  (checking "" [content-type (generators/such-that
                               (fn [content-type]
                                 (not (#{"text/html" "text/*" "*/*"} (string/trim content-type))))
                               generators/string-alphanumeric)
                request (request/generator "/" {:headers {"accept" content-type}})]
    (let [response (-> #{}
                       (test-system/construct-handler)
                       (test-system/make-request request))]
      (is (unsupported-media-type-response? response)))))

(defn- non-existant-route-returns-not-found? [{:keys [status]}]
  (is (= 404 status)))

(deftest not-found-returned-for-unknown-paths
  (checking "" [path (generators/such-that
                       (fn [path]
                         (let [existing-routes (->> (app/router #{})
                                                    (reitit/routes)
                                                    (map first)
                                                    (set))]
                           (not (contains? existing-routes path))))
                       generators/string-alphanumeric)
                request (request/generator path)]
    (let [response (-> #{}
                       (test-system/construct-handler)
                       (test-system/make-request request))]
      (is (non-existant-route-returns-not-found? response)))))

;; spec for uncaught exceptions

(comment
  (non-text-html-content-type-not-supported)
  (not-found-returned-for-unknown-paths)
  )
