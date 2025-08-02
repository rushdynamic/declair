#!/usr/bin/env bb

(require '[clojure.string :as str]
         '[babashka.process :refer [shell process]])

;; Check for gum
(defn ensure-gum! []
  (let [result (shell {:out :string :err :string} "which" "gum")]
    (when-not (zero? (:exit result))
      (println "❌ 'gum' is not installed.")
      (println "👉 Install it from https://github.com/charmbracelet/gum#installation")
      (System/exit 1))))

(ensure-gum!)


;; Gum helpers
(defn gum-input [placeholder]
  (-> (shell {:out :string} "gum" "input" "--placeholder" placeholder)
      :out
      str/trim))


(defn nix-search
  [pkg]
  (-> {:out :string :err "/dev/null"}
      (shell (str "nix search nixpkgs " pkg " --json --extra-experimental-features \"nix-command flakes\""))
      :out))


(defn -main []
  (let [query (gum-input "Search for a package")
        result (atom nil)
        message (str "Searching for \"" query "\"...")
        spinner (process ["gum" "spin" "--spinner" "dot" "--title" message "--" "sleep" "999"]
                         {:out :inherit :err :inherit})
        results [(str query)
                 (str query "-core")
                 (str query "-extra")]]
    (try
      ;; Run API call
      (reset! result (nix-search query))

      ;; Stop spinner
      (.destroy (:proc spinner))

      (println @result)
      #_(gum-select results)
      (println "Done")

      (catch Exception e
        (.destroy (:proc spinner))
        (throw e)))))

(-main)