(ns contacts.app
  (:require [clojure.string :as string]
            [contacts.contact :as contact]
            [contacts.contact.delete :as delete]
            [contacts.contact.edit :as edit]
            [contacts.contact.new :as contact.new]
            [contacts.contacts :as contacts]
            [clojure.java.io :as io]
            [contacts.page :as page]
            [contacts.request :as request]
            [liberator.core :refer [resource]]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.flash :as flash]
            [ring.middleware.session :as session]))

(def ^:private return-home
  [:p "Here's the incantation for getting back " [:a {:href "/"} "Home"] "."])

(defn we-messed-up [{:keys [request]}]
  (page/render
    (:flash request)
    (list
      [:h1 "Ooops ... looks like we messed up ... sorry about that."]
      return-home)))

(defn- could-not-find-it [{:keys [request]}]
  (page/render
    (:flash request)
    (list
      [:h1 "Ooops ... we've looked under the sofa ... but we still can't find it."]
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
   :handle-not-found      (fn [{:keys [request]}] (could-not-find-it request))
   :handle-exception      (fn [{:keys [request]}] (we-messed-up request))})

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
                ["/contacts/new" {:conflicting true
                                  :handler     (contact.new/resource defaults contacts-storage)}]
                ["/contacts/:id" {:conflicting true
                                  :handler     (contact/resource defaults contacts-storage)}]
                ["/contacts/:id/edit" (edit/resource defaults contacts-storage)]
                ["/contacts/:id/delete" (delete/resource defaults contacts-storage)]]))

(defn handler [contacts-storage]
  (let [router (router contacts-storage)]
    (ring/ring-handler
      router
      (ring/create-default-handler
        {:not-found      (fn [request] {:status 404
                                        :body   (could-not-find-it request)})
         :not-acceptable (fn [request] {:status 500
                                        :body   (we-messed-up request)})})
      {:middleware     [session/wrap-session
                        flash/wrap-flash
                        #(request/wrap-params % {:router router})]
       :inject-match?  false
       :inject-router? false})))

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
