(ns contacts.users-spec
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.test.check.generators :as generators]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [contacts.system.users-storage :as users-storage]
            [contacts.test-lib.database :as database]
            [database-test-container]))

(use-fixtures :once database/postgres-fixture)

(deftest the-same-authorisation-id-returns-the-same-user
  (checking "" [authorisation-ids (generators/vector-distinct generators/string-alphanumeric)
                authorisation-id (generators/such-that
                                   (fn [authorisation-id]
                                     (and
                                       (seq authorisation-id)
                                       (not (contains? (set authorisation-ids) authorisation-id))))
                                   generators/string-alphanumeric)]
            (with-open [connection (database/reset)]
              (let [users-storage (users-storage/users-storage connection)]
                (run! #(users-storage/->user users-storage %) authorisation-ids))
              (is (= (users-storage/->user (users-storage/users-storage connection) authorisation-id)
                     (users-storage/->user (users-storage/users-storage connection) authorisation-id))))))

(comment
  (the-same-authorisation-id-returns-the-same-user))
