(ns nl.openweb.command-handler.transfer-event-handlers
  (:require [nl.openweb.command-handler.command-util :as command-util]
            [nl.openweb.command-handler.db :as db]
            [nl.openweb.topology.clients :as clients]
            [nl.openweb.topology.value-generator :as vg])
  (:import (org.apache.kafka.clients.consumer ConsumerRecord)
           (org.apache.kafka.clients.producer KafkaProducer)
           (nl.openweb.data TransferStartedEvent TransferCompletedEvent TransferFailedEvent MarkTransferFailedCommand CreditMoneyCommand DebitMoneyCommand)))

(defn transfer-started
  [^KafkaProducer producer ^TransferStartedEvent event]
  (if (db/get-from-db :bank-transfers-running (.getId event))
    (let [id (.getId event)
          id-s (vg/identifier->string id)
          _ (db/add-to-db! :bank-transfers-running id {:token       (.getToken event)
                                                       :amount      (.getAmount event)
                                                       :from        (.getFrom event)
                                                       :to          (.getTo event)
                                                       :description (.getDescription event)
                                                       :username    (.getUsername event)
                                                       :debited     false})
          [topic key command] (cond
                                (= (.getFrom event) (.getTo event)) ["bank_commands" id-s (MarkTransferFailedCommand. id "from and to can't be same for transfer")]
                                (command-util/invalid-from (.getFrom event)) ["bank_commands" id-s (MarkTransferFailedCommand. id "from is invalid")]
                                (= (.getFrom event) "cash") ["transfer_commands" (.getTo event) (CreditMoneyCommand. id (.getTo event) (.getAmount event))]
                                :else ["transfer_commands" (.getFrom event) (DebitMoneyCommand. id (.getFrom event) (.getAmount event) (.getToken event) (.getUsername event))])]
      (clients/produce producer topic key command))))

(defn remove-active-if-present
  [transfer-id]
  (if (db/get-from-db :bank-transfers-running transfer-id)
    (db/remove-from-db! :bank-transfers-running transfer-id)))

(defn transfer-completed
  [^TransferCompletedEvent event]
  (remove-active-if-present (.getId event)))

(defn transfer-failed
  [^TransferFailedEvent event]
  (remove-active-if-present (.getId event)))

(defn handle-transfer-started
  [^KafkaProducer producer ^TransferStartedEvent event]
  (db/add-to-db! :bank-transfers (.getId event)
                 {:state :started})
  (transfer-started producer event))

(defn handle-transfer-completed
  [^TransferCompletedEvent event]
  (db/update-in-db! :bank-transfers (.getId event)
                    (fn [m] (assoc m :state :complete)))
  (transfer-completed event))

(defn handle-transfer-failed
  [^TransferFailedEvent event]
  (db/update-in-db! :bank-transfers (.getId event)
                    (fn [m] (assoc m :state :failed)))
  (transfer-failed event))

(defn handle-event
  [^KafkaProducer producer event]
  (condp instance? event
    TransferStartedEvent (handle-transfer-started producer event)
    TransferCompletedEvent (handle-transfer-completed event)
    TransferFailedEvent (handle-transfer-failed event)))

(defn handle-event-kafka
  [^KafkaProducer producer ^ConsumerRecord record]
  (handle-event producer (.value record)))




