(ns core.billing-service
  (:require  [cheshire.core :refer [generate-string parse-string]]
             [clj-http.client :as client]
             [clojure.walk :refer [keywordize-keys]]
             [muuntaja.middleware :as muuntaja]
             [reitit.ring :as reitit]
             [ring.adapter.jetty :as jetty]
             [ring.util.http-response :as response]
             [ring.util.response :refer [redirect]]
             [ring.middleware.reload :refer [wrap-reload]]
             [ring.util.codec :as codec]
             [selmer.parser :as selmer]
             [selmer.middleware :refer [wrap-error-page]]
             [taoensso.timbre :as timbre
              :refer [info log debug error warn]]))

(def services {:services ["electric" "phone" "internet" "water"]})

(defn wrap-nocache [handler]
  (fn [request]
    (-> request
        handler
        (assoc-in [:headers "Pragma"] "no-cache"))))

(defn wrap-formats [handler]
  (-> handler
      (muuntaja/wrap-format)))

(defn response-handler [request]
  (response/ok
   (str "<html><body>your IP is: "
        (:remote-addr request)
        "</body></html>")))

(defn home-handler [request]
  (response/ok
   (generate-string services)))

(def routes
  [["/billing/v1" 
    ["/services" {:get home-handler}]]])

(def handler
  (reitit/routes
   (reitit/ring-handler
    (reitit/router routes))
   (reitit/create-resource-handler
    {:path "/"})
   (reitit/create-default-handler
    {:not-found
     (constantly (response/not-found "404 - Page not found"))
     :method-not-allowed
     (constantly (response/method-not-allowed "405 - Not allowed"))
     :not-acceptable
     (constantly (response/not-acceptable "406 - Not acceptable"))})))

(defn -main []
  (jetty/run-jetty
   (-> #'handler
       wrap-nocache
       wrap-reload)
   {:port 4000
    :join? false}))
