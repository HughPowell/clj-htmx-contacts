(ns secrets
  (:require [aero.core :as aero]
            [clojure.java.shell :as shell]
            [clojure.string :as string]))

(defn- sh [& args]
  (let [{:keys [exit out err] :as response} (apply shell/sh args)]
    (if (zero? exit)
      (string/trimr out)
      (throw (ex-info (if (seq err) err out) response)))))

(defn- load-secrets* []
  (sh "vlt" "login")
  (->> (sh "vlt" "secrets" "list")
       (string/split-lines)
       (rest)
       (pmap (fn [line]
               (let [secret-name (re-find #"^\w+" line)]
                 [secret-name
                  (sh "vlt" "secrets" "get" "-plaintext" secret-name)])))
       (into {})))

(defn- load-secrets []
  (try
    (load-secrets*)
    (catch Exception _
      (sh "vlt" "logout")
      (load-secrets*))))

(def secrets (load-secrets))

(defn reset []
  (alter-var-root #'secrets (fn [_] (load-secrets))))

(defmethod aero/reader 'vault
  [_opts _tag value]
  (get secrets (str value)))
