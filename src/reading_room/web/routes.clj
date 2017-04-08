(ns reading-room.web.routes
  (:require [clojure.java.io :as io]
            [compojure.core :refer [defroutes GET]]
            [reading-room.image :as im]
            [reading-room.library :as library]
            [reading-room.zip :as zip]
            [ring.util.io :as ring-io]
            [ring.util.response :as response]
            [reading-room.fs :as fs]))

(defn series-url [title]
  (str "/series/" title))

(defn cover-url [title volume-num]
  (str "/series/" title "/" volume-num "/cover.jpg"))

(defn page-url [title volume-num page-num]
  (str "/series/" title "/" volume-num "/page/" page-num))

(defn page-image-url [title volume-num page-num]
  (str (page-url title volume-num page-num) ".jpg"))

(defn download-volume-url [title volume-num]
  (str "/series/" title "/" volume-num "/download/" title " - " volume-num ".zip"))

(defn page
  ([title content] (page title {} content))
  ([title html-opts content]
   ;; bootstrap boilerplate
   [:html (merge {:lang "en"} html-opts)
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:meta {:name "apple-mobile-web-app-capable" :content "yes"}]

     ;; The above 3 meta tags *must* come first in the head; any other head
     ;; content must come *after* these tags
     [:title title]


     [:link {:href "/assets/bootstrap/css/bootstrap.css" :rel "stylesheet"}]
     [:link {:href "/assets/bootstrap/css/bootstrap-theme.css" :rel "stylesheet"}]]
    [:body {:role "document"}
     [:div.container
      content]
     [:script {:src "https://ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js"}]
     [:script {:src "/assets/bootstrap/js/bootstrap.js"}]
     [:script {:type "text/javascript"}
      "
if (window.navigator.standalone) {
    var local = document.domain;
    $('document').on('click', 'a', function() {
        var a = $(this).attr('href');
        if ( a.match('http://' + local) || a.match('http://www.' + local) ){
            event.preventDefault();
            document.location.href = a;
        }
    });
}
"
      ]
     ]]))

(defn grid [item-seq layout-item-fn]
  (for [row (partition-all 3 item-seq)]
    [:div.row
     (for [item row]
       [:div.col-sm-6.col-md-4
        (layout-item-fn item)])]))

(defn thumb-with-caption [{:keys [url href caption]}]
  [:div.thumbnail
   [:a {:href href} [:img {:src url}]]
   [:div.caption
    [:a {:href href} caption]]])

(defn show-library [{:keys [library]}]
  (page "library"
        [:div
         (grid (library/query-first-volume-of-each-series library)
               (fn [{:keys [::library/author ::library/title]}]
                 (thumb-with-caption
                  {:url (cover-url title 1)
                   :href (series-url title)
                   :caption [:p title "&nbsp;"
                             (when author (str "(" author  ")"))]})))]))

(defn show-series [{:keys [library series-title]}]
  (let [series (sort-by ::library/volume-num
                        (library/query-volumes-like library {::library/title series-title}))]
    (if-not series
      [:div "Can't find series with title " series-title]
      (page series-title
            [:div
             [:h1
              (::library/title series)
              "&nbsp;"
              [:a {:href "/"} "up"]]

             (grid series
                   (fn [v]
                     (let [volume-num (::library/volume-num v)]
                       (thumb-with-caption
                        {:url (cover-url series-title volume-num)
                         :href (page-url series-title volume-num 1)
                         :caption [:p
                                   "Volume " volume-num
                                   "&nbsp;"
                                   [:a {:href (download-volume-url series-title volume-num)} "(Download)"]]}))))]))))

(defn css [style-map]
  (->> style-map
       (map (fn [[key val]]
              (str (name key) ": " (str val))))
       (clojure.string/join ";")))

(defn show-page [{:keys [library series-title volume-num page-num]}]
  (page (str series-title " #" volume-num)
        [:div
         [:a {:href (page-url series-title volume-num (inc page-num))}
          [:img {:src (page-image-url series-title volume-num page-num)
                 :usemap "pagemap"
                 :style (css {:width "auto"
                              :height "100%"
                              :min-height "50%"})}]]]))

(defn- likely-cover-image [library series-title volume-num]
  (let [volume (first (library/query-volumes-like library {::library/title series-title
                                                           ::library/volume-num volume-num}))]
    (when-let [wide-cover (->> (library/volume-pages volume)
                               (take 10)
                               (filter #(> (im/aspect %) 1))
                               (first))]
      (assoc wide-cover
             ::im/crop ::im/left-half))))

(defn cover-image [{:keys [library series-title volume-num]}]
  (response/response
   (ring-io/piped-input-stream
    (fn [output-stream]
      (try
        (let [volume (first (library/query-volumes-like library {::library/title series-title
                                                                 ::library/volume-num volume-num}))
              cover-image (or ;; (likely-cover-image library series-title volume-num)
                           (first (library/volume-pages volume)))]
          (-> cover-image
              (assoc ::im/width 200)
              (im/render-to-output-stream output-stream)))
        (catch Exception e
          (println e)
          (throw e)))))))

(defn page-image [{:keys [library series-title volume-num page-num]}]
  (response/response
   (ring-io/piped-input-stream
    (fn [output-stream]
      (let [volume (first (library/query-volumes-like library {::library/title series-title
                                                               ::library/volume-num volume-num}))]
        (-> (library/volume-page volume page-num)
            (im/render-to-output-stream output-stream)))))))

(defn download-volume [{:keys [library series-title volume-num]}]
  (response/response
   (let [volume (first (library/query-volumes-like library {::library/title series-title
                                                            ::library/volume-num volume-num}))]
     (fs/content-stream volume))))

(defn maybe-parse-int [s]
  (when s
    (Integer/parseInt s)))

(defn munge-request-map [req]
  (let [{:keys [series-title volume-num page-num]} (:route-params req)]
    {:library (::library/library req)
     :series-title series-title
     :volume-num (maybe-parse-int volume-num)
     :page-num (maybe-parse-int page-num)}))

(defroutes app
  (GET "/" [:as req]
    (show-library (munge-request-map req)))
  (GET "/series/:series-title" [:as req]
    (show-series (munge-request-map req)))
  (GET "/series/:series-title/:volume-num/download/:file-name" [:as req]
    (download-volume (munge-request-map req)))
  (GET "/series/:series-title/:volume-num/cover.jpg" [:as req]
    (cover-image (munge-request-map req)))
  (GET "/series/:series-title/:volume-num/page/:page-num.jpg" [:as req]
    (page-image (munge-request-map req)))
  (GET "/series/:series-title/:volume-num/page/:page-num" [:as req]
    (show-page (munge-request-map req))))
