#!/usr/bin/env bb

(require '[babashka.fs :as fs]
         '[babashka.process :refer [shell process]]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.string :as str])


(def ^:private declair-config (atom nil))


(def ^:const config-dir (str (System/getenv "HOME") "/.config/declair"))


(def config-path (str config-dir "/config.edn"))


(defn read-config-file
  "Reads the declair config file if it exists"
  []
  (when (fs/exists? config-path)
    (edn/read-string (slurp config-path))))


(defn save-config
  "Accepts a config and stores it in the config path"
  [cfg]
  (fs/create-dirs config-dir)
  (spit (str config-dir "/config.edn") cfg))


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
  (try
    (-> (shell {:out :string :err :inherit} "gum" "input" "--placeholder" placeholder)
        :out
        str/trim)
    (catch Exception _
      (System/exit 130))))


(defn create-or-load-config
  "Checks if a declair config file exists or not,
   and if it does, read the NixOS config path from it,
   else asks the user for it"
  []
  (if-let [cfg (read-config-file)]
    (reset! declair-config cfg)
    (let [cfg-path (gum-input "Enter the path to your NixOS configuration file")
          auto-rebuild? (gum-input "Automatically rebuild NixOS after adding a package? (y/N)")]
      (if (fs/exists? cfg-path)
        (save-config {:nix-path cfg-path
                      :auto-rebuild? (or (= auto-rebuild? "y") (= auto-rebuild? "Y"))})
        (do
          (println "❌ Invalid path to config")
          (System/exit 1))))))


(create-or-load-config)


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
  "Adds `selected-pkg` into the `with pkgs; [ ... ]` block
   of the given Nix file, placing it just above the closing `]`.
   NOTE: Also creates a `.declair.bak` backup of the config file before modifying it."
  [file-path selected-pkg]
  (let [lines (vec (str/split-lines (slurp file-path)))
        ;; find the line containing "with pkgs; ["
        start-idx (some->> lines
                           (map-indexed vector)
                           (filter #(str/includes? (second %) "with pkgs; ["))
                           ffirst)
        start-line (when start-idx (nth lines start-idx))
        ;; find first line with ]
        end-idx (when start-idx
                  (some->> (subvec lines start-idx)
                           (map-indexed (fn [i l] [(+ i start-idx) l]))
                           (filter #(str/includes? (second %) "]"))
                           ffirst))
        end-line (when end-idx (nth lines end-idx))
        indent (or (some-> end-line (as-> $ (re-find #"^(\s*)" $)) second) "  ")
        ;; new lines depending on case
        new-lines (cond
                    (nil? end-idx)
                    lines

                    ;; case 1: everything on same line, ie: with pkgs; []
                    (and (= start-idx end-idx)
                         (re-find #"\[\s*\]" start-line))
                    (assoc lines start-idx
                           (str/replace start-line
                                        #"\[\s*\]"
                                        (str "[ " selected-pkg " ]")))

                    ;; case 2: standalone closing bracket on a new line
                    (= (str/trim end-line) "]")
                    (vec (concat
                          (subvec lines 0 end-idx)
                          [(str indent selected-pkg)]
                          (subvec lines end-idx)))

                    ;; case 3: inline closing bracket, eg: "bar]"
                    :else
                    (let [before (subs end-line 0 (str/index-of end-line "]"))
                          after (subs end-line (str/index-of end-line "]"))]
                      (vec (concat
                            (subvec lines 0 end-idx)
                            [before
                             (str indent selected-pkg)
                             (str indent after)]
                            (subvec lines (inc end-idx))))))]
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
            (println (str "Adding `" selected-pkg "` to your NixOS config."))
            (add-pkg (:nix-path @declair-config) selected-pkg)
            (if (:auto-rebuild? @declair-config)
              (do
                (println "Rebuilding NixOS with the new package...")
                (shell "sudo nixos-rebuild switch"))
              (println "Done")))))

      (catch Exception _e
        (stop-spinner!)))))

(-main *command-line-args*)