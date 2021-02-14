(ns nl.openweb.graphql-endpoint.company-account-initiator
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [nl.openweb.graphql-endpoint.command-bus :as command-bus]
            [nl.openweb.graphql-endpoint.util :refer [new-id]])
  (:import (nl.openweb.data CreateBankAccountCommand MoneyTransferCommand)))

(defn open-company-account
  [db]
  (let [id (new-id)
        cb (:command-bus db)
        command-feedback (command-bus/issue-command cb (CreateBankAccountCommand. id "NL66OPEN0000000000" "openweb"))]
    (if (:success command-feedback)
      (command-bus/issue-command cb (MoneyTransferCommand.
                                      id
                                      "cash"
                                      100000000000000000
                                      "cash"
                                      "NL66OPEN0000000000"
                                      "initial funds"
                                      "openweb")))))

(defrecord Initiator []
  component/Lifecycle
  (start [this]
    (open-company-account this)
    this)
  (stop [this] this))

(defn init
  []
  {:initiator (-> {}
                  map->Initiator
                  (component/using [:command-bus]))})
