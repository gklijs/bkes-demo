(ns nl.openweb.graphql-endpoint.money-transfer-service
  (:require [com.stuartsierra.component :as component]
            [nl.openweb.graphql-endpoint.command-bus :as command-bus]
            [nl.openweb.topology.value-generator :refer [bytes->uuid uuid->bytes]]
            [clojure.tools.logging :as log])
  (:import (nl.openweb.data MoneyTransferCommand Id)
           (java.util UUID)))

(defn create-money-transfer
  [^UUID uuid args]
  (let [id (Id. (uuid->bytes uuid))]
    (MoneyTransferCommand.
      id
      (:token args)
      (long (:amount args))
      (:from args)
      (:to args)
      (:descr args)
      (:username args))))

(defn money-transfer
  [db args]
  (try
    (let [uuid-arg (:uuid args)
          uuid (UUID/fromString uuid-arg)
          command (create-money-transfer uuid args)
          command-feedback (command-bus/issue-command (:command-bus db) command)]
      (if (:failure command-feedback)
        {:uuid    uuid-arg
         :success false
         :reason  (:reason command-feedback)}
        {:uuid    uuid-arg
         :success true
         :reason  nil}))
    (catch IllegalArgumentException e (log/warn (:uuid args) "is not valid" e))))

(defrecord MoneyTransferService []
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn new-service
  []
  {:money-transfer-service (-> {}
                               map->MoneyTransferService
                               (component/using [:command-bus]))})
