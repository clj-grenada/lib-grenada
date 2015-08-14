(ns grenada.annotations)

(defonce annotation-for )

(xyz/provide-find! ["print-method" ::string-entry]
                   {:aspects #{::a/defmethod}
                    :bars {::b/doc "Prints the whole contents of the string
                                   entry."}})

(xyz/provide-bar! ["print-method" ::string-entry]
                  ::b/doc "Prints the whole contents of the string entry.")
