(defproject reading-room "0.1.0-SNAPSHOT"
  :description "TODO"
  :url "TODO"
  :license {:name "TODO: Choose a license"
            :url "http://choosealicense.com/"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha6"]
                 [ring "1.5.0"]
                 [hiccup "1.0.5"]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [instaparse "1.4.2"]
                 [prone "1.1.1" :exclusions [org.clojure/clojure]]
                 [ring/ring-codec "1.0.1" :exclusions [org.clojure/clojure]]
                 [compojure "1.5.1"]
                 [org.webjars/bootstrap "4.0.0-alpha.2"]
                 [org.webjars/font-awesome "4.6.3"]
                 [ring-webjars "0.1.1"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev"]}})
