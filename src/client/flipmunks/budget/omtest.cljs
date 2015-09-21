(ns flipmunks.budget.omtest
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]))

(def testdata {2015 {1 {1 {:purchases [{:name "coffee" :cost {:currency "LEK"
                                                              :price 400}}
                                       {:name "dinner" :cost {:currency "LEK"
                                                              :price 1000}}]
                           :rates {"LEK" 0.0081}}
                        2 {:purchases [{:name "lunch" :cost {:currency "LEK"
                                                             :price 600}}]
                           :rates {"LEK" 0.0081}}}}})

(defn month-to-str [n]
  ;;TODO: GET A DATE LIBRARY
  (get {1 "January"
        2 "February"
        3 "March"
        4 "April"
        5 "TODO"} n))

(defn purchase-view [rates {:keys [name cost]}]
  [:div {:class "row"}
   [:div {:class "col-md-6"} name]
   [:div {:class "col-md-6"} (str (:price cost) " " (:currency cost))]])

(defn day-budget [[day {:keys [purchases rates]}]]
  (prn purchases)
  (let [prices (map (juxt (comp :price :cost)
                          #(let [curr (-> % :cost :currency)]
                             (* (get rates curr)
                                (-> % :cost :price))))
                    purchases)]
    (print prices)
    ;;TODO: want to be able to expand this view. What about local state?
    (html
      [:div {:class "row"}
       [:div {:class "col-md-4"} (str "Day " day ".")]
       [:div {:class "col-md-4"} (reduce + 0 (map first prices))]
       [:div {:class "col-md-4"} (reduce + 0 (map second prices))]
       (map (partial purchase-view rates) purchases)])))

(defn month-budget-view [month-data owner]
  (reify
    om/IRender
    (render [this]
      (html [:div (map day-budget (into (sorted-map) @month-data))]))))

(defn widget [data owner]
  (reify
    om/IRender
    (render [this]
      (let [{:keys [year month]} (:date data)]
        (html [:div {:class "container"}
               [:div {:class "page-header"}
                [:h1 "Budget App"]
                [:h2 (str year "-" (month-to-str month))]]
               (om/build month-budget-view 
                         (-> data :budget-data (get year) (get month)))])))))


(defn run []
  (let [app-state (atom {:date {:year 2015 :month 1}
                         :budget-data testdata})]
    (om/root widget 
             app-state
             {:target (. js/document (getElementById "my-app"))})))

