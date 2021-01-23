(ns frontend.app
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [reitit.frontend.controllers :as rfc]
            [reitit.coercion.schema :as rsc]
            [fipp.edn :as fedn]))

(defonce match (r/atom nil))

(defn current-page []
  [:div.container
   [:div [:a {:href (rfe/href ::frontpage)} "Home"]]
   [:div [:a {:href "http://localhost:8080/auth/realms/sso-test/protocol/openid-connect/auth?client_id=implicitClient&response_type=token&redirect_uri=http://localhost:8081/callback"} "Login"]]
   [:div [:a {:href (rfe/href ::services)} "Services"]]
   [:div [:a {:href "/logout"} "Logout"]]
   [:h1.title.is-4 "Implicit Grant Type"]
   (if @match
       (let [view (:view (:data @match))]
         [view @match]))
   [:pre (with-out-str (fedn/pprint @match))]])

(defn home-page []
  [:div.container
   [:h1.title.is-4 "Home Page"]])

(defn services []
  [:div.container
   [:h1.title.is-4 "Services"]])

(defn callback []
  (js/console.log "h3llo")
  [:div.container
   [:h1.title.is-4 "Callback"]])

(defn log-fn [& params]
  (fn [_]
    (apply js/console.log params)))

(def routes
  (rf/router
   ["/"
    [""
     {:name ::frontpage
      :view home-page
      :controllers [{:start (log-fn "start" "frontpage controller")
                     :stop (log-fn "stop" "frontpage controller")}]}]
    ["services"
     {:name ::services
      :view services
      :controllers [{:start (log-fn "start" "services controller")
                     :stop (log-fn "stop" "services controller")}]}]
    ["callback"
     {:name ::callback
      :view callback
      :controllers [{:start (log-fn "start" "callback controller")
                     :stop (log-fn "stop" "callback controller")}]}]]
   {:data {:controllers [{:start (log-fn "start" "root-controller")
                           :stop (log-fn "stop" "root controller")}]
            :coercion rsc/coercion}}))


(defn ^:dev/after-load start
  []
  (rfe/start!
   routes
   (fn [new-match]
     (swap! match (fn [old-match]
                    (if new-match
                      (assoc new-match :controllers 
                             (rfc/apply-controllers (:controllers old-match) new-match))))))
   {:use-fragment false})
  (rdom/render [current-page]
                      (.getElementById js/document "app")))


(defn init []
  (start))


