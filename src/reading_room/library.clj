(ns reading-room.library
  (:require [clojure.java.io :as io]
            [clojure.spec :as s]
            [clojure.spec.test :as stest]
            [clojure.string :as string]
            [instaparse.core :as insta]
            [reading-room.fs :as fs]))

(s/def ::title string?)
(s/def ::kind string?)
(s/def ::author string?)
(s/def ::volume-num integer?)

(s/def ::volume (s/keys :req [::title ::fs/path]
                        :opt [::kind ::author ::volume-num]))

(def manga-name-parser
  (insta/parser (io/resource "manga_name.bnf")))

(defn- map-entry-convert-fn [key xf]
  (fn [& args]
    [key (apply xf args)]))

(def ^:private field-conversion
  {:kind (map-entry-convert-fn ::kind str)
   :author (map-entry-convert-fn ::author str)
   :title (map-entry-convert-fn ::title str)
   :volume (map-entry-convert-fn ::volume-num #(Integer/parseInt %))})

(s/fdef parse-manga-file-name
        :args (s/cat :name string?)
        :ret (s/keys :req [::title] :opt [::kind ::author ::volume-num]))

(defn parse-manga-file-name [name]
  (let [ast (insta/parse manga-name-parser name)
        xf-ast (insta/transform field-conversion ast)]
    (->> xf-ast
         (filter (fn [[k v]] (namespace k)))
         (into {}))))

(defn- volume [volume-file]
  (merge volume-file
         (parse-manga-file-name (::fs/name volume-file))))

(defn- series [series-dir]
  (->> (fs/children series-dir)
       ;; (filter fs/file?)
       (map volume)
       (sort-by ::volume-num)))

(defn- coerce-to-file [x]
  (if (string? x)
    (fs/file x)
    x))

(s/fdef library
        :args (s/cat :library-dir (s/or :path string?
                                        :file ::fs/directory))
        :ret (s/coll-of ::volume))

(defn library [library-dir]
  (->> (coerce-to-file library-dir)
       fs/children
       (filter fs/directory?)
       (mapcat series)))

(defn- appears-to-be-image-file? [name]
  (let [name (.toLowerCase name)]
   (or
    (string/ends-with? name "jpg")
    (string/ends-with? name "jpeg")
    (string/ends-with? name "gif")
    (string/ends-with? name "bmp")
    (string/ends-with? name "png"))))

(defn- ignore-file-in-volume? [f]
  (let [name (::fs/name f)]
    (or
     (string/ends-with? name "/")
     (string/ends-with? name ".db")
     (string/starts-with? name ".")
     (not (appears-to-be-image-file? name)))))

(s/fdef pages
        :args (s/cat :volume ::volume)
        :ret (s/coll-of
              (s/and ::fs/file
                     (s/keys :req [::volume]))))

(defn volume-pages [volume]
  (->> (tree-seq fs/has-children? fs/children volume)
       (remove fs/has-children?)
       (remove ignore-file-in-volume?)
       (sort-by fs/sort-key)
       (map #(assoc % ::volume volume))))

(defn volume-page [volume n]
  (nth (volume-pages volume) n))

(defn query-volumes-like [library example]
  (filter #(= (select-keys % (keys example))
              example)
          library))

(defn query-first-volume-of-each-series [library]
  (->> library
       (group-by ::title)
       (map (fn [[_ vols-for-title]]
              (first (sort-by ::volume-num vols-for-title))))))

#_(defn latest-library-chan [library-dir]

  ;; make an internal channel that gets writes on the callback from dirwatch
  ;;  https://github.com/juxt/dirwatch
  (let [out-chan (async/chan)]
    (go-loop [latest-library]
      ;; select on the internal channel or a write to the output channel
      ;; when a value comes into the internal channel, go load the library and recur with it
      )
    out-chan))
