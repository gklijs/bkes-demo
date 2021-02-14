(defproject nl.openweb/topology "0.1.0-SNAPSHOT"
  :plugins [[walmartlabs/shared-deps "0.2.8"]]
  :repositories  [["confluent" "https://packages.confluent.io/maven/"]]
  :dependencies [[clj-time "0.15.2"]]
  :dependency-sets [:abracad :avro :avro-serializer :clojure :jackson :logging]
  :java-source-paths ["target/main/java"]
  :javac-options ["-target" "11" "-source" "11"]
  :prep-tasks ["compile" "javac"]
  :aot :all
  :profiles {:provided {:dependencies [[org.apache.avro/avro-compiler "1.10.1"]]
                        :source-paths ["env/dev/clj"]
                        :aot          [nl.openweb.dev.avro-compile]}})
