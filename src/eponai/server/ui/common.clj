(ns eponai.server.ui.common
  (:require
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [clojure.string :as string]
    [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

;; Utils

(def text-javascript "text/javascript")

(defn inline-javascript [code]
  {:pre [(every? string? code)]}
  (let [code (->> code
                  (map string/trim)
                  (apply str))]
    (dom/script {:type                    text-javascript
                 :dangerouslySetInnerHTML {:__html code}})))

(defn anti-forgery-field []
  (dom/input
    {:id "__anti-forgery-token"
     :name "__anti-forgery-token"
     :type "hidden"
     :value *anti-forgery-token*}))


(defn iubenda-code []
  (inline-javascript ["(function (w,d) {var loader = function () {var s = d.createElement(\"script\"), tag = d.getElementsByTagName(\"script\")[0]; s.src = \"//cdn.iubenda.com/iubenda.js\"; tag.parentNode.insertBefore(s,tag);}; if(w.addEventListener){w.addEventListener(\"load\", loader, false);}else if(w.attachEvent){w.attachEvent(\"onload\", loader);}else{w.onload = loader;}})(window, document);"]))

;; Facebook login init code

(defn facebook-async-init-code []
  ["window.fbAsyncInit = function() {"
   "  FB.init({"
   "    appId: '936364773079066',"
   "    xfbml: true,"
   "    version: 'v2.7'"
   "  });"
   "};"
   ""
   "(function(d, s, id){"
   "   var js, fjs = d.getElementsByTagName(s)[0];"
   "   if (d.getElementById(id)) {return;}"
   "   js = d.createElement(s); js.id = id;"
   "   js.src = \"//connect.facebook.net/en_US/sdk.js\";"
   "   fjs.parentNode.insertBefore(js, fjs);"
   " }(document, 'script', 'facebook-jssdk'));"])

;; Mix panel inline code

(defn mixpanel []
  (inline-javascript
    ["(function(e,a){if(!a.__SV){var b=window;try{var c,l,i,j=b.location,g=j.hash;c=function(a,b){return(l=a.match(RegExp(b+\"=([^&]*)\")))?l[1]:null};g&&c(g,\"state\")&&(i=JSON.parse(decodeURIComponent(c(g,\"state\"))),\"mpeditor\"===i.action&&(b.sessionStorage.setItem(\"_mpcehash\",g),history.replaceState(i.desiredHash||\"\",e.title,j.pathname+j.search)))}catch(m){}var k,h;window.mixpanel=a;a._i=[];a.init=function(b,c,f){function e(b,a){var c=a.split(\".\");2==c.length&&(b=b[c[0]],a=c[1]);b[a]=function(){b.push([a].concat(Array.prototype.slice.call(arguments,0)))}}var d=a;\"undefined\"!==typeof f?d=a[f]=[]:f=\"mixpanel\";d.people=d.people||[];d.toString=function(b){var a=\"mixpanel\";\"mixpanel\"!==f&&(a+=\".\"+f);b||(a+=\" (stub)\");return a};d.people.toString=function(){return d.toString(1)+\".people (stub)\"};k=\"disable time_event track track_pageview track_links track_forms register register_once alias unregister identify name_tag set_config reset people.set people.set_once people.increment people.append people.union people.track_charge people.clear_charges people.delete_user\".split(\" \");for(h=0;h<k.length;h++)e(d,k[h]);a._i.push([b,c,f])};a.__SV=1.2;b=e.createElement(\"script\");b.type=\"text/javascript\";b.async=!0;b.src=\"undefined\"!==typeof MIXPANEL_CUSTOM_LIB_URL?MIXPANEL_CUSTOM_LIB_URL:\"file:\"===e.location.protocol&&\"//cdn.mxpnl.com/libs/mixpanel-2-latest.min.js\".match(/^\\/\\//)?\"https://cdn.mxpnl.com/libs/mixpanel-2-latest.min.js\":\"//cdn.mxpnl.com/libs/mixpanel-2-latest.min.js\";c=e.getElementsByTagName(\"script\")[0];c.parentNode.insertBefore(b,c)}})(document,window.mixpanel||[]);mixpanel.init(\"b266c99172ca107a814c16cb22661d04\");"]))

;; End mix panel.

(defn icons []
  (letfn [(icon [size rel href-without-size]
            (let [sxs (str size "x" size)]
              (dom/link {:rel   rel
                         :sizes sxs
                         :href  (str href-without-size sxs ".png?v=2")
                         :type  "image/png"})))]
    (concat
      (map #(icon % "apple-touch-icon" "/assets/img/favicon/apple-icon-")
           [57 60 72 76 114 120 144 152 180])
      (map #(icon % "icon" "/assets/img/favicon/favicon-")
           [16 32 96])
      (map #(icon % "icon" "/assets/img/favicon/android-icon-")
           [36 48 72 96 144 192]))))

(defn head [release? & [exclude-icons?]]
  [(dom/meta {:name    "google-site-verification"
              :content "eWC2ZsxC6JcZzOWYczeVin6E0cvP4u6PE3insn9p76U"})
   
   (dom/meta {:charset "utf-8"})
   (dom/meta {:http-equiv "X-UA-Compatible"
              :content    "IE=edge"})
   (dom/meta {:name    "viewport"
              :content "width=device-width, initial-scale=1 maximum-scale=1 user-scalable=0"})
   (dom/meta {:name "author" :content "SULO Live"})
   (dom/meta {:name    "description"
              :content "Watch and interact with your favorite local makers and artisans on Vancouver's online marketplace."})
   (comment (dom/meta {:http-equiv "Content-Type"
                       :content    "text/html; charset=utf-8"}))
   (dom/title nil "Vancouver's local marketplace online - SULO Live")
   (dom/link {:href (if release?
                      "https://cdn.rawgit.com/novus/nvd3/v1.8.1/build/nv.d3.css"
                      "/bower_components/nvd3/build/nv.d3.css")
              :rel  "stylesheet"})
   (dom/link {:href "/lib/videojs/video-js.min.css"
              :rel "stylesheet"})


   (dom/link {:href "//cdn.quilljs.com/1.2.0/quill.core.css"
              :rel "stylesheet"})

   ;;TODO: https://cdn.quilljs.com/1.0.0/quill.snow.css if want to save to local in dev mode
   (dom/link {:href "//cdn.quilljs.com/1.2.0/quill.snow.css"
              :rel "stylesheet"})

   (dom/link {:href "/assets/css/app.css"
              :rel  "stylesheet"})
   ;; Custom fonts
   (dom/link {:href (if release?
                      "https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css"
                      "/assets/font-awesome/css/font-awesome.min.css")
              :rel  "stylesheet"
              :type "text/css"})
   (dom/link {:href "https://fonts.googleapis.com/css?family=Roboto:300,400,500"
              :rel "stylesheet"
              :type "text/css"})


   (when release?
     (mixpanel))
   (iubenda-code)

   ;; Favicon
   (when (not exclude-icons?)
     (icons))

   (dom/link {:rel "manifest" :href "/assets/img/favicon/manifest.json"})
   (dom/meta {:name "msapplication-TileColor" :content "#ffffff"})
   (dom/meta {:name    "msapplication-TileImage"
              :content "/assets/img/favicon/ms-icon-144x144.png"})
   (dom/meta {:name "theme-color" :content "#ffffff"})
   (dom/script {:src "//player.wowza.com/player/1.0.07.4414/wowzaplayer.min.js"})
   ;(dom/script {:src (if release?
   ;                    "//player.wowza.com/player/latest/wowzaplayer.min.js"
   ;                    "/bower_components/wowzaplayer/wowzaplayer.min.js")})
   ])

(defn budget-js-path [cljs-build-id]
  (str "/"
       (cond (= cljs-build-id "release") "release"
             (= cljs-build-id "devcards") "devcards"
             :else "dev")
       "/js/out/budget.js"))

(defn red5pro-script-tags [release?]
  (comment
    "We don't need these yet."
    [(dom/script {:src (if release? "https://webrtc.github.io/adapter/adapter-latest.js"
                                    "/bower_components/webrtc-adapter/release/adapter.js")})
     (dom/script {:src "/lib/videojs/video.min.js"})
     (dom/script {:src "/lib/videojs/videojs-media-sources.min.js"})
     (dom/script {:src "/lib/videojs/videojs.hls.min.js"})
     (dom/script {:src "/lib/red5pro/red5pro-sdk.min.js"})]))

(defn auth0-lock-passwordless [release?]
  (dom/script {:src (if release? "https://cdn.auth0.com/js/lock-passwordless-2.2.min.js"
                                 "/bower_components/auth0-lock-passwordless/build/lock-passwordless.min.js")}))

(defn auth0-lock [release?]
  (dom/script {:src (if release? "https://cdn.auth0.com/js/lock/10.6/lock.min.js"
                                 "/bower_components/auth0-lock/build/lock.js")}))