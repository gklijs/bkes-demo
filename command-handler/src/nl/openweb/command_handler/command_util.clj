(ns nl.openweb.command-handler.command-util
  (:require [nl.openweb.topology.clients :as clients]
            [nl.openweb.topology.value-generator :as vg])
  (:import (nl.openweb.data CommandName CommandFailed CommandSucceeded)))

(defn feedback-function
  [feedback-topic event-topic]
  (fn [producer command result]
    (let [cn (CommandName/valueOf (.getSimpleName (.getClass command)))
          id (.getId command)
          id-string (vg/identifier->string id)]
      (cond
        (string? result) (clients/produce producer feedback-topic id-string (CommandFailed. id cn result))
        (coll? result) (do
                         (clients/produce producer feedback-topic id-string (CommandSucceeded. id cn (first result)))
                         (clients/produce-without-key producer event-topic (second result)))
        :else (do
                (clients/produce producer feedback-topic id-string (CommandSucceeded. id cn nil))
                (clients/produce-without-key producer event-topic result))))))

