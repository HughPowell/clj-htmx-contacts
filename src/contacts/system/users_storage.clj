(ns contacts.system.users-storage
  (:require [honey.sql.helpers :as sql.helpers]))

;; Schemas

(def users-table
  {:up (-> (sql.helpers/create-table :users :if-not-exists)
           (sql.helpers/with-columns [[:id :varchar :primary-key [:default [:raw "gen_random_uuid ()"]]]
                                      [:authorisation-id :varchar :not-null :unique]]))
   :down (sql.helpers/drop-table :users)})

;; Persistence
