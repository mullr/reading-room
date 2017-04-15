(ns reading-room.web
  (:require [hiccup.core :as hiccup]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition.table :as table]
            [reading-room.fs :as fs]
            [reading-room.image :as im]
            [reading-room.library :as library]
            [ring.util.codec :as codec]
            [ring.util.response :as response]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [io.pedestal.interceptor.helpers :as interceptor]))

(defn url-for
  "Slightly terser wrapper for route/url-for"
  [route-name & opts]
  (if (map? (first opts))
    (apply route/url-for route-name :path-params opts)
    (apply route/url-for route-name opts)))

(defn download-volume-url [title volume-num]
  (str "/series/" title "/" volume-num "/download/" title " - " volume-num ".zip"))

(defn page
  ([title content] (page title {} content))
  ([title html-opts content]
   (response/response
    (hiccup/html
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
       ]]))))

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

(defn show-library [library req]
  (page "library"
        [:div
         (grid (library/query-first-volume-of-each-series library)
               (fn [{:keys [::library/author ::library/title]}]
                 (thumb-with-caption
                  {:url (url-for :cover-image {:series-title title :volume-num 1})
                   :href (url-for :series {:series-title title})
                   :caption [:p title "&nbsp;"
                             (when author (str "(" author  ")"))]})))]))

(defn show-series [library {{:keys [series-title]} :path-params}]
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
                     (let [volume-num (::library/volume-num v)
                           volume-path-params {:series-title series-title
                                               :volume-num volume-num
                                               :file-name (str series-title ".zip")
                                               :page-num 1}]
                       (thumb-with-caption
                        {:url (url-for :cover-image volume-path-params)
                         :href (url-for :page volume-path-params)
                         :caption [:p
                                   "Volume " volume-num
                                   "&nbsp;"
                                   [:a {:href (url-for :download-volume volume-path-params)} "(Download)"]]}))))]))))

(defn css [style-map]
  (->> style-map
       (map (fn [[key val]]
              (str (name key) ": " (str val))))
       (clojure.string/join ";")))

(defn show-page [library {{:keys [series-title volume-num page-num]} :path-params}]
  (let [this-page-path-params {:series-title series-title
                               :volume-num volume-num
                               :page-num page-num}
        next-page-path-params (update this-page-path-params :page-num inc)]
    (page (str series-title " #" volume-num)
          [:div
           [:a {:href (url-for :page next-page-path-params)}
            [:img {:src (url-for :page-image this-page-path-params)
                   :usemap "pagemap"
                   :style (css {:width "auto"
                                :height "100%"
                                :min-height "50%"})}]]])))

(defn- likely-cover-image [library series-title volume-num]
  (let [volume (first (library/query-volumes-like library {::library/title series-title
                                                           ::library/volume-num volume-num}))]
    (when-let [wide-cover (->> (library/volume-pages volume)
                               (take 10)
                               (filter #(> (im/aspect %) 1))
                               (first))]
      (assoc wide-cover
             ::im/crop ::im/left-half))))

(defn cover-image [library image-cache {{:keys [series-title volume-num]} :path-params}]
  (response/response
   (fn [output-stream]
     (try
       (let [volume (first (library/query-volumes-like library {::library/title series-title
                                                                ::library/volume-num volume-num}))
             cover-image (or ;; (likely-cover-image library series-title volume-num)
                          (first (library/volume-pages volume)))]
         (-> cover-image
             (assoc ::im/width 200)
             (im/render-to-output-stream image-cache output-stream)))
       (catch Exception e
         (println e)
         (throw e))))))

(defn page-image [library image-cache {{:keys [series-title volume-num page-num]} :path-params}]
  (response/response
   (fn [output-stream]
     (let [volume (first (library/query-volumes-like library {::library/title series-title
                                                              ::library/volume-num volume-num}))]
       (-> (library/volume-page volume page-num)
           (im/render-to-output-stream image-cache output-stream))))))

(defn download-volume [library {{:keys [series-title volume-num]} :path-params}]
  (response/response
   (let [volume (first (library/query-volumes-like library {::library/title series-title
                                                            ::library/volume-num volume-num}))]
     (fs/content-stream volume))))

(defn when-not-nil [f]
  (fn [x]
    (when x
      (f x))))

(defn parse-int [x]
  (Integer/parseInt x))

(interceptor/defbefore parse-path-params [req]
  (update-in req [:request :path-params]
             #(-> %
                  (update :volume-num (when-not-nil parse-int))
                  (update :page-num (when-not-nil parse-int))
                  (update :series-title (when-not-nil codec/url-decode))
                  (update :file-name (when-not-nil codec/url-decode)))))

(def common-interceptors
  [http/html-body parse-path-params])
(defn webjar-handler [path]
  (wrap-webjars
   (fn [req] (response/not-found "asset not found"))
   path))

(defn make-routes [library image-cache]
  (table/table-routes
   [["/"
     :get (conj common-interceptors (partial show-library library))
     :route-name :root]

    ["/assets/*"
     :get (webjar-handler "/assets")
     :route-name :webjars]

    ["/series/:series-title"
     :get (conj common-interceptors (partial show-series library))
     :route-name :series]

    ["/series/:series-title/:volume-num/page/:page-num"
     :get (conj common-interceptors (partial show-page library))
     :route-name :page]

    ["/series/:series-title/:volume-num/download/:file-name"
     :get (conj common-interceptors (partial download-volume library))
     :route-name :download-volume]

    ["/series/:series-title/:volume-num/cover.jpg"
     :get (conj common-interceptors (partial cover-image library image-cache))
     :route-name :cover-image]

    ["/series/:series-title/:volume-num/page/:page-num/full.jpg"
     :get (conj common-interceptors (partial page-image library image-cache))
     :route-name :page-image]]))


