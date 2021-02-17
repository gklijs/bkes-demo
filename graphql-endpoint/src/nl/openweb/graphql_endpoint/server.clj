(ns nl.openweb.graphql-endpoint.server
  (:require [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.pedestal2 :as lp]
            [io.pedestal.http :as http]))

(def host (or (System/getenv "PEDESTAL_HOST") "localhost"))

(defrecord Server [schema-provider server]

  component/Lifecycle
  (start [this]
    (assoc this :server (-> schema-provider
                            :schema
                            (lp/default-service {:host host})
                            (assoc :io.pedestal.http/allowed-origins ["http://localhost:4200", "http://localhost:8181", "http://localhost:3449", "http://localhost:8888"])
                            http/create-server
                            http/start)))

  (stop [this]
    (http/stop server)
    (assoc this :server nil)))

(defn new-server
  []
  {:server (component/using (map->Server {})
                            [:schema-provider])})


