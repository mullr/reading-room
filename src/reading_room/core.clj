(ns reading-room.core
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [reading-room.image :as image]
            [reading-room.library :as library]
            [reading-room.web :as web]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec :as s]))

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

(defn file-exists? [path]
  (.exists (io/file path)))

(s/def ::library-path (s/and string? file-exists?))
(s/def ::port nat-int?)
(s/def ::config (s/keys :req-un [::port ::library-path]))

(defn validate-config [config]
  (when-not (s/valid? ::config config)
    (s/explain ::config config)
    (throw (ex-info "Invalid config" (s/explain-data ::config config))))
  config)

(defn make-system [config-file]
  (let [config (-> config-file
                   slurp
                   edn/read-string
                   validate-config)
        {:keys [library-path port]} config]
    (component/system-map
     :library (library/->Library library-path)
     :image-cache (image/->Cache)
     :sever (component/using (map->PedestalRRServer {:port port})
                             [:library :image-cache]))))

(defn -main [config-file]
  (component/start (make-system config-file)))
