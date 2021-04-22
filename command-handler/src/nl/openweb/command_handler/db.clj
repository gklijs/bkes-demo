(ns nl.openweb.command-handler.db)

(defonce bank-accounts (atom {}))
(defonce bank-transfers (atom {}))
(defonce bank-transfers-running (atom {}))
(defonce users (atom {}))

(defn get-db [type]
  (condp = type
    :bank-accounts bank-accounts
    :bank-transfers bank-transfers
    :bank-transfers-running bank-transfers-running
    :users users
    nil))

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


