(ns contacts.system.app
  (:require [aero.core :as aero]
            [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [contacts.system.auth :as auth]
            [contacts.contact :as contact]
            [contacts.contact.delete :as delete]
            [contacts.contact.edit :as edit]
            [contacts.contact.new :as contact.new]
            [contacts.contacts :as contacts]
            [clojure.java.io :as io]
            [contacts.lib.page :as page]
            [contacts.lib.request :as request]
            [contacts.system.storage :as storage]
            [database-test-container]
            [liberator.core :refer [resource]]
            [liberator.representation :as representation]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.cookies :as cookies]
            [ring.middleware.flash :as flash]
            [ring.middleware.session :as session])
  (:gen-class)
  (:import (org.eclipse.jetty.server Server)))

(def ^:private return-home
  [:p "Here's the incantation for getting back " [:a {:href "/"} "Home"] "."])

(defn we-messed-up [ctx]
  (page/render
    ctx
    (list
      [:h1 "Ooops ... looks like we messed up ... sorry about that."]
      return-home)))

(defn- could-not-find-it [{:keys [ctx]}]
  (page/render
    ctx
    (list
      [:h1 "Ooops ... we've looked under the sofa ... but we still can't find it."]
      return-home)))

(defn defaults [auth]
  {:available-media-types ["text/html"]
   :authorized?           (fn [ctx] (auth/authorized? auth ctx))
   :handle-unauthorized   (fn [ctx] (auth/handle-unauthorized auth ctx))
   :handle-not-acceptable (fn [{:keys [request]}]
                            (string/join
                              " "
                              [(format "Unsupported media type(s), %s."
                                       (get-in request [:headers "accept"]))
                               "This server only serves HTML."
                               "Please re-request with Content-Type of text/html, text/* or */*."]))
   :handle-not-found      (fn [{:keys [request]}] (representation/ring-response
                                                    {:headers {"Content-Type" "text/html;charset=UTF-8"}
                                                     :body    (could-not-find-it request)}))
   :handle-exception      (fn [{:keys [request]}] (representation/ring-response
                                                    {:headers {"Content-Type" "text/html;charset=UTF-8"}
                                                     :body    (we-messed-up request)}))})

(defn router [auth contacts-storage]
  (ring/router [["/" (resource (defaults auth)
                       :handle-ok (fn [_]
                                    (representation/ring-response
                                      {:status  303
                                       :headers {"Location" "/contacts"}})))]
                ["/favicon.ico" (resource (defaults auth)
                                  :available-media-types ["image/x-icon"]
                                  :handle-ok (fn [_] (-> "public/favicon.ico"
                                                         (io/resource)
                                                         (io/input-stream))))]
                ["/public/*" (ring/create-resource-handler)]
                ["/contacts" (contacts/resource (defaults auth) contacts-storage)]
                ["/contacts/new" {:conflicting true
                                  :handler     (contact.new/resource (defaults auth) contacts-storage)}]
                ["/contacts/:id" {:conflicting true
                                  :handler     (contact/resource (defaults auth) contacts-storage)}]
                ["/contacts/:id/edit" (edit/resource (defaults auth) contacts-storage)]
                ["/contacts/:id/delete" (delete/resource (defaults auth) contacts-storage)]]))

(defn handler [auth contacts-storage]
  (let [router (router auth contacts-storage)]
    (ring/ring-handler
      router
      (ring/create-default-handler
        {:not-found      (fn [request] {:status 404
                                        :body   (could-not-find-it request)})
         :not-acceptable (fn [request] {:status 500
                                        :body   (we-messed-up request)})})
      {:middleware     [session/wrap-session
                        cookies/wrap-cookies
                        flash/wrap-flash
                        #(request/wrap-params % {:router router})]
       :inject-match?  false
       :inject-router? false})))

(defn start-server [contacts-storage auth]
  (jetty/run-jetty (#'handler auth contacts-storage) {:join? false :port 3000}))

(defrecord ServerComponent [contacts-storage auth]
  component/Lifecycle
  (start [component]
    (assoc component :server (start-server (:storage contacts-storage) (:auth auth))))
  (stop [component]
    (when-let [server (:server component)]
      (.stop ^Server server))
    (assoc component :server nil)))

(defn server-component []
  (map->ServerComponent {}))

(defn system-map [config]
  (component/system-map
    :storage (storage/storage-component config)
    :auth (auth/auth-component config)
    :app (component/using (server-component)
                          {:contacts-storage :storage
                           :auth             :auth})))

(defn read-config
  ([] (read-config nil))
  ([config]
   (-> (or config "config.edn")
       (io/resource)
       (aero/read-config))))

(defn -main [& _]
  (let [system (component/start-system (system-map (read-config)))]
    (.addShutdownHook
      (Runtime/getRuntime)
      (Thread. ^Runnable #(component/stop-system system)))))

(comment
  (user/reset-app)
  )
