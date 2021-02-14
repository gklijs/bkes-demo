(ns nl.openweb.graphql-endpoint.account-creation-service
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [crypto.password.pbkdf2 :as crypto]
            [nl.openweb.graphql-endpoint.command-bus :as command-bus]
            [nl.openweb.graphql-endpoint.query-bus :as query-bus]
            [nl.openweb.graphql-endpoint.util :refer [new-id]]
            [nl.openweb.topology.value-generator :refer [bytes->uuid]]
            [nl.openweb.topology.value-generator :as vg])
  (:import (nl.openweb.data FindUserQuery CreateUserAccountCommand CreateBankAccountCommand
                            FindBankAccountsForUserQuery)))

(defn- error
  [feedback]
  {:iban   nil
   :token  nil
   :reason (:reason feedback)})

(defn- success
  [query-feedback username]
  (let [bank-account (first (:result query-feedback))]
    {:iban   (:iban bank-account)
     :token  (get (:users bank-account) username)
     :reason nil}))

(defn- success-new
  [iban command-feedback]
  {:iban   iban
   :token  (:additional-info command-feedback)
   :reason nil})

(defn get-account
  [db username password]
  (let [id (new-id)
        qb (:query-bus db)
        cb (:command-bus db)
        existing-account (query-bus/issue-query qb (FindUserQuery. id username))]
    (cond
      (:timed-out existing-account)
      (error existing-account)
      (:failure existing-account)
      (let [command-feedback (command-bus/issue-command cb (CreateUserAccountCommand. id username (crypto/encrypt password)))]
        (if (:success command-feedback)
          (let [iban (vg/new-iban)
                command-feedback (command-bus/issue-command cb (CreateBankAccountCommand. id iban username))]
            (if (:success command-feedback)
              (success-new iban command-feedback)
              (error command-feedback)))
          (error command-feedback)))
      (:success existing-account)
      (if
        (crypto/check password (:password (:result existing-account)))
        (let [query-feedback (query-bus/issue-query qb (FindBankAccountsForUserQuery. id username))]
          (cond
            (:failure query-feedback) (error query-feedback)
            (empty? (:result query-feedback)) (error {:reason "no linked bank account found"})
            :else (success query-feedback username)))
        (error {:reason "invalid password"})))))

(defrecord AccountCreationService []
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn new-service
  []
  {:account-creation-service (-> {}
                                 map->AccountCreationService
                                 (component/using [:query-bus :command-bus]))})
