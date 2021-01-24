(ns nl.openweb.command-handler.user-event-handlers
  (:require [nl.openweb.command-handler.db :as db])
  (:import (nl.openweb.data UserAddedToBankAccountEvent)
           (org.apache.kafka.clients.consumer ConsumerRecord)))

(defn handle-user-added-to-bank-account-event
  [^UserAddedToBankAccountEvent event]
  (db/update-in-db! :users (.getIban event)
                    (fn [m] (update m :users #(conj % (.getUsername event))))))

(defn handle-event
  [^ConsumerRecord record]
  (let [event (.value record)]
    (condp instance? event
      UserAddedToBankAccountEvent (handle-user-added-to-bank-account-event event))))
