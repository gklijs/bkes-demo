(ns nl.openweb.graphql-endpoint.command-bus
  (:require [com.stuartsierra.component :as component]
            [nl.openweb.topology.clients :as clients]
            [nl.openweb.topology.value-generator :as vg])
  (:import (nl.openweb.data CreateUserAccountCommand CommandSucceeded)))

(def app-id "command-bus")

(defn issue-command
  [db command]
  (let [[query-topic promise-map] (if (instance? CreateUserAccountCommand command)
                                    ["user_commands" (:user-promise-map db)]
                                    ["bank_commands" (:bank-promise-map db)])
        key (vg/identifier->string (.getId command))
        promise (promise)
        id (.getId command)]
    (swap! promise-map assoc id promise)
    (clients/produce (get-in db [:kafka-producer :producer]) query-topic key command)
    (let [result (deref promise 1000 "timeout")]
      (swap! promise-map dissoc id)
      result)))

(defn resolve-promise-if-present
  [promise-map command-feedback]
  (if-let [p (get @promise-map (.getId command-feedback))]
    (if
      (instance? CommandSucceeded command-feedback)
      (deliver p true)
      (deliver p (.getReason command-feedback)))))

(defrecord CommandBus []

  component/Lifecycle

  (start [this]
    (let [user-promise-map (atom {})
          bank-promise-map (atom {})
          stop-user-f (clients/consume-all-from-now app-id "user_command_feedback"
                                                    #(resolve-promise-if-present user-promise-map (.value %)))
          stop-bank-f (clients/consume-all-from-now app-id "bank_command_feedback"
                                                    #(resolve-promise-if-present bank-promise-map (.value %)))]
      (-> this
          (assoc :user-promise-map user-promise-map)
          (assoc :bank-promise-map bank-promise-map)
          (assoc :stop-user-f stop-user-f)
          (assoc :stop-bank-f stop-bank-f)
          )))

  (stop [this]
    ((:stop-user-f this))
    ((:stop-bank-f this))
    (-> this
        (assoc :user-promise-map nil)
        (assoc :bank-promise-map nil)
        (assoc :stop-user-f nil)
        (assoc :stop-bank-f nil))))

(defn new-command-bus
  []
  {:command-bus (-> {}
                    map->CommandBus
                    (component/using [:kafka-producer]))})
