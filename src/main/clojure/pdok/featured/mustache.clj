(ns pdok.featured.mustache
   (:require [clostache.parser :as clostache]))

(defn resolve-as-function [function]
  (ns-resolve *ns* (symbol (str "pdok.featured.mustache-functions/" (name function)))))

(defn lookup-proxy [obj]
  (reify
    clojure.lang.ILookup
      (valAt [_ k]
           (if (and (map? obj)
                    (or (contains? obj k)(contains? obj (name k)))) 
             (let [value (get obj k (get obj (name k)))] 
                  (lookup-proxy value)) 
             (when-let [f (resolve-as-function k)]
               (lookup-proxy (f obj)))))
      
    clojure.lang.IPersistentCollection 
      (cons [_ o](lookup-proxy (conj obj o)))
      (seq [this] (if (or (map? obj) (string? obj)) (list obj) (seq obj)))
    Object
      (toString [_] (str obj))))

(defn render [template feature]
  (clostache/render template (lookup-proxy feature)))

(defn render-resource [path feature]
  (clostache/render-resource path (lookup-proxy feature)))

;(with-open [s (file-stream ".test-files/new-features-single-collection-100000.json")] (time (last (features-from-package-stream s))))