(ns pdok.featured.persistence
  (:require [pdok.featured.cache :refer :all]
            [clojure.java.jdbc :as j]
            [clj-time [coerce :as tc]]))

;; DROP TABLE IF EXISTS featured.feature;

;; CREATE TABLE featured.feature
;; (
;;   id bigserial NOT NULL,
;;   dataset character varying(100) NOT NULL,
;;   collection character varying(255) NOT NULL,
;;   feature_id character varying(50) NOT NULL,
;;   validity timestamp without time zone NOT NULL,
;;   CONSTRAINT feature_pkey PRIMARY KEY (id)
;; )
;; WITH (
;;   OIDS=FALSE
;; );
;; ALTER TABLE featured.feature
;;   OWNER TO postgres;

;; CREATE INDEX feature_index
;;   ON featured.feature
;;   USING btree
;;   (dataset, collection, id);


(defn- jdbc-insert
  ([db dataset collection id validity]
   (jdbc-insert db (list [dataset collection id validity])))
  ([db entries]
   (j/with-db-connection [c db]
     (letfn [( transform [entry]
               (let [[dataset collection id validity] entry]
                 [dataset collection id (tc/to-timestamp validity)]))]
       (let [records (map transform entries)]
         (apply
          (partial j/insert! c :featured.feature [:dataset :collection :feature_id :validity])
          records)
         )))))

(defn- jdbc-load-cache [db dataset collection]
  (let [results
        (j/with-db-connection [c db]
          (j/query c ["SELECT dataset, collection, feature_id, max(validity) as cur_val FROM featured.feature
WHERE dataset = ? AND collection = ?
GROUP BY dataset, collection, feature_id"
                      dataset collection]))]
    (map (fn [f] [[(:dataset f) (:collection f) (:feature_id f)] (:cur_val f)]) results)
    )
  )

(defn- jdbc-stream-validity [db dataset collection id]
   (j/with-db-connection [c db]
     (let [results
           (j/query c ["SELECT max(validity) FROM featured.feature
                        WHERE dataset = ? AND collection = ? AND feature_id = ?"
                       dataset collection id])]
       (-> results first :max))))

(defn processor-jdbc-persistence
  ([config]
   (let [db (:db-config config)
         create-stream (fn [dataset collection id])
         ; compose with nil, because insert returns record. Should fix this...
         append-to-stream (comp (fn [_] nil) (partial jdbc-insert db) )
         current-validity (partial jdbc-stream-validity db)
         stream-exists? (fn [dataset collection id] (not (nil? (current-validity dataset collection id))))]
     {:init #()
      :create-stream create-stream
      :stream-exists? stream-exists?
      :append-to-stream append-to-stream
      :current-validity current-validity
      :shutdown #() })))

(defn processor-cached-jdbc-persistence [config]
  (let [db (:db-config config)
        standard (processor-jdbc-persistence config)
        key-fn #(take 3 %)
        value-fn #(drop 3 %)]
    (cached
     (let [batched-append-to-stream (with-batch (:append-to-stream standard))
           cached-append-to-stream (with-cache batched-append-to-stream key-fn value-fn)
           ; sort of hacky? To prevent executing every time, memoize the function
           memoized-load-cache (memoize jdbc-load-cache)
           load-cache (fn [dataset collection id] (memoized-load-cache db dataset collection))
           cached-current-validity (use-cache (:current-validity standard) key-fn load-cache)
           stream-exists? (fn [dataset collection id] (not (nil? (cached-current-validity dataset collection id))))
           shutdown (partial flush-batch (:append-to-stream standard))]
       {:init #()
        :create-stream (fn [dataset collection id])
        :stream-exists? stream-exists?
        :append-to-stream cached-append-to-stream
        :current-validity cached-current-validity
        :shutdown shutdown}))))

;; (def ^:private pgdb {:subprotocol "postgresql"
;;                      :subname "//localhost:5432/pdok"
;;                      :user "postgres"
;;                      :password "postgres"})

;(def pers ( processor-jdbc-persistence {:db-config pgdb}))
;((:append-to-stream pers) "set" "col" "1" (tl/local-now))
;((:shutdown pers))
