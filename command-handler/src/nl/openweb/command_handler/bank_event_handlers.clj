(ns nl.openweb.command-handler.bank-event-handlers
  (:require [nl.openweb.command-handler.db :as db]
            [nl.openweb.command-handler.transfer-handlers :as saga-handlers])
  (:import (nl.openweb.data BankAccountCreatedEvent MoneyCreditedEvent MoneyDebitedEvent MoneyReturnedEvent
                            TransferStartedEvent TransferCompletedEvent TransferFailedEvent UserAddedToBankAccountEvent
                            UserRemovedFromBankAccountEvent)
           (org.apache.kafka.clients.consumer ConsumerRecord)
           (org.apache.kafka.clients.producer KafkaProducer)))

(defn handle-bank-account-creation
  [^BankAccountCreatedEvent event]
  (db/add-to-db! :bank-accounts (.getIban event)
                 {:iban    (.getIban event)
                  :token   [(.getToken event)]
                  :balance 0
                  :limit   -50000
                  :users   {(.getUsername event) (.getToken event)}}))

(defn handle-money-credited
  [^MoneyCreditedEvent event]
  (db/update-in-db! :bank-accounts (.getIban event)
                    (fn [m] (update m :balance #(+ % (.getAmount event))))))

(defn handle-money-debited
  [^MoneyDebitedEvent event]
  (db/update-in-db! :bank-accounts (.getIban event)
                    (fn [m] (update m :balance #(- % (.getAmount event))))))

(defn handle-money-returned
  [^MoneyReturnedEvent event]
  (db/update-in-db! :bank-accounts (.getIban event)
                    (fn [m] (update m :balance #(+ % (.getAmount event))))))

(defn handle-transfer-started
  [^KafkaProducer producer ^TransferStartedEvent event]
  (db/add-to-db! :bank-transfers (.getId event)
                 {:state :started})
  (saga-handlers/handle-transfer-started producer event))

(defn handle-transfer-completed
  [^TransferCompletedEvent event]
  (db/update-in-db! :bank-transfers (.getId event)
                    (fn [m] (assoc m :state :complete)))
  (saga-handlers/handle-transfer-completed event))

(defn handle-transfer-failed
  [^TransferFailedEvent event]
  (db/update-in-db! :bank-transfers (.getId event)
                    (fn [m] (assoc m :state :failed)))
  (saga-handlers/handle-transfer-failed event))

(defn handle-user-added
  [^UserAddedToBankAccountEvent event]
  (db/update-in-db! :bank-accounts (.getIban event)
                    (fn [m] (update m :users #(assoc % (.getUsername event) (.getToken event))))))

(defn handle-user-removed
  [^UserRemovedFromBankAccountEvent event]
  (db/update-in-db! :bank-accounts (.getIban event)
                    (fn [m] (update m :users #(dissoc % (.getUsername event))))))

(defn handle-event
  [^KafkaProducer producer ^ConsumerRecord record]
  (let [event (.value record)]
    (condp instance? event
      BankAccountCreatedEvent (handle-bank-account-creation event)
      MoneyCreditedEvent (handle-money-credited event)
      MoneyDebitedEvent (handle-money-debited event)
      MoneyReturnedEvent (handle-money-returned event)
      TransferStartedEvent (handle-transfer-started producer event)
      TransferCompletedEvent (handle-transfer-completed event)
      TransferFailedEvent (handle-transfer-failed event)
      UserAddedToBankAccountEvent (handle-user-added event)
      UserRemovedFromBankAccountEvent (handle-user-removed event))))
