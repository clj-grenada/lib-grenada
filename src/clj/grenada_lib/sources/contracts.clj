(ns grenada-lib.sources.contracts
  "Contracts for various things that can be customized in the
  grenada-lib.sources."
  (:require [trammel.core :as tr]
            [grenada-lib.util :as gren-util]
            [clojure.java.io :as io]))

(tr/defcontract nssym-src
  "Namespace symbol source."
  [& _] [=> (every? find-ns %)])

(tr/defcontract takes-dir
  "Expects a file or string representing the path to an existing directory."
  [p] [(.isDirectory (io/as-file p))])

(tr/defcontract takes-sym
  "Expects a symbol."
  [s] [(symbol? s)])

(tr/defcontract takes-symtup
  "Expects a vector of two symbols."
  [[s1 s2]] [(symbol? s1) (symbol? s2)])

(tr/defcontract takes-nssym
  "Expects a symbol denoting a findable namespace."
  [s] [(find-ns s)])

(tr/defcontract deftup-src
  "Source of def tuples."
  [& _] [=> (every? (fn [[nssym defsym]]
                      (ns-resolve (find-ns nssym) defsym))
                    %)])

(tr/defcontract returns-nsmap
  "Returns the metadata map for a namespace. (Not fully specified.)"
  [& _] [=> (map? %)])

(tr/defcontract returns-defmap
  "Returns the metadata map for a def (Not fully specified.)"
  [& _] [=> (map? %)])

(tr/defcontract takes-defmap-or-nsmap-or-othermap
  "Don't think about this."
  [m] [(map? m)])

(tr/defcontract returns-mdmap
  "Don't think about this."
  [& _] [=> (map? %)])
