(ns eponai.web.ui.unauthorized
  (:require
    [eponai.common.ui.router :as router]
    [eponai.common.ui.dom :as dom]
    [om.next :as om :refer [defui]]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.elements.grid :as grid]
    [eponai.common.ui.elements.css :as css]
    [eponai.web.ui.button :as button]
    [eponai.web.ui.footer :as foot]
    [eponai.client.routes :as routes]
    [eponai.common.ui.product-item :as pi]
    [taoensso.timbre :refer [debug]]))

(defui Unauthorized
  static om/IQuery
  (query [this]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:proxy/footer (om/get-query foot/Footer)}
     {:query/featured-items [:db/id
                             :store.item/name
                             :store.item/price
                             :store.item/created-at
                             {:store.item/photos [{:store.item.photo/photo [:photo/path :photo/id]}
                                                  :store.item.photo/index]}
                             {:store/_items [{:store/profile [:store.profile/name]}
                                             :store/locality]}]}
     :query/locations
     :query/current-route
     :query/messages])
  Object
  (render [this]
    (let [{:proxy/keys [navbar footer]
           :query/keys [locations featured-items]} (om/props this)]
      (debug "Footer: " footer)
      (common/page-container
        {:navbar navbar :footer (om/computed footer
                                             {:on-change-location #(om/transact! this [:query/featured-items])}) :id "sulo-unauthorized"}
        (grid/row-column
          (css/text-align :center)
          (dom/h1 nil "Unauthorized")
          (dom/div (css/add-class :empty-container)
            (dom/p (css/add-class :shoutout)
                   (dom/span nil "Oops, seems we're a little lost. You don't have access to this page.")))
          (if (some? (:sulo-locality/path locations))
            (button/button
              {:href    (routes/url :browse/all-items {:locality (:sulo-locality/path locations)})
               :classes [:hollow :sulo-dark]}
              (dom/span nil "Browse products"))
            (button/button
              {:href    (routes/url :landing-page)
               :classes [:hollow :sulo-dark]}
              (dom/span nil "Select location"))))
        (when (not-empty featured-items)
          [
           (grid/row-column
             nil
             (dom/hr nil)
             (dom/div
               (css/add-class :section-title)
               (dom/h3 nil (str "New arrivals in " (:sulo-locality/title locations)))))
           (grid/row
             (->>
               (grid/columns-in-row {:small 2 :medium 3 :large 6}))
             (map
               (fn [p]
                 (grid/column
                   (css/add-class :new-arrival-item)
                   (pi/product-element {:open-url? true} p)))
               (take 6 featured-items)))])))))

(router/register-component :unauthorized Unauthorized)