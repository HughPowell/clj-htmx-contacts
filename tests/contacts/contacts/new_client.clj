(ns contacts.contacts.new-client
  (:require [clojure.test :refer [is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as generators]
            [clojure.test.check.properties :refer [for-all]]
            [contacts.app :as app]
            [contacts.contacts :as contacts]
            [contacts.contacts-client]
            [contacts.contacts.new :as sut]
            [contacts.lib.app :as lib.app]
            [contacts.lib.html :as html]
            [contacts.lib.oracle :as oracle]
            [contacts.lib.request :as request]
            [net.cgrand.enlive-html :as enlive]))

(def id (atom 0))

(defn oracle-persist* [contacts-storage contact]
  (let [ids (set (map :id contacts-storage))]
    (loop [proposed-id (str (swap! id inc))]
      (if (contains? ids proposed-id)
        (recur (str (swap! id inc)))
        (conj contacts-storage (assoc contact :id proposed-id))))))

(oracle/register {'sut/persist* oracle-persist*})

(defn- contact-data-is-identical? [sut oracle]
  (is (= (set (map #(dissoc % :id) sut))
         (set (map #(dissoc % :id) oracle)))))

(defn- ids-are-unique? [contacts]
  (is (= (count contacts)
         (count (set (map :id contacts))))))

(defspec new-contact-integration-matches-oracle
  (for-all [contacts (malli.generator/generator contacts/schema)
            contact (malli.generator/generator sut/schema)]
    (let [persist-contact (fn [storage contacts contact] (-> storage
                                                             (contacts/persist* contacts)
                                                             (sut/persist* contact)
                                                             (contacts/retrieve*)))
          sut-results (persist-contact (atom #{}) contacts contact)
          oracle-results (oracle/fixture (persist-contact #{} contacts contact))]
      (and (is (contact-data-is-identical? sut-results oracle-results))
           (is (ids-are-unique? sut-results))
           (is (ids-are-unique? oracle-results))))))

(defn- new-contact-form-is-returned-ok? [contacts request]
  (let [{:keys [status]} (lib.app/make-call contacts request)]
    (is (= 200 status))))

(defn- new-form-is-empty? [contacts request]
  (let [inputs-with-values (-> (lib.app/make-call contacts request)
                               (:body)
                               (enlive/html-snippet)
                               (enlive/select [[:input (enlive/attr? :value)]]))]
    (is (empty? inputs-with-values))))

(defspec getting-a-new-contact-form-provides-an-empty-form
  (for-all [contacts (malli.generator/generator contacts/schema)
            request (request/generator "/contacts/new")]
    (and (is (new-contact-form-is-returned-ok? contacts request))
         (is (new-form-is-empty? contacts request)))))

(defn- saving-contact-redirects-to-contact-list? [{:keys [status headers]}]
  (and (is (= 303 status))
       (is (= "/contacts" (:location headers)))))
(defn- saved-contact-is-in-contacts-list? [contacts contact {:keys [status body]}]
  (let [snippet (enlive/html-snippet body)]
    (and (is (= 200 status))
         (is (set (html/table->map snippet [:first-name :last-name :phone :email]))
             (set (map #(dissoc % :id) (conj contacts contact)))))))

(defspec adding-new-contact-adds-contact-to-contacts-list
  (for-all [contacts (malli.generator/generator contacts/schema)
            [contact save-contact-request contact-list-request]
            (generators/let [contact (malli.generator/generator sut/schema)
                             save-contact-request (request/generator "/contacts/new"
                                                                     {:request-method :post
                                                                      :form-params    contact})
                             contact-list-request (request/generator "/contacts")]
              [contact save-contact-request contact-list-request])]
    (let [app (app/handler (contacts.app/init-contacts-storage contacts))
          save-contact-response (lib.app/keyword-headers (app save-contact-request))
          contact-list-response (app contact-list-request)]
      (and
        (is (saving-contact-redirects-to-contact-list? save-contact-response))
        (is (saved-contact-is-in-contacts-list? contacts contact contact-list-response))))))

(comment
  (new-contact-integration-matches-oracle)
  (getting-a-new-contact-form-provides-an-empty-form)
  (adding-new-contact-adds-contact-to-contacts-list)
  )
