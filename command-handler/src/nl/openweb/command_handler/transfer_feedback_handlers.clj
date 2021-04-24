(ns nl.openweb.command-handler.transfer-feedback-handlers
  (:require [nl.openweb.command-handler.db :as db]
            [nl.openweb.topology.clients :as clients]
            [nl.openweb.topology.value-generator :as vg])
  (:import (nl.openweb.data CreditMoneyCommand
                            MarkTransferFailedCommand ReturnMoneyCommand
                            CommandSucceeded CommandFailed CommandName
                            MarkTransferCompletedCommand)
           (org.apache.kafka.clients.producer KafkaProducer)
           (org.apache.kafka.clients.consumer ConsumerRecord)))

(defn handle-success
  [^KafkaProducer producer ^CommandSucceeded feedback]
  (let [id (.getId feedback)]
    (if-let [transfer (db/get-from-db :bank-transfers-running id)]
      (condp = (.getCommandName feedback)
        CommandName/DebitMoneyCommand
        (do
          (db/update-in-db! :bank-transfers-running id #(assoc % :debited true))
          (if (vg/valid-open-iban (:to transfer))
            (clients/produce producer "transfer_commands" (:to transfer)
                             (CreditMoneyCommand. id (:to transfer) (:amount transfer)))
            (clients/produce producer "bank_commands" (vg/identifier->string id)
                             (MarkTransferCompletedCommand. id))))
        CommandName/CreditMoneyCommand
        (clients/produce producer "bank_commands" (vg/identifier->string id)
                         (MarkTransferCompletedCommand. id))
        CommandName/ReturnMoneyCommand
        (clients/produce producer "bank_commands" (vg/identifier->string id)
                         (MarkTransferFailedCommand. id (:reason transfer)))))))

(defn handle-failure
  [^KafkaProducer producer ^CommandFailed feedback]
  (let [id (.getId feedback)]
    (if-let [transfer (db/get-from-db :bank-transfers-running id)]
      (condp = (.getCommandName feedback)
        CommandName/DebitMoneyCommand
        (clients/produce producer "bank_commands" (vg/identifier->string id)
                         (MarkTransferFailedCommand. id (.getReason feedback)))
        CommandName/CreditMoneyCommand
        (do
          (db/update-in-db! :bank-transfers-running id #(assoc % :reason (.getReason feedback)))
          (if (:debited transfer)
            (clients/produce producer "transfer_commands" (:from transfer)
                             (ReturnMoneyCommand. id (:from transfer) (:amount transfer) (.getReason feedback)))
            (clients/produce producer "bank_commands" (vg/identifier->string id)
                             (MarkTransferFailedCommand. id (:reason transfer)))))
        CommandName/ReturnMoneyCommand nil))))

(defn handle-feedback
  [^KafkaProducer producer ^ConsumerRecord record]
  (let [feedback (.value record)]
    (condp instance? feedback
      CommandSucceeded (handle-success producer feedback)
      CommandFailed (handle-failure producer feedback))))


