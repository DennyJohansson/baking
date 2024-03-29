(ns doughcalc.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [accountant.core :as accountant]))

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]
    ]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -------------------------
; flour, liquid, percentage, salt
; need to calculate the percentage of liquid on base flour
; same with salt
; and sourdough
(defn calc-perc [{:keys [flour liquid perc] :as data}]
  (let [h (/ flour 100)]
    (if (nil? perc)
      (assoc data :perc (/ liquid h)) 
      (assoc data :liquid (* perc h)))))

(def perc-data (reagent/atom (calc-perc {:flour 1600 :liquid 1200 :sourdough 20 })))

(defn slider [param value min max invalidates]
  [:input {:type "range" :value value :min min :max max
           :style {:width "100%"}
           :on-change (fn [e]
                        (let [new-value (js/parseInt (.. e -target -value))]
                          (swap! perc-data
                                 (fn [data]
                                   (-> data
                                     (assoc param new-value)
                                     (dissoc invalidates)
                                     calc-perc)))))}])

(defn perc-component []
  (let [{:keys [liquid flour perc sourdough]} @perc-data
        [color diagnose] (cond
                          (< perc 50) ["orange" "dry"]
                          (< perc 85) ["inherit" "normal"]
                          (< perc 100) ["orange" "wet"]
                          :else ["red" "soup"])]
    [:div
     [:h3 "Dough percentage calculator"]
     [:div
      "flour: " (int flour ) "g"
      [slider :flour flour 100 2000 :perc]]
     [:div
      "liquid: " (int liquid ) "g"
      [slider :liquid liquid 30 2000 :perc]]
     [:div
      "perc: " (int perc) "% "
      [:span {:style {:color color}} diagnose]
      [slider :perc perc 10 120 :liquid ]]
     [:div
      "sourdough " (int sourdough) "%: " (* sourdough (/ flour 100)) "g"
      [slider :sourdough sourdough 10 40 :perc]]
     [:div
      "salt 2%: " (* 2 (/ flour 100)) "g"]
     ]))

;; Page components
(defn home-page []
  (fn []
    [:span.main
     [:h1 "Dough calc"]
     [perc-component]]))

;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page
  ))

;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div {:style {:padding "35px"}} [page]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (rdom/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
