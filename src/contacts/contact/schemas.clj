(ns contacts.contact.schemas)

(def id [:id [:string {:min 1}]])

(def first-name [:first-name :string])

(def last-name [:last-name :string])

(def phone [:phone [:or
                    [:string {:error/message "should be blank" :max 0}]
                    [:re {:error/message "should be a valid phone number"} #"\+?(\d( |-)?){7,13}\d"]]])

(def email [:email [:or
                    [:string {:error/message "should be blank" :max 0}]
                    [:re {:error/message "should be a valid email address"} #"[a-z\.\+]+@[a-z\.]+"]]])
