(ns contacts.test-lib.users
  (:require [clojure.test.check.generators :as generators]
            [contacts.system.auth :as auth]
            [malli.generator]))

(def authorisation-id-generator
  (malli.generator/generator auth/authorisation-id-schema))

(def two-plus-authorisation-ids-generator
  (generators/vector-distinct authorisation-id-generator {:min-elements 2}))
