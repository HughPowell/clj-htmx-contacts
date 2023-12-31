(ns contacts.test-lib.users
  (:require [clojure.test.check.generators :as generators]))

(def authorisation-id-generator
  (generators/such-that seq generators/string-alphanumeric))
