(ns grey.core
  "An experiment at implementing something like Common Lisp's condition/restart
  system (see
  http://www.gigamonkeys.com/book/beyond-exception-handling-conditions-and-restarts.html)
  with Dire and Slingshot."
  (:require [dire.core :as dire]
            [plumbing.core :refer [safe-get-in]]))

;;; Note: Sorry for putting this with lib-grenada, but it's really too small to
;;;       be its own library right now.

;;; TODO: Make it keep up with the spirit of Grenada by somehow making the
;;;       available handlers and their descriptions end up as Bars. (RM
;;;       2015-08-14)

(defn with-handler!
  "Allows you to select a handler for conditions signalled during the execution
  of TASK-VAR in code calling TASK-VAR.

  Expects TASK-VAR to have a map in its metadata under the key :grey/handlers
  and the HANDLER-KEY to be present in this map. When a condition is signalled
  and matches EXCEPTION-SELECTOR, the handler for HANDLER-KEY will be called.

  See also clj::dire.core/with-handler!."
  [task-var exception-selector handler-key]
  (dire/with-handler! task-var
    exception-selector
    (safe-get-in (meta task-var) [:grey/handlers handler-key])))
