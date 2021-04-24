(ns nl.openweb.command-handler.user-event-handlers
  (:require [nl.openweb.command-handler.db :as db])
  (:import (nl.openweb.data UserAddedToBankAccountEvent)
           (org.apache.kafka.clients.consumer ConsumerRecord)))

(defn handle-user-added-to-bank-account-event
  [^UserAddedToBankAccountEvent event]
  (db/update-in-db! :users (.getIban event)
                    (fn [m] (update m :users #(conj % (.getUsername event))))))

(defn handle-event
  [event]
  (condp instance? event
    UserAddedToBankAccountEvent (handle-user-added-to-bank-account-event event)))

(defn handle-event-kafka
  [^ConsumerRecord record]
  (handle-event (.value record)))

(defn handle-event-bkes
  [event-list]
  (doseq [event event-list] (handle-event event)))
