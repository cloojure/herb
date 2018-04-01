(ns herb.core
  (:require
   [garden.core :refer [css]]
   [clojure.string :as str]
   [garden.selectors :as s]
   [debux.cs.core :as d :refer-macros [clog clogn dbg dbgn break]]
   [garden.stylesheet :refer [at-media at-keyframes]]
   [herb.runtime :as runtime])
  (:require-macros [herb.core :refer [with-style]]))

(def dev? ^boolean js/goog.DEBUG)

(defn set-global-style!
  "Takes a collection of Garden style vectors, and create or update the global style element"
  [& styles]
  (let [element (.querySelector js/document "style[data-herb=\"global\"]")
        head (.-head js/document)
        css-str (css styles)]
    (assert (some? head) "An head element is required in the dom to inject the style.")
    (if element
      (set! (.-innerHTML element) css-str)
      (let [element (.createElement js/document "style")]
        (set! (.-innerHTML element) css-str)
        (.setAttribute element "type" "text/css")
        (.setAttribute element "data-herb" "global")
        (.appendChild head element)))))

(defn join-classes
  [& classes]
  (->> classes
      (filter identity)
      (str/join " ")))

(defn- convert-modes
  [modes]
  (map
   (fn [[kw mode]]
     [(keyword (str "&" kw)) mode])
   modes))

(defn- convert-media
  [media]
  (map (fn [[query style]]
         (at-media query [:& style]))
       media))

(defn- resolve-style-fns
  "Calls each function provided in a collection of style-fns. Input can take
  multiple forms depending on how it got called from the consumer side either
  using the macro directly or via extend meta data.
  Takes a collection of `style-fns` and a collection `result` that is returned
  with the resolved style maps."
  [style-fns result]
  (if (empty? style-fns)
    result
    (let [input (first style-fns)]
      (cond
        (fn? input)
        (conj result (apply input (rest style-fns)))

        (and (coll? input) (fn? (first input)))
        (let [style-fn (first input)
              style-args (rest input)]
          (recur
           (rest style-fns)
           (conj result (apply style-fn style-args))))
        :else (recur
               (rest style-fns)
               (into result (resolve-style-fns input [])))))))

(defn- process-meta-xform
  "Return a transducer that pulls out a given meta type from a sequence and filter
  out nil values"
  [meta-type]
  (comp
   (map meta)
   (map meta-type)
   (filter identity)))

(defn- extract-styles
  "Extract all the `:extend` meta, ensuring what we walk the entire tree, passing
  each extend vector of style-fns to `resolve-style-fns` for resolution.
  Takes a collection of `style-fns` and a result collection that is returned
  with the resolved styles"
  [style-fns result]
  (cond

    (fn? style-fns)
    (recur [style-fns] result)

    (and (vector? style-fns) (not (empty? style-fns)))
    (let [styles (resolve-style-fns style-fns [])
          new-meta (into [] (process-meta-xform :extend) styles)]
      (recur new-meta
             (into styles result)))
    :else result))

(defn- extract-meta
  "Takes a group of resolved styles and a meta type. Pull out each meta obj and
  merge to prevent duplicates, finally convert to garden acceptable input and
  return"
  [styles meta-type]
  (let [convert-fn (case meta-type
                     :media convert-media
                     :mode convert-modes)
        extracted (into [] (process-meta-xform meta-type) styles)]
    (when (not (empty? extracted))
      (let [merged (apply merge {} extracted)
            converted (convert-fn merged)]
        converted))))

(defn- prepare-data
  "Prepare `resolved-styles` so they can be passed to `garden.core/css` Merge
  the styles to remove duplicate entries and ensuring precedence. Extract all
  meta and return a final vector of styles including meta."
  [resolved-styles]
   {:style (apply merge {} resolved-styles)
    :mode  (extract-meta resolved-styles :mode)
    :media (extract-meta resolved-styles :media)})

(defn- attach-selector
  "Takes an identifier and a resolved style map and returns a vector with
  classname prepended"
  [selector styles id?]
  [(str (if id? "#" ".") selector) styles])

(defn- sanitize
  "Takes `input` and remove any non-valid characters"
  [input]
  (when input
    (cond
      (keyword? input) (sanitize (name input))
      :else (str/replace (str input) #"[^A-Za-z0-9-_]" "_"))))

(defn- compose-selector
  [n k]
  (str (sanitize n)
       (when k
         (str "-" (sanitize k)))))

(defn- compose-data-string
  [n k]
  (str
   (str/replace n #"\." "/")
   (when (and dev? k) (str "[" k "]"))))

(defn with-style!
  "Entry point for macros.
  Takes an `opt` map as first argument, and currently only supports `:id true`
  which appends an id identifier instead of a class to the DOM"
  [opts fn-name ns-name style-fn & args]
  (let [resolved-styles (extract-styles (into [style-fn] args) [])
        style-data (prepare-data resolved-styles)
        meta-data (-> resolved-styles last meta)
        static (:static meta-data)
        n (.-name style-fn)
        hash* (.abs js/Math (hash style-data) -1)
        name* (cond
                (and (empty? n) (not dev?)) (str "A-" hash*)
                (and dev? (empty? n)) (str ns-name "/" "anonymous-" hash*)
                (and dev? (not (empty? n))) (demunge n)
                :else n)
        data-str (if static
                   (compose-data-string name* nil)
                   (compose-data-string name* (:key meta-data)))
        selector (compose-selector name* (:key meta-data))
        identifier (if static
                     (sanitize name*)
                     selector)
        style-data (attach-selector selector style-data (:id? opts))]
    (runtime/inject-style! identifier style-data data-str)
    selector))
