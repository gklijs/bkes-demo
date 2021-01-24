(ns nl.openweb.graphql-endpoint.kafka-producer
  (:require [com.stuartsierra.component :as component]
            [nl.openweb.topology.clients :as clients])
  (:import (org.apache.kafka.clients.producer KafkaProducer)))

(def app-id "graphql-endpoint-producer")

(defrecord KafkaProducerWrapper []

  component/Lifecycle

  (start [this]
    (let [^KafkaProducer producer (clients/get-producer app-id)]
      (assoc this :producer producer)))

  (stop [this]
    (.close (:producer this))
    (assoc this :producer nil)))

(defn new-producer
  []
  {:kafka-producer (map->KafkaProducerWrapper {})})


