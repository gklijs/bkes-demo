(defproject nl.openweb/graphql-endpoint "0.1.0-SNAPSHOT"
  :plugins [[walmartlabs/shared-deps "0.2.8"]]
  :dependencies [[clojurewerkz/money "1.10.0"]
                 [crypto-password "0.2.1"]
                 [com.stuartsierra/component "0.4.0"]
                 [com.walmartlabs/lacinia-pedestal "0.12.0" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [org.slf4j/slf4j-api "2.0.0-alpha1"]]
  :dependency-sets [:clojure nl.openweb/topology]
  :main nl.openweb.graphql-endpoint.core
  :profiles {:uberjar {:omit-source  true
                       :aot          [nl.openweb.graphql-endpoint.core]
                       :uberjar-name "ge-docker.jar"}
             :viz {:dependencies [[walmartlabs/system-viz "0.4.0"]]
                   :main nl.openweb.graphql-endpoint.viz}})
