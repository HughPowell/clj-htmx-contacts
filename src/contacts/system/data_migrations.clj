(ns contacts.system.data-migrations
  (:require [contacts.system.contacts-storage :as contacts-storage]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [ragtime.protocols]))

(defn- execute-sql [data-source sql]
  (->> sql
       (sql/format)
       (jdbc/execute! data-source)))

(defn- ->migration [{:keys [id up down]}]
  (reify ragtime.protocols/Migration
    (id [_] id)
    (run-up! [_ data-source] (execute-sql data-source up))
    (run-down! [_ data-source] (execute-sql data-source down))))

(def ^:private data-store-migrations*
  [(merge {:id "Create contacts table"} contacts-storage/contacts-table)])

(def data-store-migrations
  (map-indexed
    (fn [index migration]
      (-> migration
          (update :id #(format "%03d - %s" %2 %1) index)
          (->migration)))
    data-store-migrations*))
