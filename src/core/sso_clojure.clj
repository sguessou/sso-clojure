(ns core.sso-clojure
  (:require
   [clj-http.client :as client]
   [muuntaja.middleware :as muuntaja]
   [reitit.ring :as reitit]
   [ring.adapter.jetty :as jetty]
   [ring.util.http-response :as response]
   [ring.util.response :refer [redirect]]
   [ring.middleware.reload :refer [wrap-reload]]
   [selmer.parser :as selmer]
   [selmer.middleware :refer [wrap-error-page]]
   [taoensso.timbre :as timbre
    :refer [info log debug error warn]]))

(def auth-url "http://localhost:8080/auth/realms/sso-test/protocol/openid-connect/auth")

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
   (selmer/render-file "login.html" {:title "~=[L0GIN]=~"})))

(defn login-handler [request]
  ;; create a redirect URL for authentication endpoint.
  (let [query-string (client/generate-query-string
                      {:client_id "billingApp"
                       :response_type "code"
                       :redirect_uri "http://localhost:3000/auth-code-redirect"})]
    (redirect (str auth-url "?" query-string))))

(defn auth-code-redirect [request]
  (info (:query-string request))
  (response/ok
   (str "hello world!")))

(def routes
  [["/" {:get home-handler}]
   ["/login" {:get login-handler}]
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
