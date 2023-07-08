(ns contacts.contact)

(def id [:id [:string {:min 1}]])
(def first-name [:first-name :string])
(def last-name [:last-name :string])
(def phone [:phone [:or [:string {:max 0}] [:re #"\+?(\d( |-)?){7,13}\d"]]])
(def email [:email [:or [:string {:max 0}] [:re #"[a-z\.\+]+@[a-z\.]+"]]])
(def schema)