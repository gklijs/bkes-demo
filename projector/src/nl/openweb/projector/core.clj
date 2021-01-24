(ns nl.openweb.projector.core
  (:require [nl.openweb.projector.bank-event-handlers :as bank-event-handlers]
            [nl.openweb.projector.bank-query-handlers :as bank-query-handlers]
            [nl.openweb.projector.user-event-handlers :as user-event-handlers]
            [nl.openweb.projector.user-query-handlers :as user-query-handlers]
            [nl.openweb.topology.clients :as clients])
  (:import (org.apache.kafka.clients.producer KafkaProducer))
  (:gen-class))

(def app-id "projector")

(defn -main
  []
  (let [^KafkaProducer producer (clients/get-producer app-id)]
    (clients/consume-all-from-start app-id "user_events" user-event-handlers/handle-event)
    (clients/consume-all-from-start app-id "bank_events" #(bank-event-handlers/handle-event producer %))
    (clients/consume-part-from-now app-id "user_queries" #(user-query-handlers/handle-query producer %))
    (clients/consume-part-from-now app-id "bank_queries" #(bank-query-handlers/handle-query producer %))))

