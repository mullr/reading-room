(ns reading-room
  (:require [clojure.java.io :as io]
            [clojure.spec :as s]
            [ring.adapter.jetty :as jetty]
            [instaparse.core :as insta]
            [hiccup.core :as h]
            [bidi.ring :refer [make-handler]])
  (:import org.eclipse.jetty.server.Server))

(s/def ::path string?)
(s/def ::library (s/keys :req [::path]))

(s/def ::title string?)
(s/def ::series (s/keys :req [::title ::path]))

(s/def :jetty/port integer?)
(s/def :jetty/join? boolean?)
(s/def :jetty/server (partial instance? Server))
(s/def ::jetty-config
  (s/keys :req-un [:jetty/port]
          :opt-un [:jetty/join?]))

(s/def ::config (s/keys :req [::jetty-config
                              ::library]))

(s/def :ring/handler fn?)

(s/def ::system (s/keys :req [::config
                              :ring/handler
                              :jetty/server]))

(def manga-name-parser
  (insta/parser (io/resource "manga_name.bnf")))

(defn to-map-entry [key f & args]
  [key (apply f args)])

(defn manga-name-to-series [name]
  (let [ast (insta/parse manga-name-parser name)
        xf-ast (insta/transform
                {:kind (partial to-map-entry ::kind str)
                 :author (partial to-map-entry ::author str)
                 :title (partial to-map-entry ::title str)}
                ast)]
    (dissoc (into {} xf-ast)
            :suffix)))

(defn series [library]
  (let [library-file (io/file (::path library))]
    (->> (seq (.listFiles library-file))
         (filter (fn [f] (.isDirectory f)))
         (map (fn [f]
                (-> (manga-name-to-series (.getName f))
                    (assoc ::path (.getAbsolutePath f))) )))))

(defn root-handler [req]
  {:status 200
   :body
   (pr-str
    (series (-> req ::config ::library)))})

(def routes
  ["/" root-handler])

(defn wrap-add-config [handler config]
  (fn [req]
    (handler (assoc req ::config config))))

(defn make-app [config]
  (-> (fn [req] (root-handler req))
      (wrap-add-config config)))

(def system
  {::config {::jetty-config {:port 3000
                             :join? false}
             ::library {::path "/home/mullr/storage/Manga"}}})

(defn start [{:keys [::config ::make-handler] :as s}]
  (let [handler (make-app config)]
    (assoc s
           :ring/handler handler
           ::server (jetty/run-jetty handler
                                     (::jetty-config config)))))

(defn stop [s]
  (.stop (::server s))
  (dissoc s ::server))
