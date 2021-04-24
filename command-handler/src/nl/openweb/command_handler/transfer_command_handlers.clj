(ns nl.openweb.command-handler.transfer-command-handlers
  (:require [clojurewerkz.money.amounts :as ma]
            [clojurewerkz.money.currencies :as cu]
            [clojurewerkz.money.format :as fo]
            [nl.openweb.command-handler.db :as db]
            [nl.openweb.command-handler.bank-event-handlers :as bank-event-handlers]
            [nl.openweb.command-handler.command-util :as command-util])
  (:import (java.util Locale)
           (nl.openweb.data CreditMoneyCommand ReturnMoneyCommand DebitMoneyCommand MoneyReturnedEvent
                            MoneyCreditedEvent MoneyDebitedEvent)
           (org.apache.kafka.clients.producer KafkaProducer)
           (org.apache.kafka.clients.consumer ConsumerRecord)))

(def feedback (command-util/feedback-function "transfer_command_feedback" :bank))
(def dutch-locale (Locale. "nl" "NL"))
(def euro-c (cu/for-code "EUR"))

(defn handle-debit-money
  [^KafkaProducer producer ^DebitMoneyCommand command]
  (feedback producer command (.getIban command)
            (if-let [account (db/get-from-db-optional-read-from-bkes :bank-accounts (.getIban command) bank-event-handlers/handle-event-bkes)]
              (if-let [token (get-in account [:users (.getUsername command)])]
                (if (= token (.getToken command))
                  (if (> (- (:balance account) (.getAmount command)) (:limit account))
                    {:event (MoneyDebitedEvent. (.getIban command) (.getAmount command) (.getId command))
                     :order (+ 1 (:order account))}
                    (str "insufficient funds would be below the limit of " (fo/format (ma/amount-of euro-c (/ (:limit account) 100)) dutch-locale)))
                  "token is not valid")
                "user is no owner of bank account")
              "iban not known")))

(defn handle-credit-money
  [^KafkaProducer producer ^CreditMoneyCommand command]
  (feedback producer command (.getIban command)
            (if-let [account (db/get-from-db-optional-read-from-bkes :bank-accounts (.getIban command) bank-event-handlers/handle-event-bkes)]
              {:event (MoneyCreditedEvent. (.getIban command) (.getAmount command) (.getId command))
               :order (+ 1 (:order account))}
              "iban not known")))

(defn handle-return-money
  [^KafkaProducer producer ^ReturnMoneyCommand command]
  (feedback producer command (.getIban command)
            (if-let [account (db/get-from-db-optional-read-from-bkes :bank-accounts (.getIban command) bank-event-handlers/handle-event-bkes)]
              {:event (MoneyReturnedEvent. (.getIban command) (.getAmount command) (.getId command) (.getReason command))
               :order (+ 1 (:order account))}
              "iban not known")))

(defn handle-command
  [^KafkaProducer producer ^ConsumerRecord record]
  (let [command (.value record)]
    (condp instance? command
      CreditMoneyCommand (handle-credit-money producer command)
      DebitMoneyCommand (handle-debit-money producer command)
      ReturnMoneyCommand (handle-return-money producer command))))
