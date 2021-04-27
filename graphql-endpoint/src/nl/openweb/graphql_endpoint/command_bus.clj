(ns nl.openweb.graphql-endpoint.command-bus
  (:require [com.stuartsierra.component :as component]
            [nl.openweb.topology.clients :as clients]
            [nl.openweb.topology.value-generator :as vg])
  (:import (nl.openweb.data CreateUserAccountCommand CommandName CommandFailed)))

(def app-id "command-bus")
(def saga-failed #{CommandName/MarkTransferFailedCommand})
(def saga-started #{CommandName/MoneyTransferCommand})

(def time-out-value 2000)
(def time-out-default
  {:failure   true
   :timed-out true
   :reason    (str "timed out after " time-out-value " ms")})

(defn issue-command
  [db command]
  (let [[query-topic promise-map] (if (instance? CreateUserAccountCommand command)
                                    ["user_commands" (:user-promise-map db)]
                                    ["bank_commands" (:bank-promise-map db)])
        id (.getId command)
        key (vg/identifier->string id)
        promise (promise)]
    (swap! promise-map assoc id promise)
    (clients/produce (get-in db [:kafka-producer :producer]) query-topic key command)
    (let [result (deref promise time-out-value time-out-default)]
      (swap! promise-map dissoc id)
      result)))

(defn resolve-promise-if-present
  [promise-map command-feedback]
  (if-let [p (get @promise-map (.getId command-feedback))]
    (cond
      (instance? CommandFailed command-feedback)
      (deliver p {:failure true
                  :reason  (.getReason command-feedback)})
      (saga-started (.getCommandName command-feedback))
      true
      (saga-failed (.getCommandName command-feedback))
      (deliver p {:failure true
                  :reason  (.getAdditionalInfo command-feedback)})
      (.getAdditionalInfo command-feedback)
      (deliver p {:success         true
                  :additional-info (.getAdditionalInfo command-feedback)})
      :else
      (deliver p {:success true}))))

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
          (assoc :stop-bank-f stop-bank-f))))

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
