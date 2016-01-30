(ns eponai.client.ui.stripe
  (:require [cljs.core.async :refer [chan <! put!]]
            [clojure.walk :refer [keywordize-keys]]
            [eponai.client.ui :refer-macros [opts]]
            [goog.string :as gstring]
            [goog.string.format]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [info debug error trace]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn load-checkout [channel]
  (-> (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
      (.addCallback #(put! channel [:stripe-checkout-loaded :success]))))

(defn checkout-loaded? []
  (boolean (aget js/window "StripeCheckout")))

(defn stripe-token-recieved-cb [component]
  (fn [token]
    (let [clj-token (keywordize-keys (js->clj token))]
      (debug "Recieved token from Stripe.")
      (trace "Recieved token from Stripe: " clj-token)
      (om/transact! component `[(stripe/charge ~{:token clj-token})]))))

(defn open-checkout [component args]
  (let [checkout (.configure js/StripeCheckout
                             (clj->js {:key    "pk_test_KHyU4tNjwX7R0lkxDmPxvbT9"
                                       :locale "auto"
                                       :token  (stripe-token-recieved-cb component)}))]
    (.open checkout (clj->js args))))

(defn show-loading [component show?]
  (if show?
    (om/transact! component `[(ui.loader/show) :query/loader])
    (om/transact! component `[(ui.loader/hide) :query/loader])))

;; ---------- UI components ----------------

(defn price->str [price]
  (gstring/format "$%.2f" price))

(defn plan-item [component props]
  (let [{:keys [plan-name
                plan-price
                plan-monthly
                user-email]} props]
    (html
      [:li.plan
       [:ul.plan-container
        [:li.title
         [:h2 plan-name]]
        [:li.price
         [:p (price->str plan-price)]]
        [:li
         [:ul.options
          [:li
           [:div
            (opts {:style {:display "inline-block"}})
            (price->str plan-monthly)]
           [:span " /month"]]]]
        [:li
         [:button
          {:on-click (fn []
                       (show-loading component true)
                       (open-checkout component
                                      {:name        "JourMoney"
                                       :description plan-name
                                       :currency    "usd"
                                       :email       user-email
                                       :amount      (* 100 plan-price)
                                       :locale      "auto"
                                       :allowRememberMe false
                                       :opened      #(show-loading component false)
                                       :closed      #(debug "StripeCheckout did close.")}))}
          "Buy"]]]])))

(defui Payment
  static om/IQuery
  (query [_]
    [{:query/current-user [:user/uuid
                           :user/email]}])
  Object
  (initLocalState [_]
    {:checkout-loaded? (checkout-loaded?)
     :load-checkout-chan (chan)})
  (componentWillMount [this]
    (let [{:keys [load-checkout-chan
                  checkout-loaded?]} (om/get-state this)]
      (when-not checkout-loaded?
        (go (<! load-checkout-chan)
            (prn "Setting state...")
            (om/set-state! this {:checkout-loaded? true}))
        (load-checkout load-checkout-chan))))
  (render [this]
    (let [{:keys [checkout-loaded?]} (om/get-state this)
          {:keys [query/current-user]} (om/props this)]
      (debug "StripeCheckout loaded: " checkout-loaded?)
      (html
        [:div
         [:h3
          (opts {:style {:text-align "center"}})
          "Select your plan"]
         [:hr.intro-divider]

         (if checkout-loaded?
           [:div#pricePlans
            [:ul#plans
             (plan-item this
                        {:plan-name    "Monthly"
                         :plan-price   9.90
                         :plan-monthly 9.90
                         :user-email (:user/email current-user)})

             (plan-item this
                        {:plan-name    "Yearly"
                         :plan-price   90
                         :plan-monthly 7.50
                         :user-email (:user/email current-user)})]]
           [:div.loader])]))))

(def ->Payment (om/factory Payment))