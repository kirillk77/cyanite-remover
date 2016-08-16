(defproject cyanite-remover "0.6.3-SNAPSHOT"
  :description "Cyanite data removal tool"
  :url "https://github.com/cybem/cyanite-remover"
  :license {:name "MIT License"
            :url "https://github.com/cybem/cyanite-remover/blob/master/LICENSE"}
  :maintainer {:email "cybem@cybem.info"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.spootnik/logconfig "0.7.3"]
                 [cc.qbits/alia "3.1.10"]
                 [cc.qbits/alia-async "3.1.3"]
                 [net.jpountz.lz4/lz4 "1.3.0"]
                 [org.xerial.snappy/snappy-java "1.1.2.6"]
                 [org.clojure/core.async "0.2.385"]
                 [clojurewerkz/elastisch "2.2.2"]
                 [throttler "1.0.0"]
                 [com.climate/claypoole "1.1.3"]
                 [clj-time "0.12.0"]
                 [org.clojure/math.combinatorics "0.1.3"]
                 [intervox/clj-progress "0.2.1"]]
  :main ^:skip-aot cyanite-remover.cli
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
