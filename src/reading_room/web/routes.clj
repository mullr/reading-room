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
                       [:p "Volume " volume-num]]
                      ]]))])]))))


;; <div class="row">
;; <div class="col-sm-6 col-md-4">
;; <div class="thumbnail">
;; <img src="..." alt="...">
;; <div class="caption">
;; <h3>Thumbnail label</h3>
;; <p>...</p>
;; <p><a href="#" class="btn btn-primary" role="button">Button</a> <a href="#" class="btn btn-default" role="button">Button</a></p>
;; </div>
;; </div>
;; </div>
;; </div>

(defn volume-cover-image [{:keys [route-params ::rr/library]}]
  (let [{:keys [title volume-num]} route-params
        file (-> library
                 (rr/series-with-title title)
                 (rr/volume (Integer/parseInt volume-num))
                 ::rr/path)
        cover-entry (first (zip/zip-entries file))
        img (BufferedImage. 200 -1 BufferedImage/TYPE_INT_RGB)
        _ (ImageIO/write )
        scaled (.getScaledInstance img)]

    ;; BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    ;; img.createGraphics().drawImage(ImageIO.read(new File("test.jpg")).getScaledInstance(100, 100, Image.SCALE_SMOOTH),0,0,null);
    ;; ImageIO.write(img, "jpg", new File("test_thumb.jpg"));

    (zip/zip-entry-stream file cover-entry)))

(defroutes app
  (GET "/" [] show-library)
  (GET "/series/:title" [] show-series)
  (GET "/series/:title/:volume-num/cover" [] volume-cover-image))
