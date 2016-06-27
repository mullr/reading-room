(ns reading-room.web.routes
  (:require [reading-room.core :as rr]
            [reading-room.zip :as zip]
            [reading-room.image :refer [volume-image-stream thumbnail-image-stream]]
            [compojure.core :refer [defroutes GET]]))

(defn series-url [title]
  (str "/series/" title))

(defn cover-url [title volume-num]
  (str "/series/" title "/" volume-num "/cover.jpg"))

(defn page-url [title volume-num page-num]
  (str "/series/" title "/" volume-num "/page/" page-num))

(defn page-image-url [title volume-num page-num]
  (str (page-url title volume-num page-num) ".jpg"))

(defn page [title content]
  ;; bootstrap boilerplate
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
    [:div.container
     content]
    [:script {:src "https://ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js"}]
    [:script {:src "/assets/bootstrap/js/bootstrap.js"}]]])

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
         (grid library
               (fn [{:keys [::rr/author ::rr/title ::rr/volumes]}]
                 (thumb-with-caption
                  {:url (cover-url title 1)
                   :href (series-url title)
                   :caption [:p title "&nbsp;"
                             (when author (str "(" author  ")"))]})))]))

(defn show-series [{:keys [library title]}]
  (let [series (rr/series-with-title library title)]
    (if-not series
      [:div "Can't find series with title " title]
      (page title
            [:div
             [:h1
              (::rr/title series)
              "&nbsp;"
              [:a {:href "/"} "up"]]

             (grid (::rr/volumes series)
                   (fn [v]
                     (let [volume-num (::rr/volume-num v)]
                       (thumb-with-caption
                        {:url (cover-url title volume-num)
                         :href (page-url title volume-num 1)
                         :caption [:p "Volume " volume-num]}))))]))))

(defn show-page [{:keys [library title volume-num page-num]}]
  (page (str title " #" volume-num)
        [:div
         [:div.row
          [:a {:href (series-url title)} "up"]
          "&nbsp;"
          (when (> page-num 1)
            [:a {:href (page-url title volume-num (dec page-num))} "prev"])
          "&nbsp;"
          page-num
          "&nbsp;"
          (when true
            [:a {:href (page-url title volume-num (inc page-num))} "next"])]
         [:div.row
          [:img {:src (page-image-url title volume-num page-num)}]]]))


(defn cover-image [{:keys [library title volume-num]}]
  (thumbnail-image-stream
   (volume-image-stream library title volume-num 0)))

(defn page-image [{:keys [library title volume-num page-num]}]
  (volume-image-stream library title volume-num (dec page-num)))

(defn maybe-parse-int [s]
  (when s
    (Integer/parseInt s)))

(defn munge-request-map [req]
  (let [{:keys [title volume-num page-num]} (:route-params req)]
    {:library (::rr/library req)
     :title title
     :volume-num (maybe-parse-int volume-num)
     :page-num (maybe-parse-int page-num)}))

(defroutes app
  (GET "/" [:as req]
    (show-library (munge-request-map req)))
  (GET "/series/:title" [:as req]
    (show-series (munge-request-map req)))
  (GET "/series/:title/:volume-num/cover.jpg" [:as req]
    (cover-image (munge-request-map req)))
  (GET "/series/:title/:volume-num/page/:page-num.jpg" [:as req]
    (page-image (munge-request-map req)))
  (GET "/series/:title/:volume-num/page/:page-num" [:as req]
    (show-page (munge-request-map req))))
