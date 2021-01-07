(ns core.billing-service
  (:require  [cheshire.core :refer [generate-string parse-string decode]]
             [clj-http.client :as client]
             [clojure.walk :refer [keywordize-keys]]
             [muuntaja.middleware :as muuntaja]
             [reitit.ring :as reitit]
             [ring.adapter.jetty :as jetty]
             [ring.util.http-response :as response]
             [ring.util.response :refer [redirect]]
             [ring.middleware.reload :refer [wrap-reload]]
             [ring.middleware.params :refer [wrap-params]]
             [ring.util.codec :as codec]
             [selmer.parser :as selmer]
             [selmer.middleware :refer [wrap-error-page]]
             [taoensso.timbre :as log])
  (:import [org.apache.commons.codec.binary Base64]))


(def config {:token-introspection "http://localhost:8080/auth/realms/sso-test/protocol/openid-connect/token/introspect"
             :client-id "tokenChecker"
             :client-password "b5393f3f-9149-4d6f-bf7d-edc36a504388"})

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

(defn- get-token [request]
  (cond
    ;; headers
    (some? (get-in request [:headers "authorization"])) 
    (do 
      (log/info ::get-token {:method :authorization})
      (get-in request [:headers "authorization"]))
    
    ;; form-params
    (some? (get-in request [:form-params "access_token"]))
    (do
      (log/info ::get-token {:method :form-body})
      (get-in request [:form-params "access_token"]))
    
    ;; query
    (some? (get-in request [:query-params "access_token"]))
    (do 
      (log/info ::get-token {:method :query-params})
      (get-in request [:query-params "access_token"]))
    
    :else nil))

(defn- validate-token [token]
  (let [response
        (client/post (:token-introspection config)
                     {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                      :basic-auth [(:client-id config) (:client-password config)]
                      :form-params {:token_type_hint "requesting_party_token"
                                    :token (clojure.string/replace token #"Bearer " "")}})
        active (-> response
                   :body
                   parse-string
                   (get "active"))]
    (log/info ::validate-token {:active active})
    active))

(defn- decode-token-claim [token]
  (let [claim (-> token
                  (clojure.string/split #"\.")
                  second
                  Base64/decodeBase64
                  String.
                  decode)]
    (log/info ::check-token-scope {:claim claim})
    claim))

(defn services-handler [request]
  (log/info ::services-handler {:headers (keys (:headers request))})
  (if-let [token (get-token request)]
    (do
      (let [valid (validate-token token)
            scope (-> (decode-token-claim token)
                      (get "scope")
                      (clojure.string/split #" ")
                      set)]
        (log/info ::services-handler {:valid-token valid :scope scope})
        (cond
          (false? valid) 
          (do 
            (log/info ::services-handler {:error "Invalid token"})               
            (response/bad-request "Invalid token"))
          (nil? (get scope "getBillingService")) 
          (do
            (log/info ::services-handler {:error "Invalid token scope. Required scope [getBillingService]"})
            (response/bad-request "Invalid scope"))
          :else (response/ok (generate-string services)))))
    (do
      (log/info ::services-handler {:error "Missing access token"})
      (response/forbidden "Missing access token"))))

(defn test-request-handler [request]
  (let [{params :params 
         form-params :form-params 
         query-params :query-params
         headers :headers} request]
    (log/info ::services-post {:request {:form-params form-params
                                         :query-params query-params
                                         :params params
                                         :headers headers
                                         :result (validate-token (get-token request))}})
    (response/ok "OK")))

(def routes
  [["/billing/v1" {:middleware [wrap-params]}
    ["/services" {:get services-handler
                  :post test-request-handler}]]])

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
