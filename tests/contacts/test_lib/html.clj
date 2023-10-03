(ns contacts.test-lib.html
  (:require [net.cgrand.enlive-html :as enlive]))

(defn table->map [snippet headers]
  (->>
    (enlive/select snippet #{[:table :tr :> enlive/void] [:table :tr :> :td :> enlive/text-node]})
    (map (fn [node] (when (string? node) node)))
    (partition (count headers))
    (map (fn [contact] (into {} (map #(vector %1 (str %2)) headers contact))))))

(defn rendered-contacts [{:keys [body]}]
  (let [contact-data (table->map body [:first-name :last-name :phone :email])
        contact-ids (->> (enlive/select body [:table :tr :> :td :> :a])
                         (map (fn [{:keys [attrs]}] (:href attrs)))
                         (partition 2)
                         (map (fn [[edit view]] (let [edit-id (second (re-seq #"[^/]+" edit))
                                                      view-id (second (re-seq #"[^/]+" view))]
                                                  (when (= edit-id view-id)
                                                    edit-id)))))]
    (map (fn [contact id] (assoc contact :id id)) contact-data contact-ids)))
