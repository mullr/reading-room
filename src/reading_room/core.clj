(ns reading-room.core
  (:gen-class)
  (:require [clojure.spec :as s]
            compojure.response
            [environ.core :refer [env]]
            [prone.middleware :as prone]
            [reading-room.fs :as fs]
            [reading-room.library :as library]
            [reading-room.web :as web]
            [com.stuartsierra.component :as component]
            [reading-room.image :as image]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as interceptor]))

(defrecord PedestalRRServer [port library image-cache]
  component/Lifecycle
  (start [this]
    (let [s (-> #::http {:port port
                         :type :jetty
                         :join? false ; do not block thread that starts web server
                         :routes (fn [] (web/make-routes library image-cache))}
                ;; Wire up interceptor chains
                http/default-interceptors
                http/dev-interceptors
                http/create-server
                http/start)]
      (assoc this :server s)))

  (stop [this]
    (http/stop (:server this))
    (dissoc this :server)))

(defn make-system [{:keys [port library-path]}]
  (component/system-map
   :library (library/->Library library-path)
   :image-cache (image/->Cache)
   :sever (component/using (map->PedestalRRServer {:port port})
                           [:library :image-cache])))

(defn -main [& [port library-path]]
  (component/start (make-system {:port port :library-path library-path})))
