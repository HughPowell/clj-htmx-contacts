(ns contacts.system.storage
  (:require [clojure.core :as core]
            [com.stuartsierra.component :as component]
            [contacts.contact.schemas :as schemas]
            [malli.core :as malli]
            [malli.error :as malli.error]
            [malli.util :as malli.util]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [honey.sql :as sql]
            [honey.sql.helpers :as sql.helpers])
  (:refer-clojure :exclude [update])
  (:import (com.zaxxer.hikari HikariDataSource)))

;; Schemas

(def new-contact-schema
  [:map
   schemas/first-name
   schemas/last-name
   schemas/phone
   schemas/email])

(def existing-contact-schema
  (malli.util/merge
    new-contact-schema
    [:map schemas/id]))

(defn- ids-are-unique? [contacts]
  (= (count contacts)
     (count (set (map :id contacts)))))

(def contacts-schema
  [:and
   [:set existing-contact-schema]
   [:fn ids-are-unique?]])

(defn- validate [schema data]
  (when-not (malli/validate schema data)
    (let [explanation (malli/explain schema data)]
      (throw (ex-info (str (malli.error/humanize explanation)) explanation))))
  data)

;; Persistence

(defprotocol ContactsStorage
  (retrieve* [this] [this id])
  (create* [this contact])
  (update* [this contact])
  (delete* [this id]))

(def ^:private contacts-table
  (-> (sql.helpers/create-table :contacts :if-not-exists)
      (sql.helpers/with-columns [[:id :varchar :primary-key [:default [:raw "gen_random_uuid ()"]]]
                                 [:first-name :varchar [:not nil]]
                                 [:last-name :varchar [:not nil]]
                                 [:phone :varchar [:not nil]]
                                 [:email :varchar [:not nil]]])
      (sql/format)))

(defn- contacts-insert [contacts]
  (-> (sql.helpers/insert-into :contacts)
      (sql.helpers/values (seq contacts))
      (sql/format)))

(defn contacts-storage [data-source contacts]
  (jdbc/execute! data-source contacts-table)
  (when (seq contacts)
    (jdbc/execute! data-source (contacts-insert (validate contacts-schema contacts))))
  (reify ContactsStorage
    (retrieve* [_]
      (let [select-all (-> (sql.helpers/select :*)
                           (sql.helpers/from :contacts)
                           (sql/format))]
        (->> (jdbc/execute! data-source select-all jdbc/unqualified-snake-kebab-opts)
             (set))))
    (retrieve* [_ id]
      (let [select-all (-> (sql.helpers/select :*)
                           (sql.helpers/from :contacts)
                           (sql.helpers/where [:= :id id])
                           (sql/format))]
        (jdbc/execute-one! data-source select-all jdbc/unqualified-snake-kebab-opts)))
    (create* [this contact]
      (jdbc/execute! data-source (contacts-insert [contact]))
      this)
    (update* [this contact]
      (let [update (-> (sql.helpers/update :contacts)
                       (sql.helpers/set (dissoc contact :id))
                       (sql.helpers/where [:= :id (:id contact)])
                       (sql/format))]
        (jdbc/execute! data-source update))
      this)
    (delete* [this id]
      (let [delete (-> (sql.helpers/delete-from :contacts)
                       (sql.helpers/where [:= :id id])
                       (sql/format))]
        (jdbc/execute! data-source delete))
      this)))

(defn retrieve
  ([contacts-storage]
   (->> contacts-storage
        (retrieve*)
        (validate contacts-schema)))
  ([contacts-storage id]
   (->> id
        (retrieve* contacts-storage)
        (validate [:maybe existing-contact-schema]))))

(defn create [contacts-storage contact]
  (->> contact
       (validate new-contact-schema)
       (create* contacts-storage)))

(defn update [contacts-storage contact]
  (->> contact
       (validate existing-contact-schema)
       (update* contacts-storage)))

(defn delete [contacts-storage id]
  (delete* contacts-storage id))

(defrecord DataSourceComponent [credentials]
  component/Lifecycle
  (start [component]
    (->> credentials
         (:credentials)
         (connection/jdbc-url)
         (hash-map :jdbcUrl)
         (connection/->pool HikariDataSource)
         (assoc component :data-source)))
  (stop [component]
    (core/update component :data-source #(when % (.close ^HikariDataSource %)))))

(defn data-source-component
  ([] (map->DataSourceComponent {}))
  ([credentials] (map->DataSourceComponent {:credentials {:credentials credentials}})))

(defrecord StorageComponent [data-source contacts]
  component/Lifecycle
  (start [component]
    (assoc component :storage (contacts-storage (:data-source data-source) contacts)))
  (stop [component]
    (assoc component :storage nil)))

(defn storage-component
  ([] (storage-component #{}))
  ([contacts] (map->StorageComponent {:contacts contacts})))

(comment
  )
