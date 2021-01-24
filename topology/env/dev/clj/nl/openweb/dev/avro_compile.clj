(ns nl.openweb.dev.avro-compile
  (:require [abracad.avro :as avro]
            [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import (org.apache.avro Schema)
           (org.apache.avro.compiler.specific SpecificCompiler SpecificCompiler$FieldVisibility)
           (java.io File)
           (org.apache.avro.generic GenericData$StringType))
  (:gen-class))

(defn fill-fields
  [sub-types field]
  (if (keyword? field)
    {:name (name field) :type (field sub-types)}
    field))

(defn schema-reducer
  [sub-types additional-fields m name fields]
  (conj m (avro/parse-schema (-> {}
                                 (assoc :type :record)
                                 (assoc :name name)
                                 (assoc :namespace "nl.openweb.data")
                                 (assoc :fields (mapv #(fill-fields sub-types %) (concat additional-fields fields)))))))

(defn add-enum-types
  [schema-c]
  (let [command-names (vec (keys (:commands schema-c)))
        query-names (vec (keys (:queries schema-c)))]
    (-> schema-c
        (assoc-in [:types :command-name] {:name "CommandName" :type :enum :symbols command-names})
        (assoc-in [:types :query-name] {:name "QueryName" :type :enum :symbols query-names}))))

(defonce schema-config (add-enum-types (-> (io/resource "schemas.edn") slurp edn/read-string)))

(defn commands
  []
  (reduce-kv (partial schema-reducer (:types schema-config) [:id]) [] (:commands schema-config)))

(defn events
  []
  (reduce-kv (partial schema-reducer (:types schema-config) []) [] (:events schema-config)))

(defn feedback
  []
  (reduce-kv (partial schema-reducer (:types schema-config) [:id]) [] (:feedback schema-config)))

(defn queries
  []
  (reduce-kv (partial schema-reducer (:types schema-config) [:id]) [] (:queries schema-config)))

(defn derived-events
  []
  (reduce-kv (partial schema-reducer (:types schema-config) []) [] (:derived-events schema-config)))

(defn compile-schemas
  [schemas]
  (doseq [^Schema schema schemas]
    (let [compiler (SpecificCompiler. schema)
          _ (doto compiler
              (.setFieldVisibility SpecificCompiler$FieldVisibility/PRIVATE)
              (.setStringType GenericData$StringType/String))
          java-target-path "target/main/java"
          file (File. "")
          dest (File. ^String java-target-path)]
      (.compileToDestination compiler file dest))))

(compile-schemas
  (concat (commands) (events) (feedback) (queries) (derived-events)))


