(ns contacts.system.users-storage
  (:require [contacts.contact.schemas :as schemas]
            [honey.sql :as sql]
            [honey.sql.helpers :as sql.helpers]
            [next.jdbc :as jdbc]))

;; Schemas

(def users-table
  {:up   (-> (sql.helpers/create-table :users :if-not-exists)
             (sql.helpers/with-columns [[:user-id :varchar :primary-key [:default [:raw "gen_random_uuid ()"]]]
                                        [:authorisation-id :varchar :not-null :unique]]))
   :down (sql.helpers/drop-table :users)})

(def user-schema
  [:map
   [:user-id [:string {:min 1}]]])

;; Persistence

(defprotocol UsersStorage
  (->user* [this authorisation-id]))

(defn users-storage [data-source]
  (reify UsersStorage
    (->user* [_ authorisation-id]
      (let [user-id-query (-> (sql.helpers/select :user-id)
                              (sql.helpers/from :users)
                              (sql.helpers/where := :authorisation-id authorisation-id)
                              (sql/format))
            user-id (jdbc/execute-one! data-source user-id-query jdbc/unqualified-snake-kebab-opts)]
        (if user-id
          user-id
          (let [insert-user-id-statement (-> (sql.helpers/insert-into :users)
                                             (sql.helpers/values [{:authorisation-id authorisation-id}])
                                             (sql/format))]
            (try
              (jdbc/execute-one! data-source insert-user-id-statement)
              (jdbc/execute-one! data-source user-id-query jdbc/unqualified-snake-kebab-opts)
              (catch Exception _
                (jdbc/execute-one! data-source user-id-query jdbc/unqualified-snake-kebab-opts)))))))))

(defn ->user [users-storage authorisation-id]
  (schemas/validate user-schema (->user* users-storage authorisation-id)))

(comment

  )
