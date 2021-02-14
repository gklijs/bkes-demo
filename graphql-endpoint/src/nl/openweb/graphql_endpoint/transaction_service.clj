(ns nl.openweb.graphql-endpoint.transaction-service
  (:require [com.stuartsierra.component :as component]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojurewerkz.money.amounts :as ma]
            [clojurewerkz.money.currencies :as cu]
            [clojurewerkz.money.format :as fo]
            [nl.openweb.graphql-endpoint.query-bus :as query-bus]
            [nl.openweb.graphql-endpoint.util :refer [new-id]]
            [nl.openweb.topology.clients :as clients])
  (:import (nl.openweb.data TransactionHappenedEvent TransactionByIdQuery AllLastTransactionsQuery TransactionsByIbanQuery)
           (java.util Locale)))

(def app-id "transaction-service")
(def euro-c (cu/for-code "EUR"))
(def dutch-locale (Locale. "nl" "NL"))

(defn the->graphql
  [^TransactionHappenedEvent the]
  (let [changed-by (.getChangedBy the)]
    {:id          (.getTransactionId the)
     :iban        (.getIban the)
     :new_balance (fo/format (ma/amount-of euro-c (/ (.getNewBalance the) 100)) dutch-locale)
     :changed_by  (fo/format (ma/amount-of euro-c (/ (Math/abs changed-by) 100)) dutch-locale)
     :from_to     (.getFromTo the)
     :direction   (if (< changed-by 0) "DEBIT" "CREDIT")
     :descr       (.getDescription the)
     :cbl         changed-by}))

(defn projector->graphql
  [transaction]
  (let [^long changed-by (:changed-by transaction)]
    {:id          (:id transaction)
     :iban        (:iban transaction)
     :new_balance (fo/format (ma/amount-of euro-c (/ (:new-balance transaction) 100)) dutch-locale)
     :changed_by  (fo/format (ma/amount-of euro-c (/ (Math/abs changed-by) 100)) dutch-locale)
     :from_to     (:from-to transaction)
     :direction   (if (< changed-by 0) "DEBIT" "CREDIT")
     :descr       (:description transaction)
     :cbl         changed-by}))

(defn stream-the
  [cr subscriptions]
  (let [^TransactionHappenedEvent the (.value cr)
        graphql-transaction (the->graphql the)]
    (doseq [[filter-f source-stream] (vals (:map @subscriptions))]
      (when (filter-f graphql-transaction)
        (source-stream graphql-transaction)))))

(defrecord TransactionService []

  component/Lifecycle

  (start [this]
    (let [subscriptions (atom {:id 0 :map {}})
          stop-consume-f (clients/consume-all-from-now app-id "transaction_events" #(stream-the % subscriptions))]
      (-> this
          (assoc :subscriptions subscriptions)
          (assoc :stop-consume stop-consume-f))))

  (stop [this]
    ((:stop-consume this))
    (doseq [[_ source-stream] (vals (:map @(:subscriptions this)))]
      (source-stream nil))
    (-> this
        (assoc :subscriptions nil)
        (assoc :stop-consume nil))))

(defn new-service
  []
  {:transaction-service (-> {}
                            map->TransactionService
                            (component/using [:query-bus]))})

(defn find-transaction-by-id
  [db id]
  (let [query-feedback (query-bus/issue-query (:query-bus db) (TransactionByIdQuery. (new-id) id))]
    (if
      (:failure query-feedback)
      (log/warn "error getting transaction by id for id," id "with error," (:reason query-feedback))
      (projector->graphql (:result query-feedback)))))

(defn find-transactions-by-iban
  [db iban max-msg]
  (log/info db)
  (let [query-feedback (query-bus/issue-query (:query-bus db) (TransactionsByIbanQuery. (new-id) iban max-msg))]
    (if
      (:failure query-feedback)
      (log/warn "error getting transactions by iban for iban," iban "with error," (:reason query-feedback))
      (map projector->graphql (:result query-feedback)))))

(defn find-all-last-transactions
  [db]
  (let [query-feedback (query-bus/issue-query (:query-bus db) (AllLastTransactionsQuery. (new-id)))]
    (if
      (:failure query-feedback)
      (log/warn "error getting all last transactions, error," (:reason query-feedback))
      (map projector->graphql (:result query-feedback)))))

(defn add-stream
  [subscriptions filter-f source-stream]
  (let [new-id (inc (:id subscriptions))]
    (-> subscriptions
        (assoc :id new-id)
        (assoc-in [:map new-id] [filter-f source-stream]))))

(defn includes?
  [s substr]
  (string/includes? (string/lower-case s) (string/lower-case substr)))

(defn optionally-add-premise
  [premises arg premise]
  (if arg
    (conj premises (partial premise arg))
    premises))

(defn filter-function
  [args]
  (let [{:keys [iban min_amount max_amount direction descr_includes]} args
        premises (-> []
                     (optionally-add-premise iban #(= %1 (:iban %2)))
                     (optionally-add-premise min_amount #(<= %1 (:cbl %2)))
                     (optionally-add-premise max_amount #(>= %1 (:cbl %2)))
                     (optionally-add-premise direction #(= %1 (:direction %2)))
                     (optionally-add-premise descr_includes #(includes? (:desc %2) %1)))]
    (fn [bc-map] (every? #(% bc-map) premises))))

(defn create-transaction-subscription
  [db source-stream args]
  (let [subscriptions (swap! (:subscriptions db) add-stream (filter-function args) source-stream)]
    (:id subscriptions)))

(defn stop-transaction-subscription
  [db id]
  (let [source-stream (second (get (:map @(:subscriptions db)) id))]
    (when source-stream
      (source-stream nil)
      (swap! (:subscriptions db) #(update % :map dissoc id)))))
