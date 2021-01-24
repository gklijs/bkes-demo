(ns nl.openweb.command-handler.transfer-handlers
  (:require [nl.openweb.command-handler.db :as db]
            [nl.openweb.topology.clients :as clients]
            [nl.openweb.topology.value-generator :as vg]
            [nl.openweb.command-handler.command-util :as command-util])
  (:import (nl.openweb.data TransferStartedEvent DebitMoneyCommand CreditMoneyCommand TransferFailedEvent
                            TransferCompletedEvent MarkTransferFailedCommand ReturnMoneyCommand MoneyDebitedEvent
                            MoneyCreditedEvent MoneyReturnedEvent CommandSucceeded CommandFailed CommandName
                            MarkTransferCompletedCommand)
           (org.apache.kafka.clients.producer KafkaProducer)
           (org.apache.kafka.clients.consumer ConsumerRecord)))

(defn handle-transfer-started
  [^KafkaProducer producer ^TransferStartedEvent event]
  (if (db/get-from-db :bank-transfers-running (.getId event))
    (let [id (.getId event)
          id-s (vg/identifier->string id)
          _ (db/add-to-db! :bank-transfers-running id {:token       (.getToken event)
                                                       :amount      (.getAmount event)
                                                       :from        (.getFrom event)
                                                       :to          (.getTo event)
                                                       :description (.getDescription event)
                                                       :username    (.getUsername event)
                                                       :debited     false})
          [topic key command] (cond
                                (= (.getFrom event) (.getTo event)) ["bank_commands" id-s (MarkTransferFailedCommand. id "from and to can't be same for transfer")]
                                (vg/valid-open-iban (.getFrom event)) ["bank_commands" id-s (MarkTransferFailedCommand. id "from is invalid")]
                                (= (.getFrom event) "cash") ["transfer_commands" (.getTo event) (CreditMoneyCommand. id (.getTo event) (.getAmount event))]
                                :else ["transfer_commands" (.getFrom event) (DebitMoneyCommand. id (.getFrom event) (.getAmount event) (.getToken event) (.getUsername event))])]
      (clients/produce producer topic key command))))

(defn remove-active-if-present
  [transfer-id]
  (if (db/get-from-db :bank-transfers-running transfer-id)
    (db/remove-from-db! :bank-transfers-running transfer-id)))

(defn handle-transfer-completed
  [^TransferCompletedEvent event]
  (remove-active-if-present (.getId event)))

(defn handle-transfer-failed
  [^TransferFailedEvent event]
  (remove-active-if-present (.getId event)))

(def feedback (command-util/feedback-function "transfer_command_feedback" "bank_events"))

(defn handle-debit-money
  [^KafkaProducer producer ^DebitMoneyCommand command]
  (feedback producer command
            (if-let [account (db/get-from-db :bank-accounts (.getIban command))]
              (if-let [token (get (:users account) (.getUsername command))]
                (if (= token (.getToken command))
                  (if (> (- (:balance account) (.getAmount command)) (:limit account))
                    (MoneyDebitedEvent. (.getIban command) (.getAmount command) (.getId command))
                    (str "insufficient funds would be below the limit of " (:limit account)))
                  "token is not valid")
                "user is no owner of bank account")
              "iban not known")))

(defn handle-credit-money
  [^KafkaProducer producer ^CreditMoneyCommand command]
  (feedback producer command
            (if (db/get-from-db :bank-accounts (.getIban command))
              (MoneyCreditedEvent. (.getIban command) (.getAmount command) (.getId command))
              "iban not known")))

(defn handle-return-money
  [^KafkaProducer producer ^ReturnMoneyCommand command]
  (feedback producer command
            (if (db/get-from-db :bank-accounts (.getIban command))
              (MoneyReturnedEvent. (.getIban command) (.getAmount command) (.getId command) (.getReason command))
              "iban not known")))

(defn handle-command
  [^KafkaProducer producer ^ConsumerRecord record]
  (let [command (.value record)]
    (condp instance? command
      CreditMoneyCommand (handle-credit-money producer command)
      DebitMoneyCommand (handle-debit-money producer command)
      ReturnMoneyCommand (handle-return-money producer command))))

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


