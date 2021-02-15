(defproject nl.openweb/synchronizer "0.1.0-SNAPSHOT"
  :plugins [[walmartlabs/shared-deps "0.2.8"]]
  :dependencies [[clj-http "3.12.1"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.14.0"]]
  :dependency-sets [:clojure :json nl.openweb/topology]
  :main nl.openweb.synchronizer.core
  :profiles {:uberjar {:omit-source  true
                       :aot          :all
                       :uberjar-name "syn-docker.jar"}})
