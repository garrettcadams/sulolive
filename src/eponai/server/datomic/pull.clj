(ns eponai.server.datomic.pull
  (:require
    [clojure.walk :refer [walk]]
    [datomic.api :as d]
    [eponai.common.database.pull :as p]
    [eponai.common.parser.util :as parser]
    [eponai.common.report :as report]
    [taoensso.timbre :refer [debug warn]]))

(defn currencies [db]
  (p/q '[:find [(pull ?e [*]) ...]
       :where [?e :currency/code]] db))

(defn schema
  "Pulls schema from the db. If data is provided includes only the necessary fields for that data.
  (type/ref, cardinality/many or unique/identity)."
  ([db] (schema db nil))
  ([db db-since]
   (let [query (cond-> {:where '[[$ ?e :db/ident ?id]
                                 [$ :db.part/db :db.install/attribute ?e]
                                 [(namespace ?id) ?ns]
                                 [(.startsWith ^String ?ns "db") ?d]
                                 [(not ?d)]]}
                       (some? db-since)
                       (p/merge-query {:where '[[$since ?e]]
                                       :symbols {'$since db-since}}))]
     (mapv #(into {} (d/entity db %))
           (p/all-with db query)))))

(defn widget-report-query []
  (parser/put-db-id-in-query
    '[:widget/uuid
     :widget/width
     :widget/height
     {:widget/filter [{:filter/include-tags [:tag/name]}]}
     {:widget/report [:report/uuid
                      {:report/track [{:track/functions [*]}
                                      {:track/filter [{:filter/include-tags [:tag/name]}]}]}
                      {:report/goal [*
                                     {:goal/cycle [*]}]}
                      :report/title]}
     :widget/index
     :widget/data
     {:widget/graph [:graph/style]}]))

(defn transaction-query []
  (parser/put-db-id-in-query
    [:transaction/uuid
     :transaction/amount
     :transaction/conversion
     {:transaction/currency [:currency/code]}
     {:transaction/tags [:tag/name]}
     {:transaction/date [:date/ymd
                         :date/timestamp]}]))

(defn any-transaction-in-project-since-last-read [db db-since project-eid]
  (p/one-since db db-since {:where   '[[?e :transaction/project ?project]
                                       [?project :project/uuid]
                                       [(= ?project ?project-eid)]]
                            :symbols {'?project-eid project-eid}}))

(defn widgets-with-data [{:keys [db auth] :as env} project-eid widgets]
  (->> widgets
       (mapv (fn [{:keys [widget/uuid]}]
               (let [widget (p/pull db (widget-report-query) [:widget/uuid uuid])
                     project (p/pull db [:project/uuid] project-eid)
                     transactions (p/transactions-with-conversions
                                    (assoc env :query (transaction-query))
                                    (:username auth)
                                    {:filter       (:widget/filter widget)
                                     :project-uuid (:project/uuid project)})
                     report-data (report/generate-data (:widget/report widget) (get-in widget [:widget/graph :graph/filter]) transactions)]
                 (assoc widget :widget/data report-data))))))

(defn new-currencies [db rates]
  (let [currency-codes (mapv #(get-in % [:conversion/currency :currency/code]) rates)
        currencies (p/pull-many db [:currency/code] (p/all-with db {:where   '[[?e :currency/code ?code]]
                                                                    :symbols {'[?code ...] currency-codes}}))
        db-currency-codes (map :currency/code currencies)
        new-currencies (clojure.set/difference (set currency-codes) (set db-currency-codes))]
    (debug "Found new currencies: " new-currencies)
    new-currencies))

(defn k->sym [k]
  (gensym (str "?" (name k) "_")))

(defn pull-pattern->where-clauses [pattern sym]
  (cond
    (keyword? pattern)
    (if (= pattern :db/id)
      []
      [[sym pattern (k->sym pattern)]])

    (map? pattern)
    (let [[k v] (first pattern)]
      (concat (pull-pattern->where-clauses k sym)
              (pull-pattern->where-clauses v (k->sym k))))

    (sequential? pattern)
    ;; Handles the case with default and limit. (they can only take keywords).
    (when-not (symbol? (first pattern))
      (seq (filter some? (mapcat #(pull-pattern->where-clauses % sym) pattern))))

    :else (do (warn "Pattern not matched: " pattern)
              nil)))

(defn pull-pattern->paths
  ([pattern] (pull-pattern->paths pattern []))
  ([pattern prev]
   (cond
     (keyword? pattern)
     (when (not= pattern :db/id)
       [(conj prev pattern)])

     (map? pattern)
     (let [[k v] (first pattern)]
       (recur v (conj prev k)))

     (sequential? pattern)
     (if (symbol? (first pattern))
       (recur (second pattern) prev)
       (sequence (comp (mapcat #(pull-pattern->paths % prev))
                       (filter some?))
                 pattern))
     :else nil)))

(defn path->query [[p :as path] sym]
  (let [next-sym (gensym (str "?" (name p) "_"))
        query (cond-> {:where (if (= \_ (first (name p)))
                                (let [k (keyword (namespace p) (subs (name p) 1))]
                                  [[next-sym k sym]])
                                [[sym p next-sym]])}
                      (seq (rest path))
                      (p/merge-query (path->query (rest path) next-sym)))]
    {:query query :last-symbol sym}))

(defn match-path [db db-since path entity-query]
  (loop [datoms (d/seek-datoms db-since :aevt (last path))]
    (when (seq datoms)
      (let [{:keys [query last-symbol]} (path->query path '?e)]
        (debug "query: " query "path: " path)
        (p/one-with db (-> entity-query
                           (p/merge-query query)
                           (p/merge-query {:symbols {'[last-symbol ...] (mapv #(.e %) datoms)}})))))))

(comment (if (empty? attrs)
           (when (seq datoms)
             (debug "datoms: " datoms "path: " rpath "targets: " targets)
             (some (set targets) (sequence (comp seen (map #(.e %))) datoms)))
           (recur (sequence (comp seen
                                  (map #(.v %))
                                  (mapcat #(d/seek-datoms db :eavt % (first attrs))))
                            datoms)
                  (rest attrs))))

(defn paths->matches [db db-since pull-pattern entity-query]
  (let [paths (sequence (filter #(> (count %) 1)) (pull-pattern->paths pull-pattern))
        any-matches? (some (fn [path] (match-path db db-since path entity-query)) paths)]
    (debug "pull-pattern: " pull-pattern "entity-query: " entity-query "any-matches?: " any-matches?)
    (if any-matches?
      (p/pull-many db pull-pattern (p/all-with db entity-query))
      ;; Can we do even better here? pull with db-since?
      (p/pull-many db-since pull-pattern (p/all-with db (p/with-db-since entity-query db-since))))))

(defn clauses->with-missing-checks [clauses]
  (let [missing-clauses (reduce (fn [m [sym k]]
                                  ;; k is nil for the :db/id case.
                                  (if (nil? k)
                                    m
                                    (let [ret (gensym (str "?" (name k) "-in-since?" "_"))
                                          clause [(list 'missing? '$since sym k) ret]]
                                      (-> m
                                          (update :clauses conj clause)
                                          (update :symbols conj ret)))))
                                {:symbols []
                                 :clauses []}
                                clauses)
        or-not-clauses (when (seq (:symbols missing-clauses))
                         `(~'or-join ~(:symbols missing-clauses)
                            ~@(map (fn [miss?] [(list 'not miss?)])
                                   (:symbols missing-clauses))))]
    (cond-> (into (vec clauses) (:clauses missing-clauses))
            or-not-clauses
            (conj or-not-clauses))))

(defn pattern->query-map [pull-pattern]
  (let [clauses (into [] (comp (map #(pull-pattern->where-clauses % '?e))
                               (map #(clauses->with-missing-checks %))
                               (map #(cons 'and %)))
                      pull-pattern)
        or-join `(~'or-join [~'?e] ~@clauses)]
    {:where [or-join]}))

(defn- include-changed-children [db-since pull-pattern]
  (assoc (pattern->query-map pull-pattern)
    :symbols {'$since db-since}))

(defn pull-all-since [db db-since pull-pattern entity-query]
  (->> (p/merge-query entity-query
                      (include-changed-children db-since pull-pattern))
       ;;(#(do (debug "QUERY: " %) %))
       (p/all-with db)
       (p/pull-many db pull-pattern)))
