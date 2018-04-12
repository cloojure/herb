(ns examples.core
  (:require [herb.core :refer [<class]]))

(defn colors
  []
  {:color "red"
   :background-color "cyan"})

(defn test-class
  []
  (<class colors))

(defn test-anon
  []
  (<class (fn [] {:color "red"})))