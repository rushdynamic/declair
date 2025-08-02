#!/usr/bin/env bb

(require '[clojure.string :as str]
         '[babashka.process :refer [shell process]]
         '[cheshire.core :as json])

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


(defn gum-choose-styled [options-map]
  (let [formatted-options (mapv (fn [[title version desc]]
                                  (let [styled-title (-> (shell {:out :string} "gum" "style"
                                                                "--bold"
                                                                "--foreground=10"
                                                                title)
                                                         :out
                                                         str/trim)
                                        styled-version (-> (shell {:out :string} "gum" "style"
                                                                  "--italic"
                                                                  "--foreground=3"
                                                                  version)
                                                           :out
                                                           str/trim)
                                        styled-desc (-> (shell {:out :string} "gum" "style"
                                                               "--foreground=8" ; gray
                                                               desc)
                                                        :out
                                                        str/trim)]
                                    (str styled-title " " styled-version ": " styled-desc)))
                                options-map)
        selected (-> (apply shell {:out :string}
                            "gum" "choose"
                            "--header=Select a package:"
                            formatted-options)
                     :out
                     str/trim)]
    (-> selected
        (str/split #" ")
        first)))


(defn massage-search-result
  [result]
  (reduce-kv
   (fn [acc _k {:keys [pname version description]}]
     (conj acc [pname version description]))
   []
   result))


(defn nix-search
  [pkg]
  (-> {:out :string :err "/dev/null"}
      (shell (str "nix search nixpkgs " pkg " --json --extra-experimental-features \"nix-command flakes\""))
      :out
      (json/parse-string keyword)
      massage-search-result))


(defn -main [& args]
  (let [pkgs (first args)
        query (if (empty? pkgs) (gum-input "Search for a package") (first pkgs))
        result (atom nil)
        message (str "Searching for \"" query "\"...")
        spinner (process ["gum" "spin" "--spinner" "dot" "--title" message "--" "sleep" "999"]
                         {:out :inherit :err :inherit})]
    (try
      ;; Run API call
      (reset! result (nix-search query))

      ;; Stop spinner
      (.destroy (:proc spinner))

      #_(println @result)
      #_(gum-select results)

      (if (or (nil? @result) (empty? @result))
        (println "No results found")
        (println (gum-choose-styled @result)))



      (catch Exception e
        (.destroy (:proc spinner))
        (throw e)))))

(-main *command-line-args*)