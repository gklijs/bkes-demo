(ns nl.openweb.command-handler.db
  (:require [nl.openweb.command-handler.bkes :as bkes]))

(defonce bank-accounts (atom {}))
(defonce bank-transfers (atom {}))
(defonce bank-transfers-running (atom {}))
(defonce users (atom {}))
(defonce db-type-bkes-mapping {:bank-accounts  :bank
                               :users          :user
                               :bank-transfers :transfer})

(defn get-db [type]
  (condp = type
    :bank-accounts bank-accounts
    :bank-transfers bank-transfers
    :bank-transfers-running bank-transfers-running
    :users users
    nil))

(defn update-from-bkes
  [type id bkes-update-function]
  (if-let [bkes-type (type db-type-bkes-mapping)]
    (bkes-update-function (bkes/retrieve id bkes-type))))

(defn get-from-db-optional-read-from-bkes [type id bkes-update-function]
  (if-let [result (get @(get-db type) id)]
    result
    (do
      (update-from-bkes type id bkes-update-function)
      (get @(get-db type) id))))

(defn get-from-db [type id]
  (get @(get-db type) id))

(defn add-to-db! [type id entry]
  (swap! (get-db type) assoc id (assoc entry :order 0)))

(defn update-in-db! [type id update-function]
  (if
    (get-from-db type id)
    (swap! (get-db type) update id
           (fn [m] (update (update-function m) :order #(+ 1 %))))))

(defn remove-from-db! [type id]
  (swap! (get-db type) #(dissoc % id)))


