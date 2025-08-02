#!/usr/bin/env bb

(require '[babashka.fs :as fs]
         '[babashka.process :refer [shell process]]
         '[cheshire.core :as json]
         '[clojure.string :as str])


(def ^:private ^:const pkgs-path "/etc/nixos/modules/user-packages.nix")


(defn ensure-gum!
  "Checks if `gum` is available, exits if not"
  []
  (let [result (shell {:out :string :err :string} "which" "gum")]
    (when-not (zero? (:exit result))
      (println "❌ 'gum' is not installed.")
      (println "👉 Install it from https://github.com/charmbracelet/gum#installation")
      (System/exit 1))))

;; if `gum` is not present, exit
(ensure-gum!)


;; gum helpers
(defn gum-input [placeholder]
  (-> (shell {:out :string} "gum" "input" "--placeholder" placeholder)
      :out
      str/trim))


(defn ^:private format-option
  [label style color]
  (-> (shell {:out :string}
             (cond-> "gum style"
               (some? style) (str " --" style)
               (some? color) (str " --" color))
             label)
      :out
      str/trim))


(defn gum-choose-styled [options]
  (let [selected (-> (apply shell {:out :string}
                            "gum" "choose"
                            "--header=Select a package:"
                            options)
                     :out
                     str/trim)]
    (-> selected
        (str/split #" ")
        first)))


(defn massage-search-results
  "Accepts the results from find-pkg as a map,
   and returns a vector of vectors containing the relevant details
   for each found package"
  [result]
  (reduce-kv
   (fn [acc _k {:keys [pname version description]}]
     (conj acc [pname version description]))
   []
   result))


(defn find-pkg
  "Uses `nix search nixpkgs` to find relevant package information as json,
   and returns the massaged results"
  [query]
  (-> {:out :string :err "/dev/null"}
      (shell (str "nix search nixpkgs " query " --json --extra-experimental-features \"nix-command flakes\""))
      :out
      (json/parse-string keyword)
      massage-search-results))


(defn add-pkg
  "Accepts a file-path and a pkg name to be added,
   and adds it to the specified config file as the second
   last line"
  [file-path selected-pkg]
  (let [lines (str/split-lines (slurp file-path))
        indent-pattern #"^(\s*)"
        closing-bracket-line-idx (some->> lines
                                          (map-indexed vector)
                                          (filter #(str/includes? (second %) "]"))
                                          first
                                          first)
        indent (when closing-bracket-line-idx
                 (-> (nth lines closing-bracket-line-idx)
                     (as-> $ (re-find indent-pattern $))
                     second))
        new-lines (if closing-bracket-line-idx
                    (concat
                     (take closing-bracket-line-idx lines)
                     [(str indent "  " selected-pkg)]
                     (drop closing-bracket-line-idx lines))
                    lines)]
    (fs/copy file-path (str file-path ".declair.bak") {:replace-existing true})
    (spit file-path (str/join "\n" new-lines))))


(defn -main [& args]
  (let [pkgs (first args)
        query (if (empty? pkgs) (gum-input "Search for a package") (first pkgs))
        result (atom nil)
        spinner-fn (fn [msg] (process ["gum" "spin" "--spinner" "points" "--title" msg "--" "sleep" "999"]
                                      {:out :inherit :err :inherit}))
        spinner-proc (atom nil)
        stop-spinner! (fn [] (when (some? @spinner-proc)
                               (.destroy (:proc @spinner-proc))
                               (shell "clear")))
        show-spinner! (fn [msg]
                        (stop-spinner!)
                        (shell "clear")
                        (reset! spinner-proc (spinner-fn msg)))]
    (try

      (show-spinner! (str "Searching for \"" query "\"..."))

      ;; look up packages using query
      (reset! result (find-pkg query))

      ;; stop spinner
      (stop-spinner!)

      ;; show selection prompt
      (if (or (nil? @result) (empty? @result))
        (println "No results found")

        ;; let user pick the relevant package
        (do
          (show-spinner! "Gathering relevant packages...")
          (let [formatted-options (mapv (fn [[title version desc]]
                                          (let [styled-title (format-option title "bold" "foreground=10")
                                                styled-version (format-option version "italic" "foreground=3")
                                                styled-desc (format-option desc nil "foreground=8")]
                                            (str styled-title " " styled-version ": " styled-desc)))
                                        @result)
                _ (stop-spinner!)
                selected-pkg (gum-choose-styled formatted-options)]
            (println (str "Adding `" selected-pkg "` to the config."))
            (add-pkg pkgs-path selected-pkg)
            (println "Rebuilding NixOS with the new package...")
            (shell "sudo nixos-rebuild switch"))))

      (catch Exception _e
        (stop-spinner!)))))

(-main *command-line-args*)