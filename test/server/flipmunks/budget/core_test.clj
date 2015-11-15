(ns flipmunks.budget.core-test
  (:require [clojure.test :refer :all]
            [datomic.api :only [q db] :as d]
            [flipmunks.budget.core :as b]
            [flipmunks.budget.datomic.pull :as p]
            [flipmunks.budget.datomic.transact :as t]
            [flipmunks.budget.auth :as a]
            [flipmunks.budget.openexchangerates :as exch])
  (:import (clojure.lang ExceptionInfo)))

(def schema (read-string (slurp "resources/private/datomic-schema.edn")))

(def test-data [{:transaction/name       "coffee"
                 :transaction/uuid       (str (d/squuid))
                 :transaction/created-at 12345
                 :transaction/date       "2015-10-10"
                 :transaction/amount     100
                 :transaction/currency   "SEK"
                 :transaction/tags       ["fika" "thailand"]}])

(def test-curs {:SEK "Swedish Krona"})

(defn test-convs [date-str]
  {:date  date-str
   :rates {:SEK 8.333}})

(def user-params {:username "user@email.com"
                  :password "password"})

(def request {:session {:cemerick.friend/identity
                        {:authentications
                                  {1
                                   {:identity 1,
                                    :username (user-params :username),
                                    :roles    #{::b/user}}},
                         :current 1}}
              :body test-data})

(defn test-send-email [email uuid]
  (println "Email sent to \"" email "\" with link " uuid))

(def init (b/init test-convs test-send-email))

(defn- new-db
  "Creates an empty database and returns the connection."
  []
  (let [uri "datomic:mem://test-db"]
    (d/delete-database uri)
    (d/create-database uri)
    (let [conn (d/connect uri)]
      (d/transact conn schema)
      (t/new-user conn (a/new-signup (assoc request :request-method :post
                                                    :params user-params)))
      conn)))

(defn- db-with-curs []
  (let [conn (new-db)]
    (b/post-currencies conn test-curs)
    conn))

(deftest test-post-currency-rates
  (testing "posting currency rates to database."
    (let [conn (db-with-curs)
          dates ["2010-10-10"]
          db (b/post-currency-rates conn
                                    test-convs
                                    dates)
          unposted (b/post-currency-rates conn
                                          test-convs
                                          dates)
          converted (p/converted-dates (:db-after db) dates)]
      (is db)
      (is (nil? unposted))
      (is (= (count converted) (count dates)))
      (is (thrown? ExceptionInfo (b/post-currency-rates conn test-convs ["invalid-date"]))))))

(deftest test-post-user-data
  (let [db (b/post-user-data (db-with-curs)
                             request)
        result (b/fetch p/all-data (:db-after db)
                        "user@email.com"
                        {})
        no-result (b/fetch p/all-data (:db-after db)
                           "user@email.com"
                           {:d "1"})]
    (is (every? #(empty? (val %)) no-result))
    (is (= (count (:schema result)) 11))
    (is (= (count (:entities result)) 7))
    (is (thrown-with-msg? ExceptionInfo
                          #"Invalid budget id"
                          (b/fetch p/all-data (:db-after db)
                                   "invalid@email.com"
                                   {})))))

(deftest test-post-invalid-user-data
  (let [invalid-user-req (assoc-in request
                                   [:session :cemerick.friend/identity :authentications 1 :username]
                                   "invalid@user.com")
        invalid-data-req (assoc request :body [{:invalid-data "invalid"}])
        post-fn #(b/post-user-data (db-with-curs)
                                   %)]
    (is (thrown? ExceptionInfo (post-fn invalid-user-req)))
    (is (thrown? ExceptionInfo (post-fn invalid-data-req)))))

(deftest test-post-invalid-currencies 
  (testing "Posting invalid currency data."
    (is (thrown? ExceptionInfo (b/post-currencies (new-db)
                                                  (assoc test-curs :invalid 2))))))

(deftest test-signup
  (testing "signup new user"
    (let [valid-params {:username "test"
                        :password "p"}
          conn (new-db)
          db-unverified (b/signup conn {:request-method :post
                                        :params         valid-params})
          db-verified (t/new-verification conn
                                          (p/user (:db-after db-unverified) "test")
                                          :user/email
                                          :verification.status/verified)]
      (is (a/cred-fn #(b/user-creds (:db-after db-verified) %) valid-params))
      (is (thrown-with-msg? ExceptionInfo
                            #"Email verification pending."
                            (a/cred-fn #(b/user-creds (:db-after db-unverified) %) valid-params)))
      (is (thrown-with-msg? ExceptionInfo
                            #"Validation failed, "
                            (b/signup (new-db)
                                      {:request-method :post
                                       :params         {:username ""
                                                        :password ""}})))
      (is (thrown-with-msg? ExceptionInfo
                            #"Invalid request method"
                            (b/signup (new-db) {:request-method :get
                                                :params         valid-params}))))))