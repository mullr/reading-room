(ns reading-room.image
  (:require [reading-room
             [core :as rr]
             [zip :as zip]]
            [clojure.core.cache :as cache])
  (:import java.awt.image.BufferedImage
           javax.imageio.ImageIO))

(defn- render-image [in]
  (let [w (.getWidth in nil)
        h (.getHeight in nil)
        out (BufferedImage. w h BufferedImage/TYPE_INT_RGB)
        graphics (.createGraphics out)]
    (try
      (.drawImage graphics in 0 0 nil)
      out
      (finally
        (.dispose graphics)))))

(defn volume-image-stream [library title volume-num page-index]
  (let [file (-> library
                 (rr/series-with-title title)
                 (rr/volume volume-num)
                 ::rr/path)
        page-entry (nth (zip/zip-entries file) page-index)]
    (zip/zip-entry-stream file page-entry)))


(defn thumbnail-image-bytes-impl [library title volume-num page-index dimension]
  ;; todo: with-open stuff in here
  (let [full-image-stream (volume-image-stream library title volume-num page-index)
        in (ImageIO/read full-image-stream)
        scaled-img (render-image
                    (.getScaledInstance in dimension dimension java.awt.Image/SCALE_SMOOTH))
        byte-stream (java.io.ByteArrayOutputStream.)]
    (ImageIO/write scaled-img "jpg" byte-stream)
    (.toByteArray byte-stream)))


(def ^:dynamic *thumbnail-cache*
  (atom (cache/lru-cache-factory {} :threshold 1024)))

(defn thumbnail-image-bytes [library title volume-num page-index dimension]
  (let [cache-key [title volume-num page-index dimension]]
    (if-let [thumb-bytes (cache/lookup @*thumbnail-cache* cache-key)]
      (do
        (swap! *thumbnail-cache*
               cache/hit cache-key)
        thumb-bytes)
      (let [thumb-bytes (thumbnail-image-bytes-impl library title volume-num page-index dimension)]
        (swap! *thumbnail-cache*
               cache/miss cache-key thumb-bytes)
        thumb-bytes))))
