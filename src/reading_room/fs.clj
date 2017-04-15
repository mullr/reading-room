(ns reading-room.fs
  (:require [clojure.java.io :as io]
            [clojure.spec :as s]
            [clojure.string :as string]
            [reading-room.zip :as zip]
            [clojure.walk :as walk])
  (:import [java.nio.channels FileChannel]
           [java.nio.file Paths OpenOption]))

;;; A small VFS layer that knows how to reach inside zip files

(s/def ::name string?)
(s/def ::path string?)
(s/def ::path-in-zip string?)
(s/def ::children (s/map-of ::name ::file))

(defmulti file-type ::type)

(defmethod file-type :file [_]
  (s/keys :req [::name ::path]))

(defmethod file-type :directory [_]
  (s/keys :req [::name ::path]))

(defmethod file-type :zipped-file [_]
  (s/keys :req [::name ::path ::path-in-zip]))

(defmethod file-type :zipped-directory [_]
  (s/keys :req [::name ::path ::path-in-zip ::children]))


(s/def ::file (s/multi-spec file-type ::type))

(defn- add-node-to-tree [root path node]
  (if-not (seq path)
    (assoc-in root [::children (::name node)] node)
    (let [[name & names] path
          child-node (get-in root [::children name]
                             {::type :zipped-directory
                              ::name name
                              ::children {}})]
      (assoc-in root [::children name]
                (add-node-to-tree child-node names node)))))

(defn- roll-up-zip-entry-paths [archive-path entry-paths]
  (reduce (fn [tree path]
            (let [split-path (string/split path #"/")
                  new-file {::name (last split-path)
                            ::path archive-path
                            ::path-in-zip path
                            ::type (if (string/ends-with? path "/")
                                     :zipped-directory
                                     :zipped-file)}]
              (add-node-to-tree tree (drop-last split-path) new-file)))
          {::name "<root>"
           ::path archive-path
           ::path-in-zip ""
           ::type :zipped-directory
           ::children {}}
          entry-paths))

(defn- load-zip-file [archive-path]
  (roll-up-zip-entry-paths archive-path (zip/zip-entries archive-path)))

(s/fdef file :args (s/cat :root-path ::path) :ret ::file)
(defn file [root-path]
  (let [f (io/file root-path)]
   {::name (.getName f)
    ::path (.getAbsolutePath f)
    ::type (if (.isDirectory f)
             :directory
             :file)}))

(s/fdef file? :args (s/cat :f ::file) :ret boolean?)
(defn file? [f]
  (= :file (::type f)))

(s/fdef directory? :args (s/cat :f ::file) :ret boolean?)
(defn directory? [f]
  (= :directory (::type f)))

(defn- is-zip-file? [path]
  (string/ends-with? path ".zip"))

(defn children [f]
  (let [path (::path f)]
    (case (::type f)
      :file
      (when (is-zip-file? path)
        (vals (::children (load-zip-file path))))

      :zipped-file
      nil

      :directory
      (let [jf (io/file path)]
        (map file (seq (.listFiles jf))))

      :zipped-directory
      (sort-by ::name (vals (::children f))))))

(defn has-children? [f]
  (let [path (::path f)]
    (case (::type f)
      :file (is-zip-file? path)
      :zipped-file false
      :directory true
      :zipped-directory true
      false)))

(defn sort-key [f]
  (let [key-fn (case (::type f)
                 :file ::path
                 :zipped-file ::path-in-zip
                 :directory ::path
                 :zipped-directory ::path-in-zip)]
    (key-fn f)))

(defn- file-channel [path-str]
  (-> path-str
      (Paths/get (make-array String 0))
      (FileChannel/open (make-array OpenOption 0))))

(defn content-stream [f]
  (case (::type f)
    :file (file-channel (::path f))
    :zipped-file (zip/zip-entry-stream (::path f) (::path-in-zip f))))
