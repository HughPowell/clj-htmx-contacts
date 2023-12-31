(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'net.hughpowell/clj-htmx-contacts)
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s.jar" (name lib)))
(def major-version "1")
(def minor-version "0")

(defn version [_]
  (println (format "%s.%s.%s" major-version minor-version (b/git-process {:git-args "rev-parse --short HEAD"}))))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'contacts.system.app}))
