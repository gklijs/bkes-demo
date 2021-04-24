(ns nl.openweb.command-handler.bkes
  (:require [nl.openweb.topology.clients :as clients]
            [nl.openweb.topology.value-generator :as vg])
  (:import (io.confluent.kafka.serializers KafkaAvroDeserializer KafkaAvroSerializer)
           (nl.openweb.data Id)
           (tech.gklijs.bkes.client BlockingClient)
           (tech.gklijs.bkes.api RetrieveReply Record StartReply AddReply)))

(defonce ^KafkaAvroDeserializer deserializer (clients/get-deserializer))
(defonce ^KafkaAvroSerializer serializer (clients/get-serializer))
(defonce bkes {:bank     (BlockingClient. "bkes-bank" 50031)
               :user     (BlockingClient. "bkes-user" 50032)
               :transfer (BlockingClient. "bkes-transfer" 50034)})
(defonce topics {:bank     "bank_events"
                 :user     "user_events"
                 :transfer "transfer_events"})

(defn get-string-key
  [key]
  (condp instance? key
    String key
    Id (vg/identifier->string key)))

(defn start
  [key avro-instance type]
  (let [string-key (get-string-key key)
        bytes (.serialize serializer (type topics) avro-instance)
        result ^StartReply (.start (type bkes) string-key bytes)]
    (if (.hasError result)
      (.getError (.getError result))
      true)))

(defn add
  [key avro-instance type order]
  (let [string-key (get-string-key key)
        bytes (.serialize serializer (type topics) avro-instance)
        result ^AddReply (.add (type bkes) string-key bytes order)]
    (if (.hasError result)
      (.getError (.getError result))
      true)))

(defn to-avro
  [^Record bkes-record]
  (.deserialize deserializer nil (.toByteArray (.getValue bkes-record))))

(defn retrieve
  [key type]
  (let [string-key (get-string-key key)
        ^RetrieveReply result (.retrieve (type bkes) string-key)]
    (if (.hasSuccess result)
      (map to-avro (.getRecordsList (.getSuccess result)))
      [])))