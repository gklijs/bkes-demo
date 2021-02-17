(ns nl.openweb.graphql-endpoint.launcher
  "AOT-prevention dynamic loader stub for `nl.openweb.graphql-endpoint.start`."
  (:gen-class))

(defn -main
  "Chain to core.clj, see https://github.com/timmc/lein-otf"
  [& args]
  (let [main-ns 'nl.openweb.graphql-endpoint.start]
    (require main-ns)
    (apply (ns-resolve main-ns '-main) args)))