(ns tyylikas.core
  (:require [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [clojure.java.io :as io]
            [rewrite-clj.zip :as z]
            [rewrite-clj.node.protocols :as np]))

(defmulti rule (fn [type _ _ _] type))
(defmulti message identity)

(def ^:dynamic *errors* nil)

(defn add-error [m]
  (assert (keyword? (:type m)))
  (assert (:this m))
  (swap! *errors* conj m))

(defn- element? [zloc]
  (and zloc (not (z/whitespace-or-comment? zloc))))

(defn- reader-macro? [zloc]
  (and zloc (= (n/tag (z/node zloc)) :reader-macro)))

(defn- check-all [zloc p? f]
  (loop [zloc (if (p? zloc) (f zloc) zloc)]
    (if-let [zloc (z/find-next zloc zip/next p?)]
      (recur (f zloc))
      zloc)))

(defn- check [form zf & args]
  (z/root (apply zf (z/edn form) args)))

;;
;; Missing whitespace between form elements
;; Find elements where next element is also element (i.e. next element is not whitespace)
;;

(defn- missing-whitespace? [zloc]
  (and (element? zloc)
       (not (reader-macro? (zip/up zloc)))
       (element? (zip/right zloc))))

(defmethod rule :whitespace-between-nodes [type form rule-opts opts]
  (check form check-all missing-whitespace?
         (fn [zloc]
           (add-error {:type type
                       :this (-> zloc z/right)})
           (if (:fix opts)
             (-> zloc (zip/insert-right (n/spaces 1)))
             zloc))))

(defmethod message :whitespace-between-nodes [_]
  "Missing whitespace between form elements")

;;
;; Bad whitespace in start and end of list
;;

(defn- start-of-list? [zloc]
  (not (zip/left zloc)))

(defn- bad-whitespace? [zloc]
  (and (z/list? zloc)
       (or (z/whitespace? (zip/down zloc))
           (z/whitespace? (zip/rightmost (zip/down zloc))))))

(defmethod rule :bad-whitespace-start-end-of-list [type form rule-opts opts]
  (check form check-all bad-whitespace?
         (fn [zloc]
           (when (z/whitespace? (zip/down zloc))
             (add-error {:type type
                         :this (zip/down zloc)}))

           (when (z/whitespace? (zip/rightmost (zip/down zloc)))
             (add-error {:type type
                         :this (zip/rightmost (zip/down zloc))}))
           (if (:fix opts)
             (cond-> zloc
                (z/whitespace? (zip/down zloc))
                (-> zip/down z/remove zip/up)

                (z/whitespace? (zip/rightmost (zip/down zloc)))
                (-> zip/down zip/rightmost z/remove zip/up))
             zloc))))

(defmethod message :bad-whitespace-start-end-of-list [_]
  "Bad whitespace on start or end of a list")

;;
;; Missing newlines between multi-line toplevel forms
;;

(defn- check-childs [zloc p? f]
  (loop [zloc (if (p? zloc) (f zloc) zloc)]
    (if-let [zloc (z/find-next zloc zip/right p?)]
      (recur (f zloc))
      zloc)))

(defn multi-line? [zloc]
  (seq (rest (str/split-lines (z/string zloc)))))

(defn- missing-line-break? [zloc]
  (and (element? zloc)
       ;; Separated by by less than 2 line-break?
       (z/linebreak? (zip/right zloc))
       (< (z/length (zip/right zloc)) 2)
       (element? (zip/right (zip/right zloc)))
       (or (multi-line? zloc)
           (multi-line? (zip/right (zip/right zloc))))))

(defmethod rule :line-break-between-top-level-forms [type form rule-opts opts]
  (check form check-childs missing-line-break?
         (fn [zloc]
           (add-error {:type type
                       :this (-> zloc z/right)})
           (if (:fix opts)
             (-> zloc (zip/insert-right (n/newlines 1)))
             zloc))))

(defmethod message :line-break-between-top-level-forms [_]
   "Missing line break between toplevel forms")

(def all-rules
  {:whitespace-between-nodes true
   :line-break-between-top-level-forms true
   :bad-whitespace-start-end-of-list true})

(defn validate
  [node opts]
  (binding [*errors* (atom [])]
    (let [node
          (reduce
            (fn [node [k v]]
              (if v
                (rule k node v opts)
                node))
            node
            (:rules opts))]
      {:errors @*errors*
       :node node})))

(defn attach-location [{:keys [this] :as error}]
  (let [{:keys [row col]} (meta (z/node this))]
    (assoc error
           :line (get (str/split-lines (n/string (z/root this))) (dec row))
           :position [(dec row) col])))

(defn format-error [{:keys [line position type]}]
  (format "%s, line %d, column %s:\n%s\n%s^"
          (message type)
          (first position)
          (second position)
          line
          (apply str (repeat (dec (second position)) " "))))

(defn check-file [f opts]
  (assoc (validate (p/parse-file-all f) opts) :file f))

(defn check-files [fs opts]
  (doall (map #(check-file % opts) fs)))

(defn find-files [& files]
  (mapcat (fn [file]
            (filter #(and (.isFile %)
                          (or (.endsWith (.getName %) ".clj")
                              (.endsWith (.getName %) ".cljc")
                              (.endsWith (.getName %) ".cljs")))
                    (file-seq (io/file file))))
          files))

(defn report [fs opts]
  (let [results (sort-by :file (check-files fs opts))]
    (println (format "Found %d problems:\n" (count (mapcat :errors results))))
    (doseq [{:keys [file errors node]} results
            :when (seq errors)
            :let [errors (sort-by :position (map attach-location errors))]]
      (println (str (.getPath file) ":\n"))
      (doseq [error errors]
        (println (format-error error))
        (println))
      (when (:fix opts)
        (spit file (n/string node))))))

(comment
  (report (find-files "src" "test") {:rules all-rules})
  (report (find-files "test-resources") {:rules all-rules})
  (report (find-files "test-resources") {:rules all-rules :fix true})
  )
