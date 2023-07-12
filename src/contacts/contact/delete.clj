(ns contacts.contact.delete
  (:require [contacts.contact :as contact]
            [liberator.core :as liberator]
            [liberator.representation :as representation]
            [malli.core :as malli]))

;; Storage

(defn retrieve* [contacts-storage id]
  (first (get (group-by :id @contacts-storage) id)))

(defn- retrieve [contacts-storage id]
  (let [contact (retrieve* contacts-storage id)]
    (when (malli/validate contact/schema contact)
      contact)))

(defn delete* [contacts-storage contact-id]
  (swap! contacts-storage #(set (remove (fn [{:keys [id]}] (= contact-id id)) %)))
  contacts-storage)

(defn- delete [contacts-storage id]
  (delete* contacts-storage id)
  contacts-storage)

;; HTTP Resource

(defn resource [defaults contacts-storage]
  (liberator/resource defaults
    :allowed-methods [:post]
    :exists? (fn [{:keys [request]}]
               (if-let [contact (retrieve contacts-storage (get-in request [:params :id]))]
                 [true {:contact contact}]
                 false))
    :post! (fn [{:keys [contact]}]
             (delete contacts-storage (:id contact)))
    :post-redirect? true
    :can-post-to-missing? false
    :location "/contacts"
    :handle-exception (fn [ctx] (clojure.pprint/pprint ctx))
    :handle-see-other (representation/ring-response
                        {:flash "Deleted Contact!"})))
