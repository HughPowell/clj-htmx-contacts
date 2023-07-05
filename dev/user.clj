(ns user)

(defn toggle-oracle [oracle]
  (let [integrations (into {} (map (fn [[sym]] [sym @(resolve sym)]) oracle))]
    (run! (fn [[sym oracle]] (alter-var-root (resolve sym) (constantly oracle))) oracle)
    integrations))
