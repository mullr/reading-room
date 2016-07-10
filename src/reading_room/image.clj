(ns reading-room.image
  (:require [reading-room
             [core :as rr]
             [zip :as zip]]
            [clojure.core.cache :as cache]
            [clojure.spec :as s]
            [mikera.image.core :as imagez]
            [clojure.java.io :as io]
            [reading-room.image :as im])
  (:import java.awt.image.BufferedImage
           javax.imageio.ImageIO
           org.imgscalr.Scalr))

(defmulti image-type ::type)
(s/def ::basic-image (s/multi-spec image-type ::type))

(s/def ::image-path string?)
(defmethod image-type ::file [_]
  (s/keys :req [::image-path]))

(s/def ::archive-path string?)
(s/def ::entry-name string?)
(defmethod image-type ::archive [_]
  (s/keys :req [::archive-path ::entry-name]))

(s/def ::width integer?)
(s/def ::image (s/and ::basic-image
                      (s/keys :opt [::width])))

(defmulti image-stream ::type)

(defmethod image-stream ::file [image]
  (clojure.java.io/input-stream (::image-path image)))

(defmethod image-stream ::archive [image]
  (zip/zip-entry-stream (::archive-path image)
                        (::entry-name image)))

;;; image transformations

(defn resize-image [buffered-image width]
  (Scalr/resize buffered-image org.imgscalr.Scalr$Method/ULTRA_QUALITY width nil))

(defn crop-image [buffered-image kind]
  (case kind
    ::left-half (-> buffered-image
                    (.getSubimage 0 0 (/ (.getWidth buffered-image) 2) (.getHeight buffered-image))
                    imagez/copy)))

;;; regular rendering

(defn render [image]
  (let [buffered-image (with-open [s (image-stream image)] (ImageIO/read s))]
    (-> buffered-image
        ((if-let [crop-kind (::crop image)] #(crop-image % crop-kind) identity))
        ((if-let [width (::width image)] #(resize-image % width) identity)))))

;;; cached rendering

(defn- render-to-output-stream-nocache [image output-stream]
  (let [rendered (render image)]
    (imagez/write rendered output-stream "jpg" :quality 0.9)
    (.flush output-stream)))

(defn- render-to-byte-array-nocache [image]
  (let [byte-stream (java.io.ByteArrayOutputStream.)]
    (render-to-output-stream-nocache image byte-stream)
    (.flush byte-stream)
    (.toByteArray byte-stream)))

(def ^:dynamic *render-cache*
  (atom (cache/lru-cache-factory {} :threshold 1024)))

(defn- cachable? [image]
  (or (contains? image ::width)
      (contains? image ::crop)))

(defn- directly-streamable? [image]
  (and (not (contains? image ::width))
       (not (contains? image ::crop))))

(defn render-to-output-stream [image output-stream]
  (cond
    (directly-streamable? image) (with-open [s (im/image-stream image)]
                                   (io/copy s output-stream)
                                   (.flush output-stream))

    (cachable? image) (if-let [image-bytes (cache/lookup @*render-cache* image)]
                        (do
                          (swap! *render-cache* cache/hit image)
                          (io/copy (java.io.ByteArrayInputStream. image-bytes) output-stream))
                        (let [image-bytes (render-to-byte-array-nocache image)]
                          (swap! *render-cache* cache/miss image image-bytes)
                          (io/copy (java.io.ByteArrayInputStream. image-bytes) output-stream)))

    :default (render-to-output-stream-nocache image output-stream)))

(defn aspect [image]
  (let [buffered-image (with-open [s (image-stream image)]
                         (ImageIO/read s))]
    (/ (.getWidth buffered-image)
       (.getHeight buffered-image))))
