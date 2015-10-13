(defproject cyanite-remover "0.5.1-SNAPSHOT"
  :description "Cyanite data removal tool"
  :url "https://github.com/cybem/cyanite-remover"
  :license {:name "MIT License"
            :url "https://github.com/cybem/cyanite-remover/blob/master/LICENSE"}
  :maintainer {:email "cybem@cybem.info"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.spootnik/logconfig "0.7.3"]
                 [cc.qbits/alia "2.5.3"]
                 [net.jpountz.lz4/lz4 "1.3.0"]
                 [org.xerial.snappy/snappy-java "1.1.2"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [clojurewerkz/elastisch "2.2.0-beta4"]
                 [throttler "1.0.0"]
                 [com.climate/claypoole "1.1.0"]
                 [clj-time "0.11.0"]
                 [org.clojure/math.combinatorics "0.1.1"]
                 [intervox/clj-progress "0.2.1"]]
  :main ^:skip-aot cyanite-remover.cli
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
