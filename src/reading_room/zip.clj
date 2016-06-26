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

(defn call-with-zip-entry-stream [path entry-name f]
  (with-open [zf (zipfile path)]
    (let [entry (.getEntry zf entry-name)]
      (with-open [entry-stream (.getInputStream zf entry)]
        (f entry-stream)))))
