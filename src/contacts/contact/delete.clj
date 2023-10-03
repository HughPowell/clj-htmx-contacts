(ns contacts.contact.delete
  (:require [contacts.system.storage :as storage]
            [liberator.core :as liberator]
            [liberator.representation :as representation]))

;; HTTP Resource

(defn resource [defaults contacts-storage]
  (liberator/resource defaults
    :allowed-methods [:post]
    :exists? (fn [{:keys [request]}]
               (if-let [contact (storage/retrieve contacts-storage (get-in request [:params :id]))]
                 [true {:contact contact}]
                 false))
    :post! (fn [{:keys [contact]}]
             (storage/delete contacts-storage (:id contact)))
    :post-redirect? true
    :can-post-to-missing? false
    :location "/contacts"
    :handle-see-other (representation/ring-response
                        {:flash "Deleted Contact!"})))
