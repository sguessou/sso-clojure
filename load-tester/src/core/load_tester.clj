(ns core.load-tester
  (:require [clj-gatling.core :as clj-gatling]
            [org.httpkit.client :as http]))


(defn localhost-request [_]
  (let [{:keys [status]} @(http/get "http://localhost:3000/services")]
    (= status 200)))

(defn simulate []
  (clj-gatling/run
    {:name "Simulation"
     :scenarios [{:name "Localhost test scenario"
                  :steps [{:name "Root"
                           :request localhost-request}]}]}
    {:concurrency 100}))
