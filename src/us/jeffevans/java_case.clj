(ns us.jeffevans.java-case
  (:require [clojure.string :as str]))

(def ^:dynamic *java-spec-version-override* nil)

(def ^:private ^:const java-spec-version (System/getProperty "java.specification.version"))

(def ^:private ^:const highest-known-spec-version 17)

(def ^:private ^:const known-java-spec-versions
  (conj ["1.0" "1.1" "1.2" "1.3" "1.4" "1.5" "1.6" "1.7" "1.8" "9" "10" "11" "12" "13" "14" "15" "16"]
        (str highest-known-spec-version)))

(defn- highest-version [versions]
  (apply max (map #(Integer. %) (filter #(re-matches #"\d+" %) versions))))

(defn java-spec-versions
  "Gets all recognized Java specification versions. Starts with a fixed, known vector, and expands it if any of the
  following are higher than the highest known version at the time this library was published
  (`highest-known-spec-version`):

  * the `java.specification.version` JVM property value (if an integer)
  * any integer keys from the optional `range-boundaries` parameter

  This is based on the assumption that all future Java spec versions will be increasing integers, from 9 onwards."
  ([]
   (java-spec-versions nil))
  ([range-boundaries]
   (let [int-range-boundaries (filter #(re-matches #"\d+" %) (keys range-boundaries))
         curr-version         (or *java-spec-version-override* java-spec-version)
         highest-v            (highest-version (conj int-range-boundaries curr-version))]
     (if (and highest-v (> highest-v highest-known-spec-version))
       (->> (inc highest-v)
            (range (inc highest-known-spec-version))
            (map str)
            (concat known-java-spec-versions)
            vec)
       known-java-spec-versions))))

(defn- reduce-inputs-kv [acc ^String input idx]
  (if (str/ends-with? input "+")
    (let [ver (.substring input 0 (dec (count input)))]
      (assoc acc ver [true idx]))
    (assoc acc input [false idx])))

(defn- inputs->range-boundaries
  "From an `inputs` vector (version number specifiers), return a map whose keys are version numbers, and whose
  values are two-element vectors. The first item in each vector indicates whether the range at that key is open,
  and the second is the corresponding index back to `inputs` for that entry."
  [inputs]
  (reduce-kv reduce-inputs-kv {} (into {} (keep-indexed (fn [idx input]
                                                           {(str input) idx}) inputs))))

(defn- version-range-reducer [boundaries {:keys [in-range? current-range all-ranges] :as acc} version]
  (cond (contains? boundaries version)
        (assoc acc :in-range? (-> (get boundaries version)
                                  first)
                   :all-ranges (cond-> all-ranges
                                 current-range (assoc (:current-input-idx acc) current-range))
                   :current-range [version]
                   :current-input-idx (-> (get boundaries version)
                                          last))

        in-range?
        (update acc :current-range conj version)

        :else
        acc))

(defn- inputs->version-ranges
  "From an `inputs` vector (version number specifiers), return a map whose keys are version numbers, and whose
  values are two-element vectors. The first item in each vector indicates whether the range at that key is open,
  and the second is the corresponding index back to `inputs` for that entry."
  [inputs]
  (let [boundaries                                           (inputs->range-boundaries inputs)
        {:keys [all-ranges current-input-idx current-range]} (reduce (partial version-range-reducer boundaries)
                                                                     {:all-ranges {}}
                                                                     (java-spec-versions boundaries))]
    (cond-> all-ranges
      current-range
      (assoc current-input-idx current-range))))

(defn- map-indexed-case-exprs [num-defs ranges idx v]
  (if (and (even? idx) (< idx (dec num-defs)))
    (let [version-idx   (/ idx 2)
          version-range (get ranges version-idx)]
      (if (> (count version-range) 1)
        (apply list version-range)
        (first version-range)))
    v))

(defmacro java-case
  "Creates a `case` expression that tests the current Java spec version. Useful for selecting different behavior for
  different JDK versions. `definitions` should be thought of as the clauses to `clojure.core/case` (i.e. alternating
  `java-version` and `result-expr` expressions). The `test-constant` for `case` will be emitted as the current
  `java.specification.version` (or the `*java-spec-version-override*` dynamic var, if set, for testing purposes).

  Each `java-version` expression is expected to be a numeric value or string matching some JDK version. If it's a
  String ending with `+`, then all intermediate major versions will be filled in until the next higher `java-version`
  expression that was given as input. As with `case`, a single default expression may be provided last, which will
  be used if the current JDK version doesn't match any of the given `java-version` expressions.  The specified order of
  the `java-version` constants does not matter.

  This macro attempts to be future proof by assuming that all Java major versions from 9 onwards are increasing integer
  values. As long as this holds, it should be able to work with future Java versions."
  [& definitions]
  (let [num-defs   (count definitions)
        partitions (inputs->version-ranges (map first (partition 2 definitions)))]
    `(case (or *java-spec-version-override* ~java-spec-version)
           ~@(map-indexed (partial map-indexed-case-exprs num-defs partitions) definitions))))
