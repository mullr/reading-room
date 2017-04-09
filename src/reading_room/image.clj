(ns reading-room.image
  (:require [clojure.core.cache :as cache]
            [clojure.java.io :as io]
            [clojure.spec :as s]
            [mikera.image.core :as imagez]
            [reading-room.fs :as fs]
            [com.stuartsierra.component :as component])
  (:import (java.awt.image BufferedImage)
           (javax.imageio ImageIO)
           (org.imgscalr Scalr)
           (java.io ByteArrayOutputStream ByteArrayInputStream)))

(s/def ::width integer?)
(s/def ::height integer?)
(s/def ::crop #{:left-half})

(s/def ::image (s/and
                ::fs/file
                (s/keys :opt [::width])))

(s/def ::dimensions (s/keys :req [::width ::height]))


;;; image transformations

(defn resize-image [^:BufferedImage buffered-image width]
  (Scalr/resize buffered-image org.imgscalr.Scalr$Method/ULTRA_QUALITY width nil))

(defn crop-image [^:BufferedImage buffered-image kind]
  (case kind
    :left-half (-> buffered-image
                   (.getSubimage 0 0 (/ (.getWidth buffered-image) 2) (.getHeight buffered-image))
                   imagez/copy)))

;;; regular rendering

(defn render [image]
  (let [^:BufferedImage buffered-image (with-open [s (fs/content-stream image)]
                                         (ImageIO/read s))]
    (-> buffered-image
        ((if-let [crop-kind (::crop image)] #(crop-image % crop-kind) identity))
        ((if-let [width (::width image)] #(resize-image % width) identity)))))

;;; cached rendering

(defn- render-to-output-stream-nocache [image output-stream]
  (let [^:BufferedImage rendered (render image)]
    (imagez/write rendered output-stream "jpg" :quality 0.9)
    (.flush output-stream)))

(defn- render-to-byte-array-nocache [image]
  (let [byte-stream (ByteArrayOutputStream.)]
    (render-to-output-stream-nocache image byte-stream)
    (.flush byte-stream)
    (.toByteArray byte-stream)))

(defn- cachable? [image]
  (or (contains? image ::width)
      (contains? image ::crop)))

(defn- directly-streamable? [image]
  (and (not (contains? image ::width))
       (not (contains? image ::crop))))

(defn render-to-output-stream [image cache output-stream]
  (cond
    (directly-streamable? image) (with-open [s (fs/content-stream image)]
                                   (io/copy s output-stream)
                                   (.flush output-stream))

    (cachable? image) (let [{:keys [cache-atom]} cache]
                        (if-let [image-bytes (cache/lookup @cache-atom image)]
                          (do
                            (swap! cache-atom cache/hit image)
                            (io/copy (ByteArrayInputStream. image-bytes) output-stream))
                          (let [image-bytes (render-to-byte-array-nocache image)]
                            (swap! cache-atom cache/miss image image-bytes)
                            (io/copy (ByteArrayInputStream. image-bytes) output-stream))))

    :default (render-to-output-stream-nocache image output-stream)))

(defn dimensions [image-file]
  (let [^BufferedImage buffered-image (with-open [s (fs/content-stream image-file)]
                                        (ImageIO/read s))]
    {::width (.getWidth buffered-image)
     ::height (.getHeight buffered-image)}))

(defn aspect [image]
  (let [d (dimensions image)]
    (/ (::width d) (::height d))))

(defrecord Cache []
  component/Lifecycle
  (start [this]
    (assoc this :cache-atom
           (atom (cache/lru-cache-factory {} :threshold 1024))))
  (stop [this]
    (dissoc this :cache-atom)))

