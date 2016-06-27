(ns reading-room.image
  (:require [reading-room
             [core :as rr]
             [zip :as zip]])
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


(defn thumbnail-image-stream [full-image-stream]
  (let [in (ImageIO/read full-image-stream)
        scaled-img (render-image
                    (.getScaledInstance in 200 200 java.awt.Image/SCALE_SMOOTH))
        byte-stream (java.io.ByteArrayOutputStream.)]
    ;; this is pretty darn inefficient
    (ImageIO/write scaled-img "jpg" byte-stream)
    (java.io.ByteArrayInputStream. (.toByteArray byte-stream))))

