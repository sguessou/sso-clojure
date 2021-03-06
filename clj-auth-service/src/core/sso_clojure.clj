(ns core.sso-clojure
  (:require
   [cheshire.core :refer [generate-string parse-string]]
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
   [slingshot.slingshot :refer [try+ throw+]]
   [taoensso.timbre :as log]
   [taoensso.carmine :as car]))

(def redis-conn {:pool {} :spec {:host "127.0.0.1"
                                 :port 6377}})
(defmacro wcar* [& body] `(car/wcar redis-conn ~@body))

(def config
  {:auth-url "http://localhost:8080/auth/realms/sso-test/protocol/openid-connect/auth"
   :logout-url "http://localhost:8080/auth/realms/sso-test/protocol/openid-connect/logout"
   :token-endpoint "http://localhost:8080/auth/realms/sso-test/protocol/openid-connect/token"
   :client-id "billingApp"
   :client-password "fe0a7e01-8b66-4706-8f37-e7d333c29e6f"
   :redirect-uri "http://localhost:3000/auth-code-redirect"
   :landing-page "http://localhost:3000"
   :services-endpoint "http://localhost:4000/billing/v1/services"})

(def app-var (atom {}))

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
   (selmer/render-file "login.html" {:title "~=[λ RuL3z!]=~"
                                     :session (:session-state @app-var)
                                     :code (:code @app-var)
                                     :access-token (get-in @app-var [:token :access_token])
                                     :refresh-token (get-in @app-var [:token :refresh_token])
                                     :scope (get-in @app-var [:token :scope])
                                     :services (:services @app-var)})))

(defn login-handler [request]
  (let [state (.toString (java.util.UUID/randomUUID))]
    (wcar* (car/set (str "STATE-" state) state "EX" 180))
    ;; create a redirect URL for authentication endpoint.
    (let [client_id (:client-id config)
          redirect_uri (:redirect-uri config)
          query-string (client/generate-query-string
                        {:client_id client_id
                         :response_type "code"
                         :redirect_uri redirect_uri
                         :state state})
          auth-url (:auth-url config)]
      (redirect (str auth-url "?" query-string)))))

(defn get-token []
  (client/post (:token-endpoint config)
               {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                :basic-auth [(:client-id config) (:client-password config)]
                :form-params {:grant_type "authorization_code"
                              :code (:code @app-var)
                              :redirect_uri (:redirect-uri config)
                              :client_id (:client-id config)}}))

(defn- format-response [resp]
  (-> resp
      :body
      parse-string
      keywordize-keys))

(defn- exchange-token []
  (let [token (get-token)]
    (swap! app-var assoc :token (format-response token))
    (log/info ::exchange-token {:token (get-in @app-var [:token :token_type])})
    (redirect (:landing-page config))))

(defn auth-code-redirect [request]
  (log/info ::auth-redirect {:query-string (:query-string request)})
  (let [query-params (-> request :query-string codec/form-decode keywordize-keys)
        landing-page (:landing-page config)
        state (:state query-params)]
    (log/info ::auth-code-redirect {:state state})
    (if (nil? (wcar* (car/get (str "STATE-" state))))
      (do
        (log/info ::auth-code-redirect {:error "State Error"})
        (response/bad-request "Error"))
      (do
        (swap! app-var assoc :code (:code query-params)
               :session-state (:session_state query-params))
        (wcar* (car/set (str "STATE-" state) nil))
        (exchange-token)))))

(defn logout-handler [request]
  (let [query-string (client/generate-query-string {:redirect_uri (:landing-page config)})
        logout-url (str (:logout-url config) "?" query-string)]
    (reset! app-var {})
    (redirect logout-url)))

(defn services-handler [request]
  (log/info ::services-handler {:addr (:remote-addr request)})
  ;; redis is used here for debugging purposes only
  #_(wcar* (car/set (str "TEST-TOKEN-" (.toString (java.util.UUID/randomUUID))) "TEST" "EX" 180))
  (if-let [services (try+ 
                     (client/get (:services-endpoint config)
                                 {:headers {"Authorization" (str "Bearer " (get-in @app-var [:token :access_token]))}
                                  :socket-timeout 500
                                  :connection-timeout 500})
                     (catch Object _
                       (log/error ::services "Upstream error")
                       (response/bad-request "upstream-error")))]
    (do
      (swap! app-var assoc :services (-> services :body parse-string (get "services")))
      (log/info ::services {:services services})
      (redirect (:landing-page config)))
    (do 
      (swap! app-var assoc :service {:services []})
      (redirect (:landing-page config)))))

(defn refresh-token [request]
  (let [response
        (client/post (:token-endpoint config)
                     {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                      :basic-auth [(:client-id config) (:client-password config)]
                      :form-params {:grant_type "refresh_token"
                                    :refresh_token (get-in @app-var [:token :refresh_token])}})]
    (log/info ::refresh-token {:token (format-response response)})
    (redirect (:landing-page config))))

(def routes
  [["/" {:get home-handler}]
   ["/login" {:get login-handler}]
   ["/services" {:get services-handler
                 :options services-handler}]
   ["/logout" {:get logout-handler}]
   ["/refresh-token" {:get refresh-token}]
   ["/auth-code-redirect" {:get auth-code-redirect}]])

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
   {:port 3000
    :join? false}))
