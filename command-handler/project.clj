(defproject nl.openweb/command-handler "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[walmartlabs/shared-deps "0.2.8"]]
  :dependency-sets [:clojure :money nl.openweb/topology]
  :main nl.openweb.command-handler.core
  :profiles {:uberjar {:omit-source  true
                       :aot          :all
                       :uberjar-name "ch-docker.jar"}})
