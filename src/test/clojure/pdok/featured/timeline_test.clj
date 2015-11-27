(ns pdok.featured.timeline-test
  (:require [pdok.featured.timeline :as timeline]
            [clojure.test :refer :all]))

;; merge target path feature

(defn- make-feature [attributes geometry id version]
  (cond-> {:dataset "merge-test"
           :collection "only-path-should-matter"
           :id id
           :attributes attributes}
          geometry (assoc :geometry geometry)
          version (assoc :version version)))

(defn- make-root [versions & tiles]
  {:_version (first versions) :_all_versions versions :_tiles (into #{} tiles)})

(defn- mustafied
  ([attributes] (mustafied attributes nil))
  ([attributes geom] (mustafied attributes geom "not-important"))
  ( [attributes geom id]
   (reduce (fn [m [k v]] (assoc m k v))
           {:_dataset "merge-test"
            :_collection "only-path-should-matter"
            :_id id}
           (cond-> attributes geom (assoc :_geometry geom)))))

(deftest single-depth-merge
  (let [path '()
        feature (make-feature {:attribute2 "value2"} nil "not-important" 1)
        merged (#'timeline/merge {:attribute1 "value1"} path feature)
        should-be (merge (make-root '(1)) (mustafied {:attribute1 "value1" :attribute2 "value2"}))]
    (is (= should-be merged))))

(deftest double-depth-merge ;; with tiles
  (let [path '(["parent-collection" "parent-id" "nested1" "child-id"])
        feature (make-feature {:attribute "value"} {:dummy :dummy :nl-tiles #{666}} "not-important" 3)
        merged (#'timeline/merge (make-root '(1) 1) path feature)
        should-be (merge (make-root '(3 1) 1 666)
                         {:nested1 [(mustafied {:attribute "value"} {:dummy :dummy :nl-tiles #{ 666}} )]})]
    (is (= should-be merged))))

(deftest triple-depth-merge
  (let [starts-with (assoc (make-root '(1) 1) :attr1 :a)
        path '(["pc" "pid" "nested_1" "id1"] ["pc2" "pid2" "nested_2" "id2"])
        feature (make-feature {:attributeX "valueY"} nil "not-important" 5)
        merged (#'timeline/merge starts-with path feature)
        should-be (merge (make-root '(5 1) 1)
                         {:attr1 :a :nested_1
                          [{:nested_2 [(mustafied {:attributeX "valueY"})]}]})]
    (is (= should-be merged))))

(deftest close-child-merge ;; with tiles
  (let [path '(["parent-collection" "parent-id" "nested1" "child-id"])
        feature (-> (make-feature {:attribute "value"} {:dummy :dummy} "child-id" 3)
                    (assoc :action :close))
        merged (#'timeline/merge
                (-> (make-root '(1) 1) (assoc :nested1 [(mustafied {:attribute "value"} {:dummy :dummy} "child-id")]))
                path
                feature)
        should-be (merge (make-root '(3 1) 1)
                         {:nested1 []})]
    (is (= should-be merged))))
