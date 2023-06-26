(ns contacts.contact)

(def schema
  [:map
   [:id [:string {:min 1}]]
   [:first-name {:optional true} [:string {:min 1}]]
   [:last-name {:optional true} [:string {:min 1}]]
   [:phone {:optional true} [:re #"\+?(\d( |-)?){7,13}\d"]]
   [:email {:optional true} [:re #"[a-z\.\+]+@[a-z\.]+"]]])

(comment
  )
