(ns contacts.lib.html
  (:require [net.cgrand.enlive-html :as enlive]))

(defn table->map [snippet headers]
  (->>
    (enlive/select snippet #{[:table :tr :> enlive/void] [:table :tr :> :td :> enlive/text-node]})
    (map (fn [node] (when (string? node) node)))
    (partition (count headers))
    (map (fn [contact] (into {} (map #(vector %1 (str %2)) headers contact))))))
