(defproject reading-room "0.1.0-SNAPSHOT"
  :description "TODO"
  :url "TODO"
  :license {:name "TODO: Choose a license"
            :url "http://choosealicense.com/"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [hiccup "1.0.5"]
                 [instaparse "1.4.2"]
                 [prone "1.1.4" :exclusions [org.clojure/clojure]]
                 [ring/ring-codec "1.0.1" :exclusions [org.clojure/clojure]]
                 [compojure "1.5.1"]
                 [org.webjars/bootstrap "4.0.0-alpha.2"]
                 [org.webjars/font-awesome "4.6.3"]
                 [org.clojure/core.cache "0.6.5" :exclusions [org.clojure/clojure]]
                 [org.imgscalr/imgscalr-lib "4.2"]
                 [net.mikera/imagez "0.10.0" :exclusions [org.clojure/clojure]]
                 [environ "1.1.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [io.pedestal/pedestal.jetty "0.5.2"]
                 [io.pedestal/pedestal.route "0.5.2"]
                 [io.pedestal/pedestal.service "0.5.2"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev"]
                   :env {:port 4000}}}

  :main reading-room.web)
