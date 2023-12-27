(ns contacts.system.contacts-storage
  (:require [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [contacts.contact.schemas :as schemas]
            [malli.util :as malli.util]
            [next.jdbc :as jdbc]
            [honey.sql :as sql]
            [honey.sql.helpers :as sql.helpers])
  (:refer-clojure :exclude [update]))

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

;; Persistence

(defprotocol ContactsStorage
  (retrieve* [this] [this id])
  (create* [this contact])
  (update* [this contact])
  (delete* [this id]))

(def contacts-table
  {:up   (-> (sql.helpers/create-table :contacts :if-not-exists)
             (sql.helpers/with-columns [[:id :varchar :primary-key [:default [:raw "gen_random_uuid ()"]]]
                                        [:first-name :varchar [:not nil]]
                                        [:last-name :varchar [:not nil]]
                                        [:phone :varchar [:not nil]]
                                        [:email :varchar [:not nil]]]))
   :down (sql.helpers/drop-table :contacts)})

(def user-id-column
  {:up   (-> (sql.helpers/alter-table :contacts)
             (sql.helpers/add-column :user-id :varchar))
   :down (-> (sql.helpers/alter-table :contacts)
             (sql.helpers/drop-column :user-id))})

(def user-id-column-references-users-table
  (let [constraint-name "user_id_foreign_key"]
    {:up   {:alter-table :contacts
            :raw         (string/join " "
                                      [(format "ADD CONSTRAINT %s" constraint-name)
                                       "FOREIGN KEY (user_id)"
                                       "REFERENCES users(user_id)"
                                       "ON DELETE CASCADE"])}
     :down {:alter-table :contacts
            :raw         (format "DROP CONSTRAINT %s" constraint-name)}}))

(defn- contacts-insert [contacts]
  (-> (sql.helpers/insert-into :contacts)
      (sql.helpers/values (seq contacts))
      (sql/format)))

(defn contacts-storage [data-source]
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
        (schemas/validate contacts-schema)))
  ([contacts-storage id]
   (->> id
        (retrieve* contacts-storage)
        (schemas/validate [:maybe existing-contact-schema]))))

(defn create [contacts-storage contact]
  (->> contact
       (schemas/validate new-contact-schema)
       (create* contacts-storage)))

(defn update [contacts-storage contact]
  (->> contact
       (schemas/validate existing-contact-schema)
       (update* contacts-storage)))

(defn delete [contacts-storage id]
  (delete* contacts-storage id))

(defrecord ContactsStorageComponent [data-source]
  component/Lifecycle
  (start [component]
    (assoc component :contacts-storage (contacts-storage (:data-source data-source))))
  (stop [component]
    (assoc component :contacts-storage nil)))

(defn contacts-storage-component [] (map->ContactsStorageComponent {}))

(comment
  )
