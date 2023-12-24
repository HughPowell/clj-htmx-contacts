(ns contacts.system.auth
  (:require [buddy.sign.jwt :as jwt]
            [camel-snake-kebab.core :as camel-snake-kebab]
            [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [contacts.lib.http :as http]
            [com.stuartsierra.component :as component]
            [java-time.api :as java-time]
            [liberator.representation :as representation]))

(defprotocol Authorization
  (authorized? [this ctx]
    "Takes a Liberator context and returns a map containing the authorization-id and logout-uri")
  (handle-unauthorized [this ctx]
    "Takes a Liberator context and returns the ring response for when a user is unauthorized"))

(defn- authorise-code [config request]
  (when-let [code (get-in request [:params :code])]
    (let [response (client/post (:authorise-uri config)
                                {:form-params      {:client_id     (:client-id config)
                                                    :client_secret (:client-secret config)
                                                    :code          code
                                                    :redirect_uri  (:redirect-uri config)
                                                    :grant_type    "authorization_code"}
                                 :throw-exceptions false})]
      (when (= (:status response) 200)
        (try
          (let [id-token (-> response
                             (:body)
                             (cheshire/parse-string camel-snake-kebab/->kebab-case-keyword)
                             (:id-token))
                {:keys [exp]} (jwt/unsign id-token (:client-secret config))
                expires (-> exp (java-time/duration :seconds) (http/->cookie-expires-time))]
            [expires id-token])
          (catch Exception _))))))

(defn- authorise-cookie [config request]
  (try
    (-> request
        (get-in [:cookies "authorization" :value])
        (jwt/unsign (:client-secret config))
        (:sub))
    (catch Exception _)))

(defn- cookie
  ([value]
   {:value     value
    :http-only true
    :secure    true})
  ([value expires]
   (merge
     (cookie value)
     {:expires expires})))

(def ^:private cookie-reset
  (->> (java-time/duration 0 :seconds)
       (http/->cookie-expires-time)
       (cookie "")))

(defn- auth-redirect? [request]
  (and (seq (get-in request [:params :state]))
       (= (get-in request [:params :state])
          (get-in request [:cookies "state" :value]))))

(defn- authorization-cookie? [request]
  (get-in request [:cookies "authorization" :value]))

(defn auth0-authorization [config]
  (reify Authorization
    (authorized? [_ {:keys [request]}]
      (cond
        (auth-redirect? request)
        (if-let [[expires id-token] (authorise-code config request)]
          [false {:login-completed? true
                  :id-token         id-token
                  :expiry           expires}]
          [false {:login-completed? false}])

        (authorization-cookie? request)
        (if-let [authorization-id (authorise-cookie config request)]
          [true {:authorization-id authorization-id
                 :logout-uri       (http/construct-url
                                     (:logout-uri config)
                                     {:post-logout-redirect-uri (:redirect-uri config)
                                      :id-token-hint            (get-in request [:cookies "authorization" :value])
                                      :state                    (get-in request [:cookies "state" :value])})}]
          [false {:login-completed? false}])

        :else
        [false {:login-completed? false}]))

    (handle-unauthorized [_ {:keys [request login-completed? id-token expiry]}]
      (let [state (str (random-uuid))]
        (if login-completed?
          (representation/ring-response
            {:status  303
             :headers {"Location" (get-in request [:cookies "location" :value])}
             :cookies {:authorization (cookie id-token expiry)
                       :state         (cookie state expiry)
                       :location      cookie-reset}})
          (representation/ring-response
            {:status  303
             :headers {"Location" (->> (select-keys config [:client-id :client-secret])
                                       (merge {:response-type "code"
                                               :scope         "openid"
                                               :redirect-uri  (:redirect-uri config)
                                               :state         state})
                                       (http/construct-url (:login-uri config)))}
             :cookies {:state         (cookie state)
                       :location      (cookie (http/construct-url request))
                       :authorization cookie-reset}}))))))

(defrecord AuthComponent [config]
  component/Lifecycle
  (start [component]
    (assoc component :auth (auth0-authorization (:auth config))))
  (stop [component]
    (assoc component :auth nil)))

(defn auth-component [config]
  (map->AuthComponent {:config config}))
