(ns nl.openweb.command-handler.bank-event-handlers
  (:require [nl.openweb.command-handler.db :as db])
  (:import (nl.openweb.data BankAccountCreatedEvent MoneyCreditedEvent MoneyDebitedEvent MoneyReturnedEvent
                            UserAddedToBankAccountEvent UserRemovedFromBankAccountEvent)
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

(defn handle-user-added
  [^UserAddedToBankAccountEvent event]
  (db/update-in-db! :bank-accounts (.getIban event)
                    (fn [m] (update m :users #(assoc % (.getUsername event) (.getToken event))))))

(defn handle-user-removed
  [^UserRemovedFromBankAccountEvent event]
  (db/update-in-db! :bank-accounts (.getIban event)
                    (fn [m] (update m :users #(dissoc % (.getUsername event))))))

(defn handle-event
  [^KafkaProducer event]
  (condp instance? event
    BankAccountCreatedEvent (handle-bank-account-creation event)
    MoneyCreditedEvent (handle-money-credited event)
    MoneyDebitedEvent (handle-money-debited event)
    MoneyReturnedEvent (handle-money-returned event)
    UserAddedToBankAccountEvent (handle-user-added event)
    UserRemovedFromBankAccountEvent (handle-user-removed event)))

(defn handle-event-kafka
  [^ConsumerRecord record]
  (handle-event (.value record)))

(defn handle-event-bkes
  [event-list]
  (doseq [event event-list] (handle-event event)))
