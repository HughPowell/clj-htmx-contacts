(ns contacts.app
  (:require [clojure.string :as string]
            [contacts.contacts :as contacts]
            [contacts.contacts.new :as contacts.new]
            [clojure.java.io :as io]
            [contacts.page :as page]
            [liberator.core :refer [resource]]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]))

(def return-home
  [:p "Here's the incantation for getting back " [:a {:href "/"} "Home"] "."])

(def we-messed-up
  (page/render
    (list
      [:h1 "Ooops ... looks like we messed up ... sorry about that"]
      return-home)))

(def defaults
  {:available-media-types ["text/html"]
   :handle-not-acceptable (fn [{:keys [request]}]
                            (string/join
                              " "
                              [(format "Unsupported media type(s), %s."
                                       (get-in request [:headers "accept"]))
                               "This server only serves HTML."
                               "Please re-request with Content-Type of text/html, text/* or */*."]))
   :handle-exception      we-messed-up})

(defn router [contacts-storage]
  (ring/router [["/" (resource defaults
                               :exists? false
                               :existed? true
                               :moved-temporarily? true
                               :location "/contacts")]
                ["/favicon.ico" (resource :available-media-types ["image/x-icon"]
                                          :handle-ok (fn [_] (io/input-stream (io/resource "public/favicon.ico"))))]
                ["/public/*" (ring/create-resource-handler)]
                ["/contacts" (contacts/resource defaults contacts-storage)]
                ["/contacts/new" (contacts.new/resource defaults)]]))

(defn handler [contacts-storage]
  (ring/ring-handler
    (router contacts-storage)
    (ring/create-default-handler
      {:not-found      (constantly {:status 404
                                    :body   (page/render
                                              (list
                                                [:h1 "Ooops ... looks like that doesn't exist"]
                                                return-home))})
       :not-acceptable (constantly {:status 500 :body we-messed-up})})
    {:inject-match? false :inject-router? false}))

(defn start-server [contacts-storage]
  (jetty/run-jetty (#'handler contacts-storage) {:join? false :port 3000}))

(defn init-contacts-storage [contacts]
  (let [storage (atom #{})]
    (set-validator! storage (fn [contacts] (= (count contacts)
                                              (count (set (map :id contacts))))))
    (contacts/persist storage contacts)))

(comment
  (require '[malli.generator :as malli.generator])
  (defn populate-contacts-storage []
    (init-contacts-storage (malli.generator/generate contacts/schema)))
  (def server (start-server (populate-contacts-storage)))

  (do
    (.stop server)
    (def server (start-server (populate-contacts-storage)))))
