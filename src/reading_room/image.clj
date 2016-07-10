(ns reading-room.image
  (:require [reading-room
             [core :as rr]
             [zip :as zip]]
            [clojure.core.cache :as cache]
            [clojure.spec :as s]
            [mikera.image.core :as imagez]
            [clojure.java.io :as io])
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

(defn render [image]
  (let [buffered-image (with-open [s (image-stream image)]
                    (ImageIO/read s))]
    (if-let [w (::width image)]
      (Scalr/resize buffered-image w nil)
      buffered-image)))

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
  (contains? image ::width))

(defn render-to-output-stream [image output-stream]
  (if (cachable? image)
    (if-let [image-bytes (cache/lookup @*render-cache* image)]
      (do
        (swap! *render-cache* cache/hit image)
        (io/copy (java.io.ByteArrayInputStream. image-bytes) output-stream))
      (let [image-bytes (render-to-byte-array-nocache image)]
        (swap! *render-cache* cache/miss image image-bytes)
        (io/copy (java.io.ByteArrayInputStream. image-bytes) output-stream)))
    (render-to-output-stream-nocache image output-stream)))
