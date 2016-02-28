(ns cprop.test.core
  (:require [cprop.core :refer [load-config cursor]]
            [cprop.source :refer [from-stream from-file from-resource]]
            [clojure.edn :as edn]
            [clojure.test :refer :all]))

(deftest should-slurp-and-provide
  (testing "should read config from -Dconfig.var"
    (let [c (load-config)]
      (is (= (c :answer) 42))))
  (testing "should be able to naviage nested props"
    (let [c (load-config)]
      (is (= (get-in c [:source :account :rabbit :vhost]) "/z-broker")))))

(deftest should-create-cursors
  (testing "should create a rabbit cursor"
    (let [c (load-config)]
      (is (= ((cursor c :source :account :rabbit) :vhost) "/z-broker"))
      (is (= ((cursor c)) c)))))

(deftest should-compose-cursors
  (testing "should compose one level"
    (let [c (load-config)]
      (is (= ((cursor c (cursor c :source) :account) :rabbit :vhost) "/z-broker"))
      (is (= ((cursor c (cursor c) :source :account) :rabbit :vhost) "/z-broker"))))
  (testing "should compose nested cursors"
    (let [c (load-config)]
      (is (= ((cursor c (cursor c (cursor c :source) :account) :rabbit) :vhost) "/z-broker")))))

(defn- read-system-env []
  (->> {"DATOMIC_URL" "\"datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic\""
        "AWS_ACCESS__KEY" "\"AKIAIOSFODNN7EXAMPLE\""
        "AWS_SECRET__KEY" "\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\""
        "AWS_REGION" "\"ues-east-1\""
        "IO_HTTP_POOL_CONN__TIMEOUT" "60000"
        "IO_HTTP_POOL_MAX__PER__ROUTE" "10"
        "OTHER__THINGS" "[1 2 3 \"42\"]"}
       (map (fn [[k v]] [(#'cprop.core/env->path k) v]))
       (into {})))

(deftest from-source
  (is (map? (from-stream "test/resources/config.edn")))
  (is (map? (from-file "test/resources/config.edn")))
  (is (map? (from-resource "config.edn")))
  (is (map? (load-config :file "test/resources/config.edn")))
  (is (map? (load-config :resource "config.edn")))
  (is (map? (load-config :resource "config.edn"
                         :file "test/resources/fill-me-in.edn"))))

(deftest with-merge
  (is (= (load-config :resource "config.edn" 
                      :merge [{:source {:account {:rabbit {:port 4242}}}}])
         (assoc-in (load-config) [:source :account :rabbit :port] 4242)))
  (is (= (load-config :file "test/resources/config.edn" 
                      :merge [{:source {:account {:rabbit {:port 4242}}}}
                              {:datomic {:url :foo}}])
         (assoc-in (assoc-in (load-config) [:source :account :rabbit :port] 4242)
                   [:datomic :url] :foo)))
  (is (= (load-config :resource "config.edn"
                      :file "test/resources/config.edn"
                      :merge [{:source {:account {:rabbit {:port 4242}}}}
                              {:datomic {:url :foo}}
                              {:datomic {:url :none}}])
         (assoc-in (assoc-in (load-config) [:source :account :rabbit :port] 4242)
                   [:datomic :url] :none))))

(deftest should-merge-with-env
  (let [config (load-config :file "test/resources/fill-me-in.edn" 
                            :resource "fill-me-in.edn")]
    (is (= {:datomic {:url "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"},
            :aws {:access-key "AKIAIOSFODNN7EXAMPLE",
                  :secret-key "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                  :region "ues-east-1",
                  :visiblity-timeout-sec 30,
                  :max-conn 50,
                  :queue "cprop-dev"},
            :io
            {:http
             {:pool
              {:socket-timeout 600000,
               :conn-timeout 60000,
               :conn-req-timeout 600000,
               :max-total 200,
               :max-per-route 10}}},
            :other-things [1 2 3 "42"]}
           (#'cprop.core/merge* config (read-system-env))))))
