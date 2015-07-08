(defproject cyanite-remover "0.3.2-SNAPSHOT"
  :description "Cyanite data removal tool"
  :url "https://github.com/cybem/cyanite-remover"
  :license {:name "MIT License"
            :url "https://github.com/cybem/cyanite-remover/blob/master/LICENSE"}
  :maintainer {:email "cybem@cybem.info"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.spootnik/logconfig "0.7.3"]
                 [cc.qbits/alia "2.5.2"]
                 [net.jpountz.lz4/lz4 "1.3.0"]
                 [org.xerial.snappy/snappy-java "1.1.1.7"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [clojurewerkz/elastisch "2.1.0"]
                 [throttler "1.0.0"]
                 [com.climate/claypoole "0.4.0"]
                 [clj-time "0.9.0"]
                 [intervox/clj-progress "0.1.6"]]
  :main ^:skip-aot cyanite-remover.cli
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
