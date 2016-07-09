(ns reading-room.zip
  (:import [java.util.zip ZipFile]
           [java.nio.charset Charset]))

(def charsets-to-try
  ["UTF-8"
   "Shift-JIS"
   "EUC-JP"
   "windows-31j"])

(defn call-with-charset-until-one-works [f]
  (loop [[charset & charsets] charsets-to-try]
    (let [result (try
                   (f charset)
                   (catch java.lang.IllegalArgumentException e
                     e))]
      (if (instance? java.lang.Exception result)
        (if (seq charsets)
          (recur charsets)
          (throw result))
        result))))

(def detect-zip-encoding
  (memoize (fn [path]
             (call-with-charset-until-one-works
              (fn [charset]
                (with-open [zf (ZipFile. path (Charset/forName charset))]
                  (doall
                   (for [entry (enumeration-seq (.entries zf))]
                     (.getName entry))))
                charset)))))

(defn- zipfile [path]
  (ZipFile. path (Charset/forName (detect-zip-encoding path))))

(defn ignore-entry? [name]
  (or
   (clojure.string/ends-with? name "/")
   (clojure.string/ends-with? name ".db")
   (clojure.string/starts-with? name ".")))

(defn zip-entries
  ([path]
   (with-open [zf (zipfile path)]
     (->> (enumeration-seq (.entries zf))
          (map #(.getName %))
          (filter (comp not ignore-entry?))
          (sort)))))

(defn zip-entry-stream [path entry-name]
  (let [zf (zipfile path)
        entry (.getEntry zf entry-name)]
    (.getInputStream zf entry)))
