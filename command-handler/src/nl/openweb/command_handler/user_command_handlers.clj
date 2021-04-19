(ns nl.openweb.command-handler.user-command-handlers
  (:require [nl.openweb.command-handler.db :as db]
            [nl.openweb.command-handler.command-util :as command-util])
  (:import (org.apache.kafka.clients.consumer ConsumerRecord)
           (org.apache.kafka.clients.producer KafkaProducer)
           (nl.openweb.data CreateUserAccountCommand UserAccountCreatedEvent)))

(def feedback (command-util/feedback-function "user_command_feedback" "user_events"))

(defn handle-create-user-account
  [^KafkaProducer producer ^CreateUserAccountCommand command]
  (feedback producer command (.getUsername command)
            (if (db/get-from-db :users (.getUsername command))
              "user already exists"
              (UserAccountCreatedEvent. (.getUsername command) (.getPassword command)))))

(defn handle-command
  [^KafkaProducer producer ^ConsumerRecord record]
  (let [command (.value record)]
    (condp instance? command
      CreateUserAccountCommand (handle-create-user-account producer command))))
