(ns pdok.featured.mustache-test
   (:require [clojure.java.io :as io]
             [clojure.test :refer :all]
             [cheshire.core :as json]
             [pdok.featured.mustache :refer :all]
             [pdok.featured.feature :refer :all]))


(def example-gml-bgt-wegdeel
  "<gml:Surface srsName=\"urn:ogc:def:crs:EPSG::28992\"><gml:patches><gml:PolygonPatch><gml:exterior><gml:LinearRing><gml:posList srsDimension=\"2\" count=\"5\">172307.599 509279.740 172307.349 509280.920 172306.379 509280.670 172306.699 509279.490 172307.599 509279.740</gml:posList></gml:LinearRing></gml:exterior></gml:PolygonPatch></gml:patches></gml:Surface>"
   )
 
(def example-geometry-bgt-wegdeel {"gml" example-gml-bgt-wegdeel, "type" "gml"})

(def example-attributes-bgt-wegdeel
  { "_traffic-area-gml-id" "abcdef"
    "_creation-data" "2014-12-05"
    "LV-publicatiedatum" "2015-02-05T17:22:59.000"
    "tijdstipRegistratie" "2014-12-05T15:32:38.000"
    "inOnderzoek" false
    "relatieveHoogteligging" 0
    :bronhouder "G4"
    "bronhouder_leeg" "BGT"
    "lokaalID" "G0303.0979f33001fd319ae05332a1e90a5e0b"
    :kruinlijn {:geo example-geometry-bgt-wegdeel}})

(def example-feature-bgt-wegdeel 
                    (merge
                    {"_action" "new"}
                    {:_geometry example-geometry-bgt-wegdeel}
                     example-attributes-bgt-wegdeel)
)

(defn example-features [n] (repeat n example-feature-bgt-wegdeel))

(defn render-wegdeel-with-example [n] 
   (map (partial render-resource-m "pdok/featured/templates/bgt-wegdeel.template") 
      (example-features n)))

  
 (deftest test-features-mapping
   (is (= 5 (count(filter #(re-find #"G0303.0979f33001fd319ae05332a1e90a5e0b" %) (render-wegdeel-with-example 5)))))
   (is (= 5 (count(filter #(re-find #"<imgeo:inOnderzoek>false</imgeo:inOnderzoek>" %) (render-wegdeel-with-example 5))))))

 
 (deftest test-features-gml-mapping
   (is (= 5 (count(filter #(re-find #"<gml:posList" %) (render-wegdeel-with-example 5))))))
 
 (defn write-gml-files [n]
    (time (with-open [w (clojure.java.io/writer "target/features.gml.json")]
       (json/generate-stream {:features (render-wegdeel-with-example n)} w))))
  
