(ns reading-room.web.routes
  (:require [reading-room.core :as rr]
            [reading-room.zip :as zip]
            [compojure.core :refer [defroutes GET]]))

(defn series-url [title]
  (str "/series/" title))

(defn volume-cover-url [title volume-num]
  (str "/series/" title "/" volume-num "/cover"))

(defn show-library [req]
  [:div
   (for [{:keys [::rr/author ::rr/title ::rr/volumes]}
         (::rr/library req)]
     [:div
      [:dl
       [:dt "Author"] [:dd author]
       [:dt "Title"] [:dd [:a {:href (series-url title)}
                           title]]
       [:dt "Volume count"] [:dd (count volumes)]]])])

(defn show-series [{:keys [route-params ::rr/library]}]
  (let [title (:title route-params)
        series (rr/series-with-title library title)]
    (if-not series
      [:div "Can't find series with title " title]
      [:span
       [:p "Title:" (::rr/title series)]
       (for [v (::rr/volumes series)]
         (let [volume-num (::rr/volume-num v)]
           [:figure
            [:img {:src (volume-cover-url title volume-num)
                   :width "150px"
                   :height "200px"}]
            [:figcaption "Volume " volume-num]]))])))

(defn volume-cover-image [{:keys [route-params ::rr/library]}]
  (let [{:keys [title volume-num]} route-params
        file (-> library
                 (rr/series-with-title title)
                 (rr/volume (Integer/parseInt volume-num))
                 ::rr/path)
        cover-entry (first (zip/zip-entries file))]
    (zip/zip-entry-stream file cover-entry)))

(defroutes app
  (GET "/" [] show-library)
  (GET "/series/:title" [] show-series)
  (GET "/series/:title/:volume-num/cover" [] volume-cover-image))
