(ns nl.openweb.command-handler.user-command-handlers
  (:require [nl.openweb.command-handler.db :as db]
            [nl.openweb.command-handler.command-util :as command-util]
            [nl.openweb.command-handler.user-event-handlers :as user-event-handlers])
  (:import (org.apache.kafka.clients.consumer ConsumerRecord)
           (org.apache.kafka.clients.producer KafkaProducer)
           (nl.openweb.data CreateUserAccountCommand UserAccountCreatedEvent)))

(def feedback (command-util/feedback-function "user_command_feedback" :user))

(defn handle-create-user-account
  [^KafkaProducer producer ^CreateUserAccountCommand command]
  (feedback producer command (.getUsername command)
            (if (db/get-from-db-optional-read-from-bkes :users (.getUsername command) user-event-handlers/handle-event-bkes)
              "user already exists"
              {:event (UserAccountCreatedEvent. (.getUsername command) (.getPassword command))
               :order 0})))

(defn handle-command
  [^KafkaProducer producer ^ConsumerRecord record]
  (let [command (.value record)]
    (condp instance? command
      CreateUserAccountCommand (handle-create-user-account producer command))))
