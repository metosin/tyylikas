(ns tyylikas.core-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [tyylikas.core :refer :all]
            [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z]))

(deftest validate-test
  (let [node (p/parse-string "(ok\n  (foo(bar)))")]
    (is (= [{:type :whitespace-between-nodes
             :this (-> node z/edn z/down z/right z/down z/right)}]
           (:errors (validate node {:rules all-rules}))))

    (is (= "(ok\n  (foo (bar)))"
           (n/string (:node (validate node {:rules all-rules :fix true}))))))

  (let [node (p/parse-string-all "(foo\n  1 2)\n(bar)")]
    (is (= [{:type :line-break-between-top-level-forms
             :this (-> node z/edn z/right)}]
           (:errors (validate node {:rules all-rules}))))

    (is (= "(foo\n  1 2)\n\n(bar)"
           (n/string (:node (validate node {:rules all-rules :fix true})))))) )

(deftest attach-location-test
  (let [node (p/parse-string "(ok\n  (foo(bar)))")
        this (-> node z/edn z/down z/right z/down z/right)]
    (is (= {:this this
            :position [1 7]
            :line "  (foo(bar)))"}
           (attach-location {:this this})))))

(deftest format-error-test
  (is (= "Missing whitespace between form elements, line 1, column 7:
  (foo(bar)))
      ^"
         (format-error {:type :whitespace-between-nodes
                        :position [1 7]
                        :line "  (foo(bar)))"}))))
