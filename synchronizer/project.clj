(defproject nl.openweb/synchronizer "0.1.0-SNAPSHOT"
  :plugins [[walmartlabs/shared-deps "0.2.8"]]
  :dependencies [[clj-http "3.10.0" :exclusions [commons-logging]]
                 [org.slf4j/jcl-over-slf4j "2.0.0-alpha1"]]
  :dependency-sets [:clojure :json nl.openweb/topology]
  :main nl.openweb.synchronizer.core
  :profiles {:uberjar {:omit-source  true
                       :aot          :all
                       :uberjar-name "syn-docker.jar"}})
