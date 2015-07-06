(ns pdok.featured.config
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [pdok.featured
             [persistence :as pers]
             [projectors :as proj]
             [timeline :as timeline]]
            [environ.core :refer [env]]))
;;LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME).setLevel(Level.FINE);

(-> (java.util.logging.LogManager/getLogManager) (.readConfiguration (io/input-stream (io/resource "logging.properties"))))

(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread throwable]
     (log/error throwable))))

(def processor-db {:subprotocol "postgresql"
                     :subname (or (env :processor-database-url) "//localhost:5432/pdok")
                     :user (or (env :processor-database-user) "postgres")
                     :password (or (env :processor-database-password) "postgres")})

(def data-db {:subprotocol "postgresql"
                     :subname (or (env :data-database-url) "//localhost:5432/pdok")
                     :user (or (env :data-database-user) "postgres")
                     :password (or (env :data-database-password) "postgres")})

(defn persistence []
  (log/trace "Creating persistence. DB: " processor-db)
  (pers/cached-jdbc-processor-persistence
                    {:db-config processor-db :batch-size 10000}))

(defn projectors [persistence]
  (log/trace "Creating projectors. DB: " data-db)
  [(proj/geoserver-projector {:db-config data-db})
   (timeline/create {:db-config processor-db :persistence persistence})])
