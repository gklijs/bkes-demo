(ns nl.openweb.command-handler.command-util
  (:require [nl.openweb.command-handler.bkes :as bkes]
            [nl.openweb.topology.clients :as clients]
            [nl.openweb.topology.value-generator :as vg])
  (:import (nl.openweb.data CommandName CommandFailed CommandSucceeded)))

(defn send-via-bkes
  [key order event type]
  (if (= order 0)
    (bkes/start key event type)
    (bkes/add key event type order)))

(defn handle-bkes-result
  [bkes-result producer feedback-topic id cn id-string optional-success-string]
  (if (string? bkes-result)
    (clients/produce producer feedback-topic id-string (CommandFailed. id cn bkes-result))
    (clients/produce producer feedback-topic id-string (CommandSucceeded. id cn optional-success-string))))

(defn feedback-function
  [feedback-topic type]
  (fn [producer command key result]
    (let [cn (CommandName/valueOf (.getSimpleName (.getClass command)))
          id (.getId command)
          id-string (vg/identifier->string id)]
      (if (string? result)
        (clients/produce producer feedback-topic id-string (CommandFailed. id cn result))
        (let [bkes-result (send-via-bkes key (:order result) (:event result) type)]
          (handle-bkes-result bkes-result producer feedback-topic id cn id-string (:optional-success-string result)))))))

(defn invalid-from
  [from]
  (cond
    (= from "cash") false
    :else (not (vg/valid-open-iban from))))
