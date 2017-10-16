(ns tyylikas.core
  (:require [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [clojure.java.io :as io]
            [rewrite-clj.zip :as z]
            [rewrite-clj.node.protocols :as np]))

(defmulti rule (fn [type _ _ _] type))

(def ^:dynamic *errors* nil)

(defn add-error [m]
  (assert (keyword? (:error m)))
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
           (add-error {:error type
                       :message "Missing whitespace between form elements"
                       :this (-> zloc z/right)})
           (if (:fix opts)
             (-> zloc (zip/insert-right (n/spaces 1)))
             zloc))))

(def all-rules
  {:whitespace-between-nodes true})

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

(defn format-error [{:keys [line position message]}]
  (format "%s, line %d, column %s:\n%s\n%s^"
          message
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
  (let [results (check-files fs opts)]
    (println (format "Found %d problems:\n" (count (mapcat :errors results))))
    (doseq [{:keys [file errors node]} results
            :when (seq errors)]
      (println (str (.getPath file) ":\n"))
      (doseq [error errors]
        (println (format-error (attach-location error)))
        (println))
      (when (:fix opts)
        (spit file (n/string node))))))

(comment
  (report (find-files "src" "test") {:rules all-rules :fix true})
  )
