(defproject tech.gklijs/bkes-demo "0.1.0-SNAPSHOT"
  :description "Event Sourcing / CQRS bank demo"
  :license {:name "MIT License"
            :url  "https://opensource.org/licenses/MIT"
            :key  "mit"
            :year 2021}
  :dependencies [[org.clojure/clojure "1.10.2"]
                 [org.clojure/core.specs.alpha "0.2.56"]
                 [org.clojure/spec.alpha "0.2.194"]]
  :plugins [[lein-sub "0.3.0"]]
  :sub ["topology" "synchronizer" "command-handler" "projector" "graphql-endpoint"])
