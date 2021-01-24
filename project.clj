(defproject nl.openweb/kafka-graphql-examples "0.1.0-SNAPSHOT"
  :plugins [[lein-modules "0.3.11"]]
  :modules {:inherited
                      {:repositories  [["confluent" "https://packages.confluent.io/maven/"]]
                       :aliases       {"all" ^:displace ["do" "clean," "test," "install", "uberjar"]
                                       "-f"  ["with-profile" "+fast"]}
                       :scm           {:dir ".."}
                       :javac-options ["-target" "11" "-source" "11"]
                       :license       {:name "MIT License"
                                       :url  "https://opensource.org/licenses/MIT"
                                       :key  "mit"
                                       :year 2021}}
            :versions {ch.qos.logback/logback-classic                 "1.3.0-alpha5"
                       com.damballa/abracad                           "0.4.14-alpha2"
                       com.fasterxml.jackson.core/jackson-annotations "2.12.1"
                       com.fasterxml.jackson.core/jackson-core        "2.12.1"
                       com.fasterxml.jackson.core/jackson-databind    "2.12.1"
                       hikari-cp/hikari-cp                            "2.12.0"
                       io.confluent/kafka-avro-serializer             "5.5.0"
                       org.apache.avro/avro                           "1.10.1"
                       org.clojure/clojure                            "1.10.2"
                       org.clojure/data.json                          "1.0.0"
                       org.clojure/tools.logging                      "1.1.0"}})
