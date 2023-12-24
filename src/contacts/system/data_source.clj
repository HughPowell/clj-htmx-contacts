(ns contacts.system.data-source
  (:require [com.stuartsierra.component :as component]
            [next.jdbc.connection :as connection])
  (:import (com.zaxxer.hikari HikariDataSource)))

(defn- data-source [credentials]
  (->> credentials
       (:credentials)
       (connection/jdbc-url)
       (hash-map :jdbcUrl)
       (connection/->pool HikariDataSource)))

(defrecord DataSourceComponent [credentials]
  component/Lifecycle
  (start [component]
    (assoc component :data-source (data-source credentials)))
  (stop [component]
    (update component :data-source #(when % (.close ^HikariDataSource %)))))

(defn data-source-component
  ([] (map->DataSourceComponent {}))
  ([credentials] (map->DataSourceComponent {:credentials {:credentials credentials}})))
