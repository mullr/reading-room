(ns reading-room.web
  (:gen-class)
  (:require [clojure.spec :as s]
            compojure.response
            [environ.core :refer [env]]
            [hiccup.core :as hiccup]
            [prone.middleware :as prone]
            [reading-room.fs :as fs]
            [reading-room.library :as library]
            [reading-room.web.routes :as routes]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.util.response :as res]
            [com.stuartsierra.component :as component]
            [reading-room.image :as image])
  (:import java.net.URLDecoder
           org.eclipse.jetty.server.Server))

(s/def :jetty/port integer?)
(s/def :jetty/join? boolean?)
(s/def :jetty/server (partial instance? Server))
(s/def ::jetty-config
  (s/keys :req-un [:jetty/port]
          :opt-un [:jetty/join?]))

(s/def :ring/handler fn?)

(s/def ::system (s/keys :req [::config
                              :ring/handler
                              :jetty/server]))

(s/def ::library-path string?)

(s/def ::config (s/keys :req [::jetty-config
                              ::library-path]))

(defn wrap-merge-map [handler m]
  (fn [req]
    (handler (merge req m req))))

(extend-protocol compojure.response/Renderable
  clojure.lang.PersistentVector
  (render [body _]
    (-> (hiccup/html body)
        res/response
        (res/content-type "text/html")
        (res/charset "UTF-8"))))

(defn wrap-request-uri-decode [handler]
  (fn [req]
    (handler (update req :uri #(URLDecoder/decode %)))))

(defn make-app [library image-cache]
  (-> (fn [req]
        (routes/app req))
      (wrap-merge-map {::library/library library
                       ::image/cache image-cache})
      wrap-request-uri-decode
      wrap-webjars
      prone/wrap-exceptions))

(defrecord RRServer [port library image-cache]
  component/Lifecycle
  (start [this]
    (let [handler (make-app library image-cache)
          server (jetty/run-jetty handler
                                  {:port port
                                   :join? false})]
      (assoc this
             :handler handler
             :server server)))

  (stop [this]
    (.stop (:server this))
    (dissoc this :server)))

(defn make-system [{:keys [port library-path]}]
  (component/system-map
   :library (library/->Library library-path)
   :image-cache (image/->Cache)
   :app (component/using (map->RRServer {:port port})
                         [:library :image-cache])))

(comment
  (def cs (atom (make-system {:port 4000
                              :library-path "/home/mullr/storage/Manga"})))

  (swap! cs component/start)

  (swap! cs component/stop)

  (keys @cs)
  (:app @cs)
  (:library @cs)
)

(defn -main [& [port library-path]]
  (component/start (c-system {:port port :library-path library-path})))
