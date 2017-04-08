(ns reading-room.fs-test
  (:require [reading-room.fs :as fs]
            [clojure.test :as t]
            [clojure.set :as set]))

;; helpers for zip nodes
(defn zipped-dir [name children]
  {::fs/type :zipped-directory
   ::fs/name name
   ::fs/children (->> children
                      (map (juxt ::fs/name identity))
                      (into {}))})

(defn zipped-file [name]
  {::fs/type :zipped-file
   ::fs/name name})

(t/deftest add-node-to-tree-test
  (t/are [root path node out] (= out (#'fs/add-node-to-tree root path node))
    (zipped-dir "root" []) [] (zipped-file "test.txt")
    (zipped-dir "root" [(zipped-file "test.txt")])

    (zipped-dir "root" []) ["a"] (zipped-file "test.txt")
    (zipped-dir "root" [(zipped-dir "a" [(zipped-file "test.txt")])])

    (zipped-dir "root" []) ["a" "b"] (zipped-file "test.txt")
    (zipped-dir "root" [(zipped-dir "a" [(zipped-dir "b" [(zipped-file "test.txt")])])])

    (zipped-dir "root" [(zipped-file "test.txt")]) ["a"] (zipped-file "test.txt")

    (zipped-dir "root" [(zipped-file "test.txt")
                        (zipped-dir "a" [(zipped-file "test.txt")])])))

(t/deftest test-dir
  (let [f (fs/file "test_resources/test_dir")]
    (t/is (= #::fs {:name "test_dir"
                    :type :directory}
             (dissoc f ::fs/path)))

    (t/is (= #::fs {:name "test.txt"
                    :type :file}
             (dissoc (first (fs/children f))
                     ::fs/path)))

    (t/is (= "This is the dir content.\n"
             (-> (first (fs/children f))
                 fs/content-stream
                 slurp)))))

(t/deftest test-zip
  (let [f (fs/file "test_resources/test.zip")]
    (t/is (= #:fs {:name "test.zip"
                   :type :file})
          (dissoc f ::fs/path))

    (t/is (= "a" (-> f fs/children first ::fs/name)))
    (t/is (= :zipped-directory (-> f fs/children first ::fs/type)))

    (t/is (= "file.txt" (-> f fs/children first fs/children first ::fs/name)))
    (t/is (= :zipped-file (-> f fs/children first fs/children first ::fs/type)))

    (t/is (= "This is the zip content.\n"
             (-> f fs/children first fs/children first fs/content-stream slurp)))))
