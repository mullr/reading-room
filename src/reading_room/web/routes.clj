(ns reading-room.web.routes
  (:require [reading-room.core :as rr]
            [reading-room.zip :as zip]
            [compojure.core :refer [defroutes GET]])
  (:import [java.awt.image BufferedImage]
           [javax.imageio ImageIO]))

(defn series-url [title]
  (str "/series/" title))

(defn volume-cover-url [title volume-num]
  (str "/series/" title "/" volume-num "/cover"))

(defn page [title content]
  [:html {:lang "en"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    ;; The above 3 meta tags *must* come first in the head; any other head
    ;; content must come *after* these tags
    [:title title]

    [:link {:href "/assets/bootstrap/css/bootstrap.css" :rel "stylesheet"}]
    [:link {:href "/assets/bootstrap/css/bootstrap-theme.css" :rel "stylesheet"}]]
   [:body {:role "document"}
    content
    [:script {:src "https://ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js"}]
    [:script {:src "/assets/bootstrap/js/bootstrap.js"}]]])

(defn show-library [req]
  (page "library"
   [:div
    (for [{:keys [::rr/author ::rr/title ::rr/volumes]}
          (::rr/library req)]
      [:div
       [:dl
        [:dt "Author"] [:dd author]
        [:dt "Title"] [:dd [:a {:href (series-url title)}
                            title]]
        [:dt "Volume count"] [:dd (count volumes)]]])]))

(defn show-series [{:keys [route-params ::rr/library]}]
  (let [title (:title route-params)
        series (rr/series-with-title library title)]
    (if-not series
      [:div "Can't find series with title " title]
      (page title
            [:div.container
             [:h1 (::rr/title series)]

             (for [row (partition-all 5 (::rr/volumes series))]
               [:div.row
                (for [v row]
                  (let [volume-num (::rr/volume-num v)]
                    [:div.col-sm-6.col-md-4
                     [:div.thumbnail
                      [:img {:src (volume-cover-url title volume-num)}]
                      [:div.caption
                       [:p "Volume " volume-num]]]]))])]))))

(defn render-image [in]
  (let [w (.getWidth in nil)
        h (.getHeight in nil)
        out (BufferedImage. w h BufferedImage/TYPE_INT_RGB)
        graphics (.createGraphics out)]
    (try
      (.drawImage graphics in 0 0 nil)
      out
      (finally
        (.dispose graphics)))))

(defn volume-cover-image [{:keys [route-params ::rr/library]}]
  (let [{:keys [title volume-num]} route-params
        file (-> library
                 (rr/series-with-title title)
                 (rr/volume (Integer/parseInt volume-num))
                 ::rr/path)
        cover-entry (first (zip/zip-entries file))
        img-stream (zip/zip-entry-stream file cover-entry)
        in (ImageIO/read img-stream)
        scaled-img (render-image
                    (.getScaledInstance in 200 200 java.awt.Image/SCALE_SMOOTH))
        byte-stream (java.io.ByteArrayOutputStream.)]
    ;; this is pretty darn inefficient
    (ImageIO/write scaled-img "jpg" byte-stream)
    (java.io.ByteArrayInputStream. (.toByteArray byte-stream))))

(defroutes app
  (GET "/" [] show-library)
  (GET "/series/:title" [] show-series)
  (GET "/series/:title/:volume-num/cover" [] volume-cover-image))
