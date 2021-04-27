(ns nl.openweb.projector.bank-event-handlers
  (:require [nl.openweb.projector.db :as db]
            [nl.openweb.topology.clients :as clients]
            [nl.openweb.topology.value-generator :as vg])
  (:import (nl.openweb.data BankAccountCreatedEvent MoneyCreditedEvent MoneyDebitedEvent MoneyReturnedEvent TransferStartedEvent TransferCompletedEvent TransferFailedEvent UserRemovedFromBankAccountEvent UserAddedToBankAccountEvent TransactionHappenedEvent)
           (org.apache.kafka.clients.consumer ConsumerRecord)
           (org.apache.kafka.clients.producer KafkaProducer)))

(defn transaction->the
  [transaction]
  (TransactionHappenedEvent. (:id transaction) (:iban transaction) (:new-balance transaction)
                             (:changed-by transaction) (:from-to transaction) (:description transaction)))

(defn handle-bank-account-creation
  [^BankAccountCreatedEvent event]
  (db/add-to-db! :bank-accounts (.getIban event)
                 {:iban    (.getIban event)
                  :token   [(.getToken event)]
                  :balance 0
                  :limit   -50000
                  :users   {(.getUsername event) (.getToken event)}})
  (db/add-to-db! :transactions-by-iban (.getIban event) [])
  (db/add-iban-for-user! (.getUsername event) (.getIban event)))

(defn handle-money-credited
  [^KafkaProducer producer ^MoneyCreditedEvent event]
  (let [iban (.getIban event)
        updated-account (db/update-in-db! :bank-accounts iban
                                          (fn [m] (update m :balance #(+ % (.getAmount event)))))
        transfer (db/get-from-db :bank-transfers (.getId event))
        transaction (db/add-transaction! {:iban        iban
                                          :new-balance (:balance updated-account)
                                          :changed-by  (.getAmount event)
                                          :from-to     (:from transfer)
                                          :description (:description transfer)})]
    (clients/produce producer "transaction_events" iban (transaction->the transaction))))

(defn handle-money-debited
  [^KafkaProducer producer ^MoneyDebitedEvent event]
  (let [iban (.getIban event)
        updated-account (db/update-in-db! :bank-accounts iban
                                          (fn [m] (update m :balance #(- % (.getAmount event)))))
        transfer (db/get-from-db :bank-transfers (.getId event))
        transaction (db/add-transaction! {:iban        iban
                                          :new-balance (:balance updated-account)
                                          :changed-by  (- (.getAmount event))
                                          :from-to     (:to transfer)
                                          :description (:description transfer)})]
    (db/update-in-db! :bank-transfers (.getId event) #(assoc % :transaction-id (:transaction-id transaction)))
    (clients/produce producer "transaction_events" iban (transaction->the transaction))))

(defn handle-money-returned
  [^KafkaProducer producer ^MoneyReturnedEvent event]
  (let [iban (.getIban event)
        updated-account (db/update-in-db! :bank-accounts iban
                                          (fn [m] (update m :balance #(+ % (.getAmount event)))))
        transfer (db/get-from-db :bank-transfers (.getId event))
        transaction (db/add-transaction! {:iban        iban
                                          :new-balance (:balance updated-account)
                                          :changed-by  (.getAmount event)
                                          :from-to     (:from transfer)
                                          :description (str "Cancelled transaction with id " (vg/identifier->string (.getId event)) " because " (.getReason event))})]
    (clients/produce producer "transaction_events" iban (transaction->the transaction))))

(defn handle-transfer-started
  [^TransferStartedEvent event]
  (db/add-to-db! :bank-transfers (.getId event)
                 {:token       (.getToken event)
                  :amount      (.getAmount event)
                  :from        (.getFrom event)
                  :to          (.getTo event)
                  :description (.getDescription event)
                  :username    (.getUsername event)
                  :state       :started}))

(defn handle-transfer-completed
  [^TransferCompletedEvent event]
  (db/update-in-db! :bank-transfers (.getId event)
                    (fn [m] (assoc m :state :complete))))

(defn handle-transfer-failed
  [^TransferFailedEvent event]
  (db/update-in-db! :bank-transfers (.getId event)
                    (fn [m] (assoc m :state :failed))))

(defn handle-user-added
  [^UserAddedToBankAccountEvent event]
  (db/update-in-db! :bank-accounts (.getIban event)
                    (fn [m] (update m :users #(assoc % (.getUsername event) (.getToken event)))))
  (db/add-iban-for-user! (.getUsername event) (.getIban event)))

(defn handle-user-removed
  [^UserRemovedFromBankAccountEvent event]
  (db/update-in-db! :bank-accounts (.getIban event)
                    (fn [m] (update m :users #(dissoc % (.getUsername event)))))
  (db/update-in-db! :ibans-by-user (.getUsername event) #(vec (remove #{(.getUsername event)} %))))

(defn handle-event
  [^KafkaProducer producer ^ConsumerRecord record]
  (let [event (.value record)]
    (condp instance? event
      BankAccountCreatedEvent (handle-bank-account-creation event)
      MoneyCreditedEvent (handle-money-credited producer event)
      MoneyDebitedEvent (handle-money-debited producer event)
      MoneyReturnedEvent (handle-money-returned producer event)
      TransferStartedEvent (handle-transfer-started event)
      TransferCompletedEvent (handle-transfer-completed event)
      TransferFailedEvent (handle-transfer-failed event)
      UserAddedToBankAccountEvent (handle-user-added event)
      UserRemovedFromBankAccountEvent (handle-user-removed event))))
