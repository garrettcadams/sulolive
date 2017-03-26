(ns eponai.common.ui.navbar
  (:require
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.ui.utils :as ui-utils]
    [eponai.common.ui.elements.css :as css]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.elements.photo :as photo]
    [taoensso.timbre :refer [debug error]]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common.ui.icons :as icons]
    [eponai.client.routes :as routes]
    [clojure.string :as s]))

(defn compute-item-price [items]
  (reduce + (map :store.item/price items)))

(defn cart-dropdown [{:keys [cart/items cart/price]}]
  (dom/div #js {:className "cart-container dropdown-pane"}
    (apply menu/vertical
           {:classes [::css/cart]}
           (map (fn [i]
                  (let [{:store.item/keys [price photos] p-name :store.item/name :as item} (:store.item/_skus i)]
                    (menu/item-link
                      {:href    (routes/url :product {:product-id (:db/id item)})
                       :classes [:cart-link]}
                      (photo/square
                        {:src (:photo/path (first photos))})
                      (dom/div #js {:className ""}
                        (dom/div #js {:className "content-item-title-section"}
                          (dom/p nil (dom/span #js {:className "name"} p-name)))
                        (dom/div #js {:className "content-item-subtitle-section"}
                          (dom/strong #js {:className "price"}
                                      (ui-utils/two-decimal-price price)))))))
                (take 3 items)))

    (dom/div #js {:className "callout transparent"}
      (if (< 3 (count items))
        (dom/small nil (str "You have " (- (count items) 3) " more item(s) in your bag"))
        (dom/small nil (str "You have " (count items) " item(s) in your bag")))
      (dom/h5 nil "Total: " (dom/strong nil (ui-utils/two-decimal-price (compute-item-price (map #(get % :store.item/_skus) items))))))
    (dom/a #js {:className "button expanded hollow gray"
                :href      (routes/url :shopping-bag nil)} "View My Bag")))

(defn category-dropdown []
  (dom/div #js {:className "dropdown-pane"}
    (menu/vertical
      {:classes [::css/categories]}
      (menu/item-link
        {:href (str "/goods?category=clothing")}
        (dom/span nil "Clothing"))
      (menu/item-link
        {:href (str "/goods?category=accessories")}
        (dom/span nil "Accessories"))
      (menu/item-link
        {:href (str "/goods?category=home")}
        (dom/span nil "Home")))))

(defn user-dropdown [component user]
  (let [store (get (first (get user :store.owner/_user)) :store/_owners)]
    (dom/div #js {:className "dropdown-pane"}
      (dom/ul #js {:className "menu vertical"}
              (when user
                (dom/li nil
                        (dom/a #js {:href (routes/url :user {:user-id (:db/id user)})}
                               "My Profile")))
              (when user
                (dom/li nil
                        (dom/a #js {:href (routes/url :user/order-list {:user-id (:db/id user)})}
                               "My Orders")))
              (when store
                (dom/li nil
                        (dom/a #js {:href (routes/url :store-dashboard {:store-id (:db/id store)})}
                               "My Store")))
              (dom/li nil
                      (dom/a #js {:href "/settings"}
                             "Account Settings"))
              (dom/li nil
                      (dom/a #js {:href "/logout"}
                             "Sign Out"))))))

(defn navbar-content [& content]
  (apply dom/div #js {:className "navbar top-bar"}
         content))

(defn collection-links [& [disabled?]]
  (map-indexed
    (fn [i c]
      (let [opts (cond-> {:key  (str "nav-" c "-" i)}
                         (not disabled?)
                         (assoc :href (routes/url :products/categories {:category (.toLowerCase c)})))]
        (menu/item-link
          (->> opts
               (css/add-class :category)
               (css/show-for {:size :large}))
          (dom/span nil (s/capitalize c)))))
    ["women" "men" "kids" "home" "art"]))

(defn live-link [& [on-click]]
  (let [opts (if on-click
                 {:key "nav-live"
                  :onClick on-click}
                 {:key "nav-live"
                  :href (routes/url :live)})]
    (menu/item-link
      (->> opts
        (css/add-class ::css/highlight)
        (css/add-class :navbar-live))
      (my-dom/strong
        (css/hide-for {:size :small :only? true})
        ;; Wrap in span for server and client to render the same html
        (dom/span nil "Live"))
      (my-dom/div
        (css/show-for {:size :small :only? true})
        (dom/i #js {:className "fa fa-video-camera fa-fw"})))))

(defn navbar-brand [& [href]]
  (menu/item-link {:href (or href "/")
                   :id   "navbar-brand"}
                  (dom/span nil "Sulo")))

(defn coming-soon-navbar [component]
  (let [{:keys [coming-soon? right-menu on-live-click]} (om/get-computed component)]
    (navbar-content
      (dom/div #js {:className "top-bar-left"}
        (menu/horizontal
          nil
          (navbar-brand (routes/url :coming-soon))

          (live-link on-live-click)
          (menu/item-dropdown
            (->> {:dropdown (category-dropdown)}
                 (css/hide-for {:size :large})
                 (css/add-class :category))
            (dom/span nil "Shop"))

          (collection-links true)))

      (dom/div #js {:className "top-bar-right"}
        right-menu))))

(defn standard-navbar [component]
  (let [{:keys [did-mount?]} (om/get-state component)
        {:keys [coming-soon?]} (om/get-computed component)
        {:query/keys [cart auth]} (om/props component)]
    (navbar-content
      (dom/div #js {:className "top-bar-left"}
        (menu/horizontal
          nil
          (navbar-brand)
          (live-link)

          (menu/item-dropdown
            (->> {:dropdown (category-dropdown)}
                 (css/hide-for {:size :large})
                 (css/add-class :category))
            (dom/span nil "Shop"))

          (collection-links)))
      (dom/div #js {:className "top-bar-right"}
        (menu/horizontal
          nil
          (menu/item nil
                     (my-dom/a
                       (->> {:id "search-icon"}
                            (css/show-for {:size :small :only? true}))
                       (dom/i #js {:className "fa fa-search fa-fw"}))
                     (my-dom/div
                       (css/hide-for {:size :small :only? true})
                       (dom/input #js {:type        "text"
                                       :placeholder "Search on SULO..."
                                       :onKeyDown   (fn [e]
                                                      #?(:cljs
                                                         (when (= 13 (.. e -keyCode))
                                                           (let [search-string (.. e -target -value)]
                                                             (set! js/window.location (str "/goods?search=" search-string))))))})))
          (menu/item-dropdown
            {:dropdown (user-dropdown component auth)}
            (dom/i #js {:className "fa fa-user fa-fw"}))
          (if did-mount?
            (menu/item-dropdown
              {:dropdown (cart-dropdown cart)
               :href     "/shopping-bag"}
              (dom/i #js {:className "fa fa-shopping-cart fa-fw"}))
            (menu/item-dropdown
              {:href "/shopping-bag"}
              (dom/i #js {:className "fa fa-shopping-cart fa-fw"}))))))))

(defn store-navbar [component]
  (let [{:query/keys [current-route auth]} (om/props component)
        {:keys [route-params]} current-route
        {:keys [store-id]} route-params]
    (navbar-content
      (dom/div #js {:className "top-bar-left"}
        (menu/horizontal
          nil
          (menu/item-link {:href "/"
                           :id   "navbar-brand"}
                          (dom/span nil "Sulo"))
          (menu/item-link
            (->> (css/add-class :category {:href (routes/url :store-dashboard/stream {:store-id store-id})})
                 (css/show-for {:size :large}))
            (dom/span nil "Stream"))
          (menu/item-link
            (->> (css/add-class :category {:href (routes/url :store-dashboard/product-list {:store-id store-id})})
                 (css/show-for {:size :large}))
            (dom/span nil "Products"))
          (menu/item-link
            (->> (css/add-class :category {:href (routes/url :store-dashboard/order-list {:store-id store-id})})
                 (css/show-for {:size :large}))
            (dom/span nil "Orders"))))
      (dom/div #js {:className "top-bar-right"}

        (menu/horizontal
          nil
          (menu/item nil
                     (my-dom/a
                       (->> {:id "search-icon"}
                            (css/show-for {:size :small :only? true}))
                       (dom/i #js {:className "fa fa-search fa-fw"}))
                     (my-dom/div
                       (css/hide-for {:size :small :only? true})
                       (dom/input #js {:type        "text"
                                       :placeholder "Search on SULO..."
                                       :onKeyDown   (fn [e]
                                                      #?(:cljs
                                                         (when (= 13 (.. e -keyCode))
                                                           (let [search-string (.. e -target -value)]
                                                             (set! js/window.location (str "/goods?search=" search-string))))))})))
          (menu/item-dropdown
            {:dropdown (user-dropdown component auth)}
            (dom/i #js {:className "fa fa-user fa-fw"}))
          (menu/item-dropdown
            {:href "/shopping-bag"}
            (dom/i #js {:className "fa fa-shopping-cart fa-fw"})))))))

(defui Navbar
  static om/IQuery
  (query [_]
    [{:query/cart [{:cart/items [{:store.item/_skus [:store.item/price
                                                     {:store.item/photos [:photo/path]}
                                                     :store.item/name
                                                     {:store/_items [:store/name]}]}]}]}
     {:query/auth [:db/id :user/email {:store.owner/_user [{:store/_owners [:store/name :db/id]}]}]}
     '{:query/top-categories [:category/label :category/path :category/level {:category/children ...}]}
     :query/current-route])
  Object
  (open-signin [this]
    (debug "Open signin")
    #?(:cljs (let [{:keys [lock]} (om/get-state this)
                   current-url js/window.location.href
                   options (clj->js {:connections  ["facebook"]
                                     :callbackURL  (str js/window.location.origin "/auth")
                                     :authParams   {:scope            "openid email user_friends"
                                                    :connectionScopes {"facebook" ["email" "public_profile" "user_friends"]}
                                                    :state            current-url}
                                     :primaryColor "#9A4B4F"
                                     :dict         {:title "SULO"}
                                     :icon         ""
                                     ;:container "modal"
                                     })]
               (.socialOrMagiclink lock options))))

  (initLocalState [_]
    {:cart-open?   false
     :on-scroll-fn #(debug "Did scroll: " %)})
  (componentWillUnmount [this]
    #?(:cljs
       (let [{:keys [lock on-scroll-fn]} (om/get-state this)]
         (.removeEventListener js/document.documentElement "scroll" on-scroll-fn))))

  (componentDidMount [this]
    #?(:cljs (let [{:keys [on-scroll-fn]} (om/get-state this)]
               (when js/Auth0LockPasswordless
                 (let [lock (new js/Auth0LockPasswordless "JMqCBngHgOcSYBwlVCG2htrKxQFldzDh" "sulo.auth0.com")]
                   (om/update-state! this assoc :lock lock)))
               (.addEventListener js/document.documentElement "scroll" on-scroll-fn)
               (om/update-state! this assoc :did-mount? true))))

  (render [this]
    (let [
          {:query/keys [cart auth current-route top-categories]} (om/props this)
          {:keys [route route-params]} current-route]

      (debug "Navbar categories: " top-categories)
      (debug "Route: " route)
      (dom/header #js {:id "sulo-navbar"}
                  (dom/div #js {:className "navbar-container"}
                    (dom/div #js {:className "top-bar navbar"}
                      (cond (and route (= (or (namespace route) (name route)) "store-dashboard"))
                            (store-navbar this)

                            ;; When the user is going through the checkout flow, don't let them navigate anywhere else.
                            (= route :checkout)
                            (navbar-content
                              (dom/div #js {:className "top-bar-left"}
                                (menu/horizontal
                                  nil
                                  (menu/item-link {:href "/"
                                                   :id   "navbar-brand"}
                                                  (dom/span nil "Sulo")))))
                            (or (= route :coming-soon) (= route :sell-soon))
                            (coming-soon-navbar this)
                            :else
                            (standard-navbar this))))))))
(def ->Navbar (om/factory Navbar))

(defn navbar [props]
  (->Navbar props))