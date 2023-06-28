(ns contacts.app
  (:require [contacts.contacts :as contacts]
            [clojure.java.io :as io]
            [liberator.core :refer [resource]]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]))

(defn router [contacts-storage]
  (ring/router [["/" (resource :available-media-types ["text/html"]
                               :exists? false
                               :existed? true
                               :moved-temporarily? true
                               :location "/contacts")]
                ["/favicon.ico" (resource :available-media-types ["image/x-icon"]
                                          :handle-ok (fn [_] (io/input-stream (io/resource "public/favicon.ico"))))]
                ["/public/*" (ring/create-resource-handler)]
                ["/contacts" (contacts/resource contacts-storage)]]))

(defn handler [contacts-storage]
  (ring/ring-handler
    (router contacts-storage)
    nil
    {:inject-match? false :inject-router? false}))

(defn start-server [contacts-storage]
  (jetty/run-jetty (#'handler contacts-storage) {:join? false :port 3000}))

(comment
  (defn init-contacts-storage []
    (contacts/persist (atom []) (malli.generator/generate contacts/schema)))
  (def server (start-server (init-contacts-storage)))

  (do
    (.stop server)
    (def server (start-server (init-contacts-storage)))))
