# java-case

A simple utility library for selecting different forms depending on the Java major version. In case the Clojure needs
to run on multiple JDK platforms, this can make things more concise.

## Releases and Dependency Information

Latest stable release: 1.1

CLI/deps.edn dependency information:

```clojure
org.clojars.jeff_evans/java-case {:mvn/version "1.1"}
```
Leiningen dependency information:

```clojure
[jeff_evans/java-case "1.1"]
```

This library is not yet deployed to Maven central.

## Usage Examples

```clojure
; assume java.security.KeyStore has been imported
(defn file->keystore
  "Initializes a Java KeyStore from the given filename and password."
  [filename password]
  ;; Java 9 adds a getInstance override to initialize directly
  ;; in older versions, it takes multiple steps
  (java-case/java-case
    "9+" (KeyStore/getInstance (io/file filename) password)
    (doto (KeyStore/getInstance (KeyStore/getDefaultType))
      (.load (io/input-stream (str "file:/" filename)) password))))

(defn instant->local-time
  "Converts the given instant to a LocalTime."
  [instant]
  ;; ofInstant came to LocalTime only JDK 9
  (java-case/java-case
    "9+" (LocalTime/ofInstant instant (ZoneId/of "UTC"))
    (let [ldt (LocalDateTime/ofInstant instant (ZoneId/of "UTC"))]
      (.toLocalTime ldt))))

```

The version expressions can be Strings ending in `+`, in which case intermediate JDK versions are filled in. The order
doesn't matter.

```clojure
(macroexpand-1 '(java-case/java-case
                  "11+"  "Java 11 through 16"
                  "17"   "Java 17!"
                  "1.8+" "Java 1.8 through 10"
                  1.7    "Java 1.7, for some reason"
                  "20+"  "Java 20 and beyond"))
=>
(clojure.core/case
  (clojure.core/or
    us.jeffevans.java-case/*java-spec-version-override*
    (us.jeffevans.java-case/current-java-spec-version))
  ("11" "12" "13" "14" "15" "16")
  "Java 11 through 16"
  "17"
  "Java 17!"
  ("1.8" "9" "10")
  "Java 1.8 through 10"
  "1.7"
  "Java 1.7, for some reason"
  "20"
  "Java 20 and beyond")
```

## Future Proof

This library attempts to be future proof, to support major Java versions that may be released after its latest release.  Suppose `K`=`J+1`, and you have a clause
for `J+`. At macro expansion time, the Clojure compiler detects the Java version is `K` (via the JVM property). This clause should work as expected. However, I
haven't thought of the best way to comprehensively test this (suggestions welcomed).

In any case, you can always just use the default clause to specify the "latest" form.

## Building and Testing

This project was created with https://github.com/seancorfield/clj-new

Run the project's tests

    $ clojure -T:build test

Run the project's CI pipeline and build a JAR

    $ clojure -T:build ci

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the JAR in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

Install it locally (requires the `ci` task be run first):

    $ clojure -T:build install

Deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment
variables (requires the `ci` task be run first):

    $ clojure -T:build deploy

## License

Copyright © 2021 Jeff Evans

Distributed under the Eclipse Public License version 1.0.
