(ns nl.openweb.projector.query-util
  (:require [nl.openweb.topology.clients :as clients]
            [nl.openweb.topology.value-generator :as vg])
  (:import (nl.openweb.data QueryName QueryFailed QuerySucceeded)))

(defn feedback-function
  [feedback-topic]
  (fn [producer query result]
    (let [cn (QueryName/valueOf (.getSimpleName (.getClass query)))
          id (.getId query)
          id-string (vg/identifier->string id)]
      (if
        (string? result)
        (clients/produce producer feedback-topic id-string (QueryFailed. id cn result))
        (clients/produce producer feedback-topic id-string (QuerySucceeded. id cn (prn-str result)))))))