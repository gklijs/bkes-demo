(ns nl.openweb.graphql-endpoint.query-bus
  (:require [clojure.edn :as edn]
            [com.stuartsierra.component :as component]
            [nl.openweb.topology.clients :as clients]
            [nl.openweb.topology.value-generator :as vg])
  (:import (nl.openweb.data FindUserQuery QuerySucceeded QueryFailed)))

(def app-id "query-bus")
(def time-out-value 1000)
(def time-out-default
  {:failure   true
   :timed-out true
   :reason    (str "timed out after " time-out-value " ms")})

(defn issue-query
  [db query]
  (let [[query-topic promise-map] (if (instance? FindUserQuery query)
                                    ["user_queries" (:user-promise-map db)]
                                    ["bank_queries" (:bank-promise-map db)])
        id (.getId query)
        key (vg/identifier->string id)
        promise (promise)]
    (swap! promise-map assoc id promise)
    (clients/produce (get-in db [:kafka-producer :producer]) query-topic key query)
    (let [result (deref promise time-out-value time-out-default)]
      (swap! promise-map dissoc id)
      result)))

(defn resolve-promise-if-present
  [promise-map query-feedback]
  (if-let [p (get @promise-map (.getId query-feedback))]
    (if
      (instance? QuerySucceeded query-feedback)
      (deliver p (edn/read-string (.getQueryResult query-feedback)))
      (deliver p (.getReason query-feedback)))))

(defn resolve-promise-if-present
  [promise-map query-feedback]
  (if-let [p (get @promise-map (.getId query-feedback))]
    (if
      (instance? QueryFailed query-feedback)
      (deliver p {:failure true
                  :reason  (.getReason query-feedback)})
      (deliver p {:success true
                  :result  (edn/read-string (.getQueryResult query-feedback))}))))

(defrecord QueryBus []

  component/Lifecycle

  (start [this]
    (let [user-promise-map (atom {})
          bank-promise-map (atom {})
          stop-user-f (clients/consume-all-from-now app-id "user_queries_feedback"
                                                    #(resolve-promise-if-present user-promise-map (.value %)))
          stop-bank-f (clients/consume-all-from-now app-id "bank_queries_feedback"
                                                    #(resolve-promise-if-present bank-promise-map (.value %)))]
      (-> this
          (assoc :user-promise-map user-promise-map)
          (assoc :bank-promise-map bank-promise-map)
          (assoc :stop-user-f stop-bank-f)
          (assoc :stop-bank-f stop-user-f))))

  (stop [this]
    ((:stop-user-f this))
    ((:stop-bank-f this))
    (-> this
        (assoc :user-promise-map nil)
        (assoc :bank-promise-map nil)
        (assoc :stop-user-f nil)
        (assoc :stop-bank-f nil))))

(defn new-query-bus
  []
  {:query-bus (-> {}
                  map->QueryBus
                  (component/using [:kafka-producer]))})
