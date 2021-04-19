(ns nl.openweb.command-handler.bank-command-handlers
  (:require [nl.openweb.command-handler.command-util :as command-util]
            [nl.openweb.command-handler.db :as db]
            [nl.openweb.topology.value-generator :as vg])
  (:import (org.apache.kafka.clients.producer KafkaProducer)
           (org.apache.kafka.clients.consumer ConsumerRecord)
           (nl.openweb.data AddUserToBankAccountCommand CreateBankAccountCommand MarkTransferCompletedCommand
                            MarkTransferFailedCommand MoneyTransferCommand RemoveUserFromBankAccountCommand
                            UserAddedToBankAccountEvent BankAccountCreatedEvent TransferCompletedEvent
                            TransferFailedEvent TransferStartedEvent UserRemovedFromBankAccountEvent)))

(def bank-feedback (command-util/feedback-function "bank_command_feedback" "bank_events"))
(def transfer-feedback (command-util/feedback-function "bank_command_feedback" "transfer_events"))

(defn handle-add-user
  [^KafkaProducer producer ^AddUserToBankAccountCommand command]
  (bank-feedback producer command (.getIban command)
                 (if-let [account (db/get-from-db :bank-accounts (.getIban command))]
              (if (contains? (:users account) (.getUserToAdd command))
                "user to add is already owner of bank account"
                (if-let [token (get (:users account) (.getUsername command))]
                  (if (= token (.getToken command))
                    (UserAddedToBankAccountEvent. (.getUserToAdd command) (.getIban command) (vg/new-token))
                    "token is not valid")
                  "user is not owner of account"))
              "iban not known")))

(defn handle-create-account
  [^KafkaProducer producer ^CreateBankAccountCommand command]
  (bank-feedback producer command (.getIban command)
                 (cond
              (not (vg/valid-open-iban (.getIban command))) "invalid open iban"
              (db/get-from-db :bank-accounts (.getIban command)) "iban already exist"
              :else (let [token (vg/new-token)]
                      [token (BankAccountCreatedEvent. (.getIban command) token (.getUsername command))]))))

(defn handle-complete-transfer
  [^KafkaProducer producer ^MarkTransferCompletedCommand command]
  (transfer-feedback producer command (vg/identifier->string(.getId command))
                 (if-let [transfer (db/get-from-db :bank-transfers (.getId command))]
              (if
                (= (:state transfer) :started)
                (TransferCompletedEvent. (.getId command))
                (str "state of transfer was " (name (:state transfer)) " so could not be completed"))
              "transfer not known")))

(defn handle-fail-transfer
  [^KafkaProducer producer ^MarkTransferFailedCommand command]
  (transfer-feedback producer command (vg/identifier->string(.getId command))
                 (if-let [transfer (db/get-from-db :bank-transfers (.getId command))]
              (if
                (= (:state transfer) :started)
                [(.getReason command) (TransferFailedEvent. (.getId command) (.getReason command))]
                (str "state of transfer was " (name (:state transfer)) " so could not be failed"))
              "transfer not known")))

(defn handle-start-transfer
  [^KafkaProducer producer ^MoneyTransferCommand command]
  (transfer-feedback producer command (vg/identifier->string(.getId command))
                 (let [id (.getId command)]
              (if-let [transfer (db/get-from-db :bank-transfers id)]
                (str "transfer was already started, current status: " (name (:state transfer)))
                (do
                  (db/add-to-db! :bank-transfers-running id {})
                  (TransferStartedEvent. id (.getToken command) (.getAmount command) (.getFrom command) (.getTo command) (.getDescription command) (.getUsername command)))))))

(defn handle-remove-user
  [^KafkaProducer producer ^RemoveUserFromBankAccountCommand command]
  (bank-feedback producer command (.getIban command)
                 (if-let [account (db/get-from-db :bank-accounts (.getIban command))]
              (if (contains? (:users account) (.getUsername command))
                (if (= (get (:users account) (.getUsername command)) (.getToken command))
                  (if (and (= 1 (count (:users account))) (not (= 0 (:balance account))))
                    "last user and balance is not zero, so can't be removed"
                    (UserRemovedFromBankAccountEvent. (.getUsername command) (.getIban command)))
                  "token is not valid")
                "user to remove is not owner of bank account")
              "iban not known")))

(defn handle-command
  [^KafkaProducer producer ^ConsumerRecord record]
  (let [command (.value record)]
    (condp instance? command
      AddUserToBankAccountCommand (handle-add-user producer command)
      CreateBankAccountCommand (handle-create-account producer command)
      MarkTransferCompletedCommand (handle-complete-transfer producer command)
      MarkTransferFailedCommand (handle-fail-transfer producer command)
      MoneyTransferCommand (handle-start-transfer producer command)
      RemoveUserFromBankAccountCommand (handle-remove-user producer command))))
