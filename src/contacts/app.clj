(ns contacts.app
  (:require [contacts.contacts :as contacts]
            [contacts.request :as request]
            [contacts.page :as page]
            [clojure.java.io :as io]
            [liberator.core :refer [resource]]
            [malli.core :as malli]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]))

(def ^:private search-key :query)
(def ^:private contacts-key :contacts)
(def ^:private search-schema
  [:or [:string {:min 1}] :nil])


(defn router []
  (ring/router [["/" (resource :available-media-types ["text/html"]
                               :exists? false
                               :existed? true
                               :moved-temporarily? true
                               :location "/contacts")]
                ["/favicon.ico" (resource :available-media-types ["image/x-icon"]
                                          :handle-ok (fn [_] (io/input-stream (io/resource "public/favicon.ico"))))]
                ["/public/*" (ring/create-resource-handler)]
                ["/contacts" (resource :available-media-types ["text/html"]
                                       :malformed? (fn [{:keys [request]}]
                                                     (let [search (-> request
                                                                      (request/assoc-query-params)
                                                                      (get-in [:params contacts/search-query-param])
                                                                      (not-empty))]
                                                       (if (malli/validate search-schema search)
                                                         [false {search-key search}]
                                                         true)))
                                       :exists? (fn [_]
                                                  (let [contacts (contacts/retrieve)]
                                                    (when (malli/validate contacts/schema contacts)
                                                      {contacts-key contacts})))
                                       :handle-ok (fn [ctx]
                                                    (-> (get ctx contacts-key)
                                                        (contacts/find (get ctx search-key))
                                                        (contacts/render (get ctx search-key))
                                                        (page/render))))]]))

(defn handler []
  (ring/ring-handler
    (router)
    nil
    {:inject-match? false :inject-router? false}))

(defn start-server []
  (jetty/run-jetty (#'handler) {:join? false :port 3000}))

(comment
  (def server (start-server))
  (do
    (.stop server)
    (def server (start-server))
    (contacts/persist (malli.generator/generate contacts/schema))))
