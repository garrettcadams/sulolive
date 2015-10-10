(ns flipmunks.budget.datomic_mem
  (:require [flipmunks.budget.core :as core]
            [flipmunks.budget.datomic.core :as budget.d]
            [clojure.tools.reader.edn :as edn]
            [clojure.java.io :as io]
            [datomic.api :as d]))

(def schema-file (io/file (io/resource "private/datomic-schema.edn")))
(def currencies [{:db/id #db/id [:db.part/user -1],
                  :db/ident :currency/THB,
                  :currency/name "Thai Baht",
                  :currency/code "THB"}])
(def transactions [{:uuid (str (java.util.UUID/randomUUID))
                     :name "lunch"
                     :date "2015-10-10"
                     :amount 180
                     :currency "THB"}
                    {:uuid (str (java.util.UUID/randomUUID))
                     :name "coffee"
                     :date "2015-10-10"
                     :amount 140
                     :currency "THB"}
                    {:uuid (str (java.util.UUID/randomUUID))
                     :name "dinner"
                     :date "2015-10-10"
                     :amount 350
                     :currency "THB"}
                    {:uuid (str (java.util.UUID/randomUUID))
                     :name "market"
                     :date "2015-10-11"
                     :amount 789
                     :currency "THB"}
                    {:uuid (str (java.util.UUID/randomUUID))
                     :name "lunch"
                     :date "2015-10-11"
                     :amount 125
                     :currency "THB"}])

(def app
  (do 
    ;; set the core/conn var
    (alter-var-root #'budget.d/conn
                    (fn [old-val] 
                      (let [uri "datomic:mem://test-db"]
                        (if (d/create-database uri)
                          (d/connect uri)
                          (throw (Exception. "Could not create datomic db with uri: " uri))))))
    (let [schema (->> schema-file slurp (edn/read-string {:readers *data-readers*}))
          conn budget.d/conn]
      (d/transact conn schema)
      (d/transact conn currencies)
      (doseq [t transactions]
        (budget.d/post-user-tx t)))
    ;; reutrn the core/app ring handler
    core/app))

