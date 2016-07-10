(ns reading-room.web.routes
  (:require [reading-room.core :as rr]
            [reading-room.zip :as zip]
            [reading-room.image :as im]
            [compojure.core :refer [defroutes GET]]
            [ring.util.response :as response]
            [ring.util.io :as ring-io]))

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

(defn show-series [{:keys [library series-title]}]
  (let [series (rr/series-with-title library series-title)]
    (if-not series
      [:div "Can't find series with title " series-title]
      (page series-title
            [:div
             [:h1
              (::rr/title series)
              "&nbsp;"
              [:a {:href "/"} "up"]]

             (grid (::rr/volumes series)
                   (fn [v]
                     (let [volume-num (::rr/volume-num v)]
                       (thumb-with-caption
                        {:url (cover-url series-title volume-num)
                         :href (page-url series-title volume-num 1)
                         :caption [:p "Volume " volume-num]}))))]))))

(defn show-page [{:keys [library series-title volume-num page-num]}]
  (page (str series-title " #" volume-num)
        [:div
         [:div.row
          [:a {:href (series-url series-title)} "up"]
          "&nbsp;"
          (when (> page-num 1)
            [:a {:href (page-url series-title volume-num (dec page-num))} "prev"])
          "&nbsp;"
          page-num
          "&nbsp;"
          (when true
            [:a {:href (page-url series-title volume-num (inc page-num))} "next"])]
         [:div.row
          [:img {:src (page-image-url series-title volume-num page-num)}]]]))


(defn- likely-cover-image [library series-title volume-num]
  (println "Looking for a cover iamge")
  (when-let [wide-cover (->> (rr/page-images library series-title volume-num)
                             (take 10)
                             (filter #(> (im/aspect %) 1))
                             (first))]
    (println "got candidate!" wide-cover)
    (assoc wide-cover
           ::im/crop ::im/left-half)))

(defn cover-image [{:keys [library series-title volume-num]}]
  (response/response
   (ring-io/piped-input-stream
    (fn [output-stream]
      (try
        (let [cover-image (or ;; (likely-cover-image library series-title volume-num)
                              (rr/page-image library series-title volume-num 0))]
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
      (-> (rr/page-image library series-title volume-num page-num)
          (im/render-to-output-stream output-stream))))))

(defn maybe-parse-int [s]
  (when s
    (Integer/parseInt s)))

(defn munge-request-map [req]
  (let [{:keys [series-title volume-num page-num]} (:route-params req)]
    {:library (::rr/library req)
     :series-title series-title
     :volume-num (maybe-parse-int volume-num)
     :page-num (maybe-parse-int page-num)}))

(defroutes app
  (GET "/" [:as req]
    (show-library (munge-request-map req)))
  (GET "/series/:series-title" [:as req]
    (show-series (munge-request-map req)))
  (GET "/series/:series-title/:volume-num/cover.jpg" [:as req]
    (cover-image (munge-request-map req)))
  (GET "/series/:series-title/:volume-num/page/:page-num.jpg" [:as req]
    (page-image (munge-request-map req)))
  (GET "/series/:series-title/:volume-num/page/:page-num" [:as req]
    (show-page (munge-request-map req))))
