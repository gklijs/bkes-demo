(ns nl.openweb.command-handler.bkes
  (:require [nl.openweb.topology.clients :as clients])
  (:import (tech.gklijs.bkes.client BlockingClient)))

(defonce decoder (clients/get-decoder))
(defonce serializer (clients/get-serializer))
(defonce bkes-bank (BlockingClient. "bkes-bank" 50031))
(defonce bkes-user (BlockingClient. "bkes-user" 50032))
(defonce bkes-transfer (BlockingClient. "bkes-transfer" 50034))

