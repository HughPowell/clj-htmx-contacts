(ns contacts.app
  (:require [contacts.contacts :as contacts]
            [contacts.request :as request]
            [contacts.page :as page]
            [clojure.java.io :as io]
            [liberator.core :refer [resource]]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]))

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
                                       :exists? (let [contacts (contacts/retrieve)]
                                                  {:contacts contacts})
                                       :handle-ok (fn [ctx]
                                                    (let [query (-> ctx
                                                                    (request/assoc-query-params)
                                                                    (get-in [:request :query-params :query]))]
                                                      (-> ctx
                                                          (:contacts)
                                                          (contacts/find query)
                                                          (contacts/render query)
                                                          (page/render)))))]]))

(defn handler []
  (ring/ring-handler
    (router)
    nil
    {:inject-match? false :inject-router? false}))

(defn start-server []
  (jetty/run-jetty (#'handler) {:join? false :port 3000}))

(comment
  (require '[clj-http.client :as client])

  (contacts/persist
    [{:id         1
      :first-name "John"
      :last-name  "Smith"
      :phone      "123-456-7890"
      :email      "john@example.comz"}
     {:id         2
      :first-name "Dana"
      :last-name  "Crandith"
      :phone      "123-456-7890"
      :email      "dcran@example.com"}
     {:id         3
      :first-name "Edith"
      :last-name  "Neutvaar"
      :phone      "123-456-7890"
      :email      "en@example.com"}])

  (def server (start-server))
  (do
    (.stop server)
    (def server (start-server)))

  (client/get "http://localhost:3000/contacts")
  *e
  )
