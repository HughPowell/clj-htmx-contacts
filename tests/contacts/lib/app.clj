(ns contacts.lib.app
  (:require [camel-snake-kebab.core :as camel-snake-kebab]
            [camel-snake-kebab.extras :as camel-snake-kebab.extras]
            [contacts.app :as app]
            [contacts.lib.oracle :as oracle]))

(defn keyword-headers [request]
  (update request :headers #(camel-snake-kebab.extras/transform-keys camel-snake-kebab/->kebab-case-keyword %)))

(defn make-call [contacts request]
  (oracle/fixture
    (-> ((app/handler contacts) request)
        (keyword-headers))))
