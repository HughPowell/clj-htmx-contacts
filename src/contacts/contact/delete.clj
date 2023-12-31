(ns contacts.contact.delete
  (:require [contacts.system.contacts-storage :as storage]
            [liberator.core :as liberator]
            [liberator.representation :as representation]))

;; HTTP Resource

(defn resource [defaults contacts-storage]
  (liberator/resource defaults
    :allowed-methods [:post]
    :exists? (fn [{:keys [request user]}]
               (if-let [contact (let [contact-id (get-in request [:params :id]) ]
                                  (if user
                                   (storage/retrieve-for-user contacts-storage (:user-id user) contact-id)
                                   (storage/retrieve contacts-storage contact-id)))]
                 [true {:contact contact}]
                 false))
    :post! (fn [{:keys [contact user]}]
             (if user
               (storage/delete-for-user contacts-storage (:user-id user) (:id contact))
               (storage/delete contacts-storage (:id contact))))
    :post-redirect? true
    :can-post-to-missing? false
    :location "/contacts"
    :handle-see-other (representation/ring-response
                        {:flash "Deleted Contact!"})))
