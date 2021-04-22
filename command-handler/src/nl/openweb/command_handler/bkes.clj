(ns nl.openweb.command-handler.bkes
  (:require [nl.openweb.topology.clients :as clients])
  (:import (tech.gklijs.bkes.client BlockingClient)))

(defonce deserializer (clients/get-deserializer))
(defonce serializer (clients/get-serializer))
(defonce bkes {:bank     (BlockingClient. "bkes-bank" 50031)
               :user     (BlockingClient. "bkes-user" 50032)
               :transfer (BlockingClient. "bkes-transfer" 50034)})
(defonce topics {:bank     "bank-events"
                 :user     "user-events"
                 :transfer "transfer-events"})

(defn start
  [key avro-instance type]
  (let [bytes (.serialize serializer (type topics) avro-instance)]
    (.start (type bkes) key bytes)))

(defn add
  [key avro-instance type order]
  (let [bytes (.serialize serializer (type topics) avro-instance)]
    (.add (type bkes) key bytes order)))

(defn retrieve
  [key type]
  (.retrieve (type bkes) key))