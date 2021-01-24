(ns nl.openweb.projector.user-query-handlers
  (:require [nl.openweb.projector.db :as db]
            [nl.openweb.projector.query-util :as query-util])
  (:import (nl.openweb.data FindUserQuery)
           (org.apache.kafka.clients.consumer ConsumerRecord)
           (org.apache.kafka.clients.producer KafkaProducer)))

(def feedback (query-util/feedback-function "user_queries_feedback"))

(defn find-user
  [^KafkaProducer producer ^FindUserQuery query]
  (feedback producer query
            (if-let [user (db/get-from-db :users (.getUsername query))]
              user
              "username does not exist")))

(defn handle-query
  [^KafkaProducer producer ^ConsumerRecord record]
  (let [query (.value record)]
    (condp instance? query
      FindUserQuery (find-user producer query))))