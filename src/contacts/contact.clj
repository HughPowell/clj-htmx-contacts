(ns contacts.contact)

(def schema
  [:map
   [:id [:string {:min 1}]]
   [:first-name :string]
   [:last-name :string]
   [:phone [:or [:string {:max 0}] [:re #"\+?(\d( |-)?){7,13}\d"]]]
   [:email [:or [:string {:max 0}] [:re #"[a-z\.\+]+@[a-z\.]+"]]]])
