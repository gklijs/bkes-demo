(ns nl.openweb.projector.user-event-handlers
  (:require [nl.openweb.projector.db :as db])
  (:import (nl.openweb.data UserAccountCreatedEvent)
           (org.apache.kafka.clients.consumer ConsumerRecord)))

(defn handle-user-created
  [^UserAccountCreatedEvent event]
  (db/add-to-db! :users (.getUsername event)
                 {:username (.getUsername event)
                  :password (.getPassword event)}))

(defn handle-event
  [^ConsumerRecord record]
  (let [event (.value record)]
    (condp instance? event
      UserAccountCreatedEvent (handle-user-created event))))
