(ns frontend.app
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

(defn app []
  [:div.container
   [:h1.title.is-4 "Implicit Grant Type"]
   [:div [:a {:href "/home"} "Home"]]
   [:div [:a {:href "http://localhost:8080/auth/realms/sso-test/protocol/openid-connect/auth?client_id=implicitClient&response_type=token&redirect_uri=http://localhost:8081/callback"} "Login"]]
   [:div [:a {:href "/services"} "Services"]]
   [:div [:a {:href "/logout"} "Logout"]]])


(defn ^:dev/after-load start
  []
  (rdom/render [app]
                      (.getElementById js/document "app")))


(defn init []
  (println "Hello")
  (start))


