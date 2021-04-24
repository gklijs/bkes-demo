(ns nl.openweb.command-handler.core
  (:require [nl.openweb.command-handler.bank-command-handlers :as bank-command-handlers]
            [nl.openweb.command-handler.bank-event-handlers :as bank-event-handlers]
            [nl.openweb.command-handler.transfer-command-handlers :as transfer-command-handlers]
            [nl.openweb.command-handler.transfer-event-handlers :as transfer-event-handlers]
            [nl.openweb.command-handler.transfer-feedback-handlers :as transfer-feedback-handlers]
            [nl.openweb.command-handler.user-command-handlers :as user-command-handlers]
            [nl.openweb.command-handler.user-event-handlers :as user-event-handlers]
            [nl.openweb.topology.clients :as clients])
  (:import (org.apache.kafka.clients.producer KafkaProducer))
  (:gen-class))

(def app-id "command-handler")

(defn -main
  []
  (let [^KafkaProducer producer (clients/get-producer app-id)]
    (clients/consume-all-from-now app-id "user_events" user-event-handlers/handle-event-kafka)
    (clients/consume-all-from-now app-id "bank_events" bank-event-handlers/handle-event-kafka)
    (clients/consume-all-from-now app-id "transfer_events" #(transfer-event-handlers/handle-event-kafka producer %))
    (clients/consume-part-from-now app-id "user_commands" #(user-command-handlers/handle-command producer %))
    (clients/consume-part-from-now app-id "bank_commands" #(bank-command-handlers/handle-command producer %))
    (clients/consume-all-from-now app-id "transfer_command_feedback" #(transfer-feedback-handlers/handle-feedback producer %))
    (clients/consume-part-from-now app-id "transfer_commands" #(transfer-command-handlers/handle-command producer %))))

