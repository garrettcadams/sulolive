(ns eponai.client.ui.all_transactions
  (:require [eponai.client.ui.format :as f]
            [eponai.client.parser :as parser]
            [eponai.client.ui.transaction :as trans]
            [eponai.client.ui :refer-macros [style opts]]
            [garden.core :refer [css]]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]))

;; Transactions grouped by a day

(defn sum
  "Adds transactions amounts together.
  TODO: Needs to convert the transactions to the 'primary currency' some how."
  [transactions]
  {:amount   (transduce (map :transaction/amount) + 0 transactions)
   ;; TODO: Should instead use the primary currency
   :currency (-> transactions first :transaction/currency :currency/code)})

(defui AllTransactions
  static om/IQuery
  (query [_]
    [{:query/all-dates
      ['*
       {:transaction/_date (om/get-query trans/Transaction)}
       ::day-expanded?]}])

  Object
  (render [this]
    (let [{:keys [query/all-dates]} (om/props this)
          by-year-month (f/dates-by-year-month all-dates)]
      (html
        [:div
         {:class "container"}
         [:div
          (map
            (fn [[year months]]
              [:div#year-panel
               (opts {:style {:display         "flex"
                              :flex-direction  "row"
                              :justify-content "flex-start"
                              :align-items     "flex-start"
                              :width "100%"}})

               [:span#year
                (opts {:class "network-name"
                       :style {:margin-right "0.5em"}})
                year]

               [:div#months
                (opts {:style {:width "100%"}})
                (map
                  (fn [[month days]]
                    [:div#month
                     (opts {:style {:display "flex"
                                    :flex-direction  "row"
                                    :align-items     "flex-start"
                                    :width "100%"}})

                     [:p#month-name
                      {:class "network-name"}
                      (f/month-name month)]

                     [:div#days
                      (opts {:class "panel-group"
                             :style {:width "100%"}})
                      (map
                        (fn [date]
                          [:div#day
                           (opts {:class "panel panel-info"})

                           [:div#weekday
                            (opts {:class "panel-heading list-group-item"
                                   :style {:display "flex"
                                           :flex-direction "row"
                                           :justify-content "space-between"}
                                   :on-click #(parser/cas!
                                               this
                                               (:db/id date)
                                               ::day-expanded?
                                               (::day-expanded? date)
                                               (not (::day-expanded? date)))})
                            [:span#dayname
                             (opts {:style {:margin-right "0.3em"}})
                             (str (f/day-name date) " " (:date/day date))]

                            [:span#daysum
                             (let [{:keys [currency
                                           amount]} (sum (:transaction/_date date))]
                               (str amount " " currency))]]

                           (when (::day-expanded? date)
                             [:div#transactions
                              (opts {:class "panel-body"})
                              (map trans/->Transaction
                                   (:transaction/_date date))])])
                        (rseq days))]])
                  (rseq months))]])
            (rseq by-year-month))]]))))

(def ->AllTransactions (om/factory AllTransactions))
