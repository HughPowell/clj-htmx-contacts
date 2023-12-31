(ns contacts.test-lib.users
  (:require [clojure.test.check.generators :as generators]))

(def authorisation-id-generator
  (generators/such-that seq generators/string-alphanumeric))

(def two-plus-authorisation-ids-generator
  (->> authorisation-id-generator
       (generators/vector-distinct)
       (generators/such-that (fn [authorisation-ids] (>= (count authorisation-ids) 2)))))
