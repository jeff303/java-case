(ns us.jeffevans.java-case-test
  (:require [clojure.test :refer :all]
            [us.jeffevans.java-case :as java-case]))

;; white box tests
(deftest version-detecting-test
  (testing "sanity check"
    (is (= (var-get #'java-case/known-java-spec-versions) (java-case/java-spec-versions))))
  (testing "future proof checks"
    (testing "java.specification.version JVM parameter at runtime is higher than we know about"
      (binding [java-case/*java-spec-version-override* "20"]
        (is (= (vec (concat (var-get #'java-case/known-java-spec-versions) ["18" "19" "20"]))
               (java-case/java-spec-versions)))))
    (testing "one of the input's integer-like range boundaries is higher than we know about"
      (is (= (vec (concat (var-get #'java-case/known-java-spec-versions) (map str (range 18 43))))
             (java-case/java-spec-versions (#'java-case/inputs->range-boundaries ["11+" "24+" "42"])))))))

(deftest input-handling-test
  (doseq [[inputs exp-boundaries exp-ranges] [[["1.0"]
                                               {"1.0" [false 0]}
                                               {0 ["1.0"]}]
                                              [["1.0+"]
                                               {"1.0" [true 0]}
                                               {0 (java-case/java-spec-versions)}]
                                              [["1.8" "1.0+"]
                                               {"1.0" [true 1]
                                                "1.8" [false 0]}
                                               {0 ["1.8"]
                                                1 ["1.0" "1.1" "1.2" "1.3" "1.4" "1.5" "1.6" "1.7"]}]
                                              [["9+" "1.7+" "16"]
                                               {"1.7" [true 1]
                                                "9"   [true 0]
                                                "16"  [false 2]}
                                               {0 ["9" "10" "11" "12" "13" "14" "15"]
                                                1 ["1.7" "1.8"]
                                                2 ["16"]}]]]
    (testing inputs
      (testing "inputs->range-boundaries works correctly"
        (is (= exp-boundaries (#'java-case/inputs->range-boundaries inputs))))
      (testing "inputs->version-ranges works correctly"
        (is (= exp-ranges (#'java-case/inputs->version-ranges inputs)))))))

;; macro tests
(deftest java-case-test
  (testing "java-case macro works as expected"
    (doseq [[expected version] [[1 "1.7"]
                                [1 "1.8"]
                                [2 "1.6"]
                                [3 "11"]
                                [4 "1.5"]]]
      (is (= expected (binding [java-case/*java-spec-version-override* version]
                        (java-case/java-case
                          11     3
                          "1.7+" 1
                          1.6    2
                          4)))))))
