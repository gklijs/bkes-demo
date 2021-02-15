(ns nl.openweb.projector.bank-query-handlers
  (:require [nl.openweb.projector.db :as db]
            [nl.openweb.projector.query-util :as query-util])
  (:import (nl.openweb.data AllLastTransactionsQuery FindBankAccountQuery FindBankAccountsForUserQuery TransactionByIdQuery TransactionsByIbanQuery)
           (org.apache.kafka.clients.consumer ConsumerRecord)
           (org.apache.kafka.clients.producer KafkaProducer)))

(def feedback (query-util/feedback-function "bank_queries_feedback"))

(defn all-last-transaction
  [^KafkaProducer producer ^AllLastTransactionsQuery query]
  (feedback producer query
            (if-let [transactions (db/get-all-last-transactions)]
              transactions
              [])))

(defn find-bank-account
  [^KafkaProducer producer ^FindBankAccountQuery query]
  (feedback producer query
            (if-let [bank-account (db/get-from-db :bank-accounts (.getIban query))]
              bank-account
              "iban does not exist")))

(defn find-bank-accounts-for-user
  [^KafkaProducer producer ^FindBankAccountsForUserQuery query]
  (feedback producer query
            (if-let [ibans (db/get-from-db :ibans-by-user (.getUsername query))]
              (mapv (fn [iban] (db/get-from-db :bank-accounts iban)) ibans)
              "username does not have accounts")))

(defn find-transaction
  [^KafkaProducer producer ^TransactionByIdQuery query]
  (feedback producer query
            (if-let [transaction (db/get-transaction (.getTransactionId query))]
              transaction
              "transaction does not exist")))

(defn find-transactions
  [^KafkaProducer producer ^TransactionsByIbanQuery query]
  (feedback producer query
            (if-let [transactions (db/get-from-db :transactions-by-iban (.getIban query))]
              (->> transactions
                   (take-last (.getMaxItems query))
                   reverse)
              [])))

(defn handle-query
  [^KafkaProducer producer ^ConsumerRecord record]
  (let [query (.value record)]
    (condp instance? query
      AllLastTransactionsQuery (all-last-transaction producer query)
      FindBankAccountQuery (find-bank-account producer query)
      FindBankAccountsForUserQuery (find-bank-accounts-for-user producer query)
      TransactionByIdQuery (find-transaction producer query)
      TransactionsByIbanQuery (find-transactions producer query))))
