(ns test-namespace)

(missing-whitespace(foo(bar)))
(bad-whitespace (   foo ( bar ))   )

(defn top-level1 [a]
  a)
(defn top-level2 [b]
  b)

(defn top-level3 [b] b)
(defn top-level4 [b] b)

(js/console.log "foo")
(.log js/console "foo")
(println "foo")
