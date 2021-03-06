(ns clojure.core.logic.match
  (:refer-clojure :exclude [reify == inc])
  (:use clojure.core.logic.minikanren)
  (:require [clojure.set :as set])
  (:import [clojure.core.logic.minikanren Substitutions]))

(defn warn [& msg]
  (binding [*out* *err*]
    (apply println "WARNING:" msg)))

(declare p->term)

(defn lcons-p? [p]
  (and (coll? p)
       (not (nil? (some '#{.} p)))))

(defn p->llist [p]
  `(llist
    ~@(map p->term
           (remove #(contains? '#{.} %) p))))

(defn p->term [p]
  (cond
   (= p '_) `(lvar)
   (lcons-p? p) (p->llist p)
   (and (coll? p)
        (not= (first p) 'quote)) (cond
                                  (list? p) p
                                  :else `[~@(map p->term p)])
   :else p))

(defn lvar-sym? [s]
  (= (first (str s)) \?))

(defn extract-vars
  ([p]
     (set (cond
           (lvar-sym? p) [p]
           (coll? p) (filter lvar-sym? (flatten p))
           :else nil)))
  ([p seen]
     (set/difference (extract-vars p) (set seen))))

(defn fresh-expr? [cs]
  (= (first cs) `fresh))

(defn ex
  ([vs t a]
     `(fresh [~@vs]
        (== ~t ~a)))
  ([vs t a exprs]
     (if (fresh-expr? exprs)
       `(fresh [~@vs]
          (== ~t ~a)
          ~exprs)
       `(fresh [~@vs]
          (== ~t ~a)
          ~@exprs))))

(defn ex* [[[p a :as pa] & par] exprs seen]
  (let [t (p->term p)
        vs (extract-vars p seen)
        seen (reduce conj seen vs)]
    (cond
     (nil? pa) exprs
     (= p '_) (ex* par exprs seen)
     (empty? par) (if exprs
                    (ex vs t a exprs)
                    (ex vs t a))
     :else (let [r (ex* par exprs seen)]
             (if r
               (ex vs t a r)
               (ex vs t a))))))

(defn all-blank? [p]
  (every? #(= % '_) p))

(defn handle-clause [as]
  (when-not (vector? as)
    (throw (Exception. (str "Expecting vector of arguments, instead " as))))
  (fn [[p & exprs]]
    (when-not (vector? p)
      (throw (Exception. (str "Expecting vector of matches, instead " p))))
    (when-not (= (count p) (count as))
      (warn "Differing number of matches. Matching" p "against" as))
    (let [pas (partition 2 (interleave p as))
          r (ex* pas exprs #{})]
      (if (all-blank? p)
        r
        (list r)))))

(defn handle-clauses [t as cs]
  `(~t
    ~@(map (handle-clause as) cs)))

(defn defnm [t n as & cs]
  (if-let [tabled? (-> n meta :tabled)]
    `(def ~n
          (clojure.core.logic.tabled/tabled [~@as]
                               ~(handle-clauses t as cs)))
    `(defn ~n [~@as]
       ~(handle-clauses t as cs))))
