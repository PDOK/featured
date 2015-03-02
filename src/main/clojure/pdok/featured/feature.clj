(ns pdok.featured.feature
  (:require [pdok.featured.geometry :refer [geometry]]
            [cheshire.core :as json]
            [cheshire.factory :as jfac]
            [cheshire.parse :as jparse]
            [clj-time.format :as tf])
  (:import (com.fasterxml.jackson.core JsonFactory JsonFactory$Feature
                                       JsonParser$Feature JsonParser JsonToken)))

(defrecord UpdatePackage [dataset metadata feature-count])
(defrecord NewFeature [dataset collection id validity geometry free-fields])

(defmulti map-to-feature :_action)

;; 2015-02-26T15:48:26.578Z
(def ^{:private true} date-time-formatter (tf/formatters :date-time) )

(defn- parse-time
  "Parses a json date timestring: yyyy-MM-ddThh:mm:sss.mmmZ"
  [datetimestring]
  (tf/parse date-time-formatter datetimestring))

(defn- free-fields [obj]
  )

(defmethod map-to-feature "new" [obj]
  (let [collection (get obj "_collection")
        id (get obj "_id")
        validity (parse-time (get obj "_validity"))
        geom (geometry (get obj "_geometry"))]))

(defn parse-object [^JsonParser jp]
  (jparse/parse* jp identity nil nil))

(defn read-meta-data [^JsonParser jp]
  (.nextToken jp)
  (loop [state {}
         currentName (-> jp .getCurrentName .toLowerCase)]
    (if (= "features" currentName)
      state
      (let [obj (do (.nextToken jp) (parse-object jp))
            newName (-> (doto jp .nextToken) .getCurrentName .toLowerCase)]
        (recur (merge state {currentName obj}) newName)))
    ))

(defn read-features [^JsonParser jp]
  (if (and (.nextToken jp) (not= (.getCurrentToken jp) JsonToken/END_ARRAY))
    (lazy-seq (cons (parse-object jp) (read-features jp)))
    [])
  )

(defn features-from-package-stream* [jp]
  (.nextToken jp)
  (when (= JsonToken/START_OBJECT (.getCurrentToken jp))
    (let [meta (read-meta-data jp)]
      (if-not (contains? meta "dataset")
        (throw (Exception. "dataset needed"))
        ;; features should be array
        (when (= JsonToken/START_ARRAY (.nextToken jp))
          (read-features jp))
        ))))


(defn features-from-package-stream [input-stream]
  "Parse until `features' for state. Returns sequence of features."
  (let [reader (clojure.java.io/reader input-stream)
        factory jfac/json-factory
        parser (.createParser factory reader)
        features (features-from-package-stream* parser)]
    features))

(defn file-stream [path]
  (clojure.java.io/reader path))
