(ns contacts.lib.oracle)

(defonce ^:private oracle* (atom {}))

(defn register [redefs]
  (swap! oracle* merge (update-keys redefs (comp symbol resolve))))

(defn oracle [] @oracle*)

(defmacro fixture [& body]
  (let [bindings (apply concat (oracle))]
    `(with-redefs [~@bindings]
       ~@body)))
