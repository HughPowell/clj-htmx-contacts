(ns contacts.app-spec
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.system.app :as app]
            [contacts.test-lib.request :as request]
            [contacts.test-lib.test-system :as test-system]
            [contacts.test-lib.users :as users]
            [reitit.core :as reitit]))

(defn- unsupported-media-type-response? [{:keys [status]}]
  (is (= 406 status)))

(deftest non-text-html-content-type-not-supported
  (checking "" [authorisation-id users/authorisation-id-generator
                content-type (generators/such-that
                               (fn [content-type]
                                 (not (#{"text/html" "text/*" "*/*"} (string/trim content-type))))
                               generators/string-alphanumeric)
                request (request/generator authorisation-id "/" {:headers {"accept" content-type}})]
            (let [response (-> authorisation-id
                               (test-system/construct-handler-for-user #{})
                               (test-system/make-request request))]
              (is (unsupported-media-type-response? response)))))

(defn- non-existant-route-returns-not-found? [{:keys [status]}]
  (is (= 404 status)))

(deftest not-found-returned-for-unknown-paths
  (checking "" [authorisation-id users/authorisation-id-generator
                path (generators/such-that
                       (fn [path]
                         (let [existing-routes (->> (app/router nil #{})
                                                    (reitit/routes)
                                                    (map first)
                                                    (set))]
                           (not (contains? existing-routes path))))
                       generators/string-alphanumeric)
                request (request/generator authorisation-id path)]
            (let [response (-> authorisation-id
                               (test-system/construct-handler-for-user #{})
                               (test-system/make-request request))]
              (is (non-existant-route-returns-not-found? response)))))

;; spec for uncaught exceptions

(comment
  (non-text-html-content-type-not-supported)
  (not-found-returned-for-unknown-paths))
