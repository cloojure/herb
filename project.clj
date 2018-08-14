(defproject herb "0.5.1-SNAPSHOT"
  :description "Clojurescript styling library that tries to mix functional programming with CSS"
  :url "https://github.com/roosta/herb"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-codox "0.10.4"]]

  :source-paths ["src"]

  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [org.clojure/clojurescript "1.10.312" :scope "provided"]
                 [philoskim/debux-stubs "0.4.8"]
                 [org.clojure/tools.analyzer.jvm "0.7.2"]
                 [org.clojure/tools.analyzer "0.6.9"]
                 [garden "1.3.5"]]

  :cljsbuild {:builds [{:id "prod"
                        :source-paths ["src"]
                        :compiler {:output-to "herb.js"
                                   :optimizations :advanced}}]}

  :profiles {:dev {:dependencies [[philoskim/debux "0.4.8"]
                                  [etaoin "0.2.8"]]
                   :source-paths ["test"]}})
