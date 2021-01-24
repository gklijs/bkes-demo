(ns nl.openweb.projector.db)

(defonce bank-accounts (atom {}))
(defonce bank-transfers (atom {}))
(defonce ibans-by-user (atom {}))
(defonce bank-transactions (atom []))
(defonce transactions-by-iban (atom {}))
(defonce users (atom {}))

(defn get-db [type]
  (condp = type
    :bank-accounts bank-accounts
    :bank-transfers bank-transfers
    :ibans-by-user ibans-by-user
    :transactions-by-iban transactions-by-iban
    :users users
    nil))

(defn get-from-db [type id]
  (get @(get-db type) id))

(defn add-to-db! [type id entry]
  (swap! (get-db type) assoc id entry))

(defn update-in-db! [type id update-function]
  (let [new-db (swap! (get-db type) update id update-function)]
    (get new-db id)))

(defn add-transaction! [transaction]
  (let [id (-> (swap! bank-transactions #(conj % transaction))
               count
               int)
        transaction-with-id (assoc transaction :id id)]
    (swap! transactions-by-iban update (:iban transaction) (fn [old] (conj old transaction-with-id)))
    transaction-with-id))

(defn get-transaction
  [transaction-id]
  (nth @bank-transactions (- transaction-id 1) nil))

(defn get-all-last-transactions
  []
  (->> (into (sorted-map) @transactions-by-iban)
       vals
       (remove empty?)
       (map last)
       vec))

(defn add-iban-for-user!
  [user iban]
  (swap! ibans-by-user
         (fn [current]
           (if-let [ibans (get current user)]
             (assoc current user (conj ibans iban))
             (assoc current user [iban])))))


