(ns reading-room.zip
  (:import [java.util.zip ZipFile]))

(defn- zipfile [path]
  (ZipFile. path))

(defn zip-entries [path]
  (with-open [zf (zipfile path)]
    (doall
     (for [entry (enumeration-seq (.entries zf))]
       (.getName entry)))))

(defn zip-entry-stream [path entry-name]
  (let [zf (zipfile path)
        entry (.getEntry zf entry-name)]
    (.getInputStream zf entry)))
