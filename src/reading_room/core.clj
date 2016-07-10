(ns reading-room.core
  (:require [clojure.java.io :as io]
            [clojure.spec :as s]
            [instaparse.core :as insta]
            [reading-room.zip :as zip]))

(s/def ::path string?)
(s/def ::title string?)

(s/def ::kind string?)
(s/def ::author string?)
(s/def ::volume-num integer?)

(s/def ::volume (s/keys :req [::title ::path]
                        :opt [::kind ::author ::volume-num]))

(s/def ::volumes (s/coll-of ::volume))
(s/def ::series (s/keys :req [::title ::path ::volumes]
                        ::opt [::kind ::author]))

(s/def ::library (s/keys :req [::path ::volumes]))

(def manga-name-parser
  (insta/parser (io/resource "manga_name.bnf")))

(defn map-entry-convert-fn [key xf]
  (fn [& args]
    [key (apply xf args)]))

(def field-conversion
  {:kind (map-entry-convert-fn ::kind str)
   :author (map-entry-convert-fn ::author str)
   :title (map-entry-convert-fn ::title str)
   :volume (map-entry-convert-fn ::volume-num #(Integer/parseInt %))})

(defn parse-manga-file-name [name]
  (let [ast (insta/parse manga-name-parser name)
        xf-ast (insta/transform field-conversion ast)]
    (->> xf-ast
         (filter (fn [[k v]] (namespace k)))
         (into {}))))

(defn load-series [path]
  (let [series-dir (io/file path)]
    (->> (seq (.listFiles series-dir))
         (filter (fn [f] (.isFile f)))
         (map (fn [f]
                (-> (parse-manga-file-name (.getName f))
                    (assoc ::path (.getAbsolutePath f)))))
         (sort-by ::volume-num)
         (into []))))

(defn load-library [path]
  (let [library-dir (io/file path)]
    (->> (seq (.listFiles library-dir))
         (filter (fn [f] (.isDirectory f)))
         (map (fn [f]
                (let [path (.getAbsolutePath f)]
                  (assoc (parse-manga-file-name (.getName f))
                         ::path path
                         ::volumes (load-series path))))))))

(defn series-with-title [library title]
  (->> library (filter #(= title (::title %))) first))

(defn volume [series volume-num]
  (get (::volumes series) (dec volume-num)))

(defn page-image [library series-title volume-num page-index]
  (let [archive-path (-> library
                         (series-with-title series-title)
                         (volume volume-num)
                         ::path)]
    #:reading-room.image
    {:type :reading-room.image/archive
     :archive-path archive-path
     :entry-name (nth (zip/zip-entries archive-path) page-index)}))
