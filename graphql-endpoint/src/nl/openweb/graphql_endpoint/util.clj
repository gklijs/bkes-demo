(ns nl.openweb.graphql-endpoint.util
  (:require [nl.openweb.topology.value-generator :refer [bytes->uuid uuid->bytes]])
  (:import (java.util UUID)
           (nl.openweb.data Id)))

(defn new-id
  []
  (Id. (uuid->bytes (UUID/randomUUID))))
