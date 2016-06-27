(ns reading-room.web
  (:require [clojure.spec :as s]
            [reading-room.core :as rr]
            [ring.adapter.jetty :as jetty]
            [hiccup.core :as hiccup]
            [ring.util.response :as res]
            [reading-room.web.routes :as routes]
            [prone.middleware :as prone]
            [compojure.response]
            [ring.middleware.webjars :refer [wrap-webjars]])
  (:import org.eclipse.jetty.server.Server
           java.net.URLDecoder))

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


(defn wrap-add-config [handler config]
  (fn [req]
    (handler (assoc req ::config config))))


(defn wrap-add-library [handler library-path]
  (fn [req]
    (handler (assoc req
                    ::rr/library (rr/load-library library-path)))))

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

(defn make-app [config]
  (-> (fn [req]
        (routes/app req))
      (wrap-add-library (::library-path config))
      (wrap-add-config config)
      wrap-request-uri-decode
      wrap-webjars
      prone/wrap-exceptions))

(def system
  {::config {::jetty-config {:port 3000
                             :join? false}
             ::library-path "/home/mullr/storage/Manga"}})

(defn start [{:keys [::config] :as s}]
  (let [handler (make-app config)]
    (assoc s
           :ring/handler handler
           ::server (jetty/run-jetty handler
                                     (::jetty-config config)))))

(defn stop [s]
  (.stop (::server s))
  (dissoc s ::server))
