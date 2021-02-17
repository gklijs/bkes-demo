(ns nl.openweb.graphql-endpoint.start
    (:require [nl.openweb.graphql-endpoint.system :as system]
      [com.stuartsierra.component :as component]))

(defn -main
      []
      (component/start (system/new-system)))
