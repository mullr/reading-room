(defproject reading-room "0.1.0-SNAPSHOT"
  :description "TODO"
  :url "TODO"
  :license {:name "TODO: Choose a license"
            :url "http://choosealicense.com/"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha6"]
                 [bidi "2.0.9"]
                 [ring "1.5.0"]
                 [hiccup "1.0.5"]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [instaparse "1.4.2"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev"]}})
