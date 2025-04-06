(ns filipesilva.datomic-pro-manager
  (:refer-clojure :exclude [test])
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.process :as process]
            [bling.core :as bling]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.net Socket SocketException]))


;; logging

(defn log [style prefix & args]
  (apply println (bling/bling [style prefix]) args))

(def info  (partial log :bold.info  "info ")) ;; blue
(def error (partial log :bold.error "error")) ;; red
(def run   (fn [& args]
             (apply log :purple "run  " args)
             (try
               (process/shell args)
               (catch Exception _
                 (error "run failed")
                 (System/exit 1)))))


;; config

(def default-config
  {:datomic-version                    "1.0.7277" ;; will be picked up from deps.edn
   :datomic-transactor-properties-path (-> "filipesilva/datomic-pro-manager/sqlite/transactor.properties" io/resource fs/path str)
   :datomic-db-uri                     "datomic:sql://{db-name}?jdbc:sqlite:./storage/sqlite.db"
   :storage-type                       :sqlite
   :sqlite-version                     "3.47.0.0" ;; will be picked up from deps.edn
   :sqlite-init-path                   (-> "filipesilva/datomic-pro-manager/sqlite/init.sql" io/resource fs/path str)
   :postgresql-version                 "42.7.5"   ;; will be picked up from deps.edn
   })

(defn slurp-edn [x]
  (try
    (-> x slurp edn/read-string)
    (catch Exception _)))

(def deps-config
  (let [deps-edn (slurp-edn "deps.edn")
        dpm-edn  (slurp-edn "dpm.edn")]
    (->> dpm-edn
         (merge {:datomic-version    (get-in deps-edn [:deps 'com.datomic/peer :mvn/version])
                 :sqlite-version     (get-in deps-edn [:deps 'org.xerial/sqlite-jdbc :mvn/version])
                 :postgresql-version (get-in deps-edn [:deps 'org.postgresql/postgresql :mvn/version])})
         (filter (comp some? second))
         (into {}))))

(def config
  (merge default-config deps-config))

(def datomic-version (:datomic-version config))
(def sqlite? (= (:storage-type config) :sqlite))
(def sqlite-version (:sqlite-version config))
(def postgresql? (= (:storage-type config) :postgresql))
(def postgresql-version (:postgresql-version config))

;; sqlite

(defn sqlite-exists? []
  (fs/exists? (fs/path "./storage/sqlite.db")))

(declare downloaded?)

(defn sqlite-create [& _]
  (cond
    (not sqlite?)
    (info "SQLite disabled")

    (not (downloaded?))
    (info "Download Datomic Pro before creating SQLite db.")

    (sqlite-exists?)
    (info "SQLite db already exists at ./storage/sqlite.db")

    :else
    (do
      (info "Creating SQLite db at ./storage/sqlite.db")
      (run "mkdir -p ./storage")
      (run (str "sqlite3 ./storage/sqlite.db -init " (:sqlite-init-path config) " .exit")))))

(defn sqlite-delete [m]
  (cond
    (not sqlite?)
    (info "SQLite disabled")

    (-> m :opts :yes)
    (do
      (info "Deleting ./storage")
      (run "rm -rf ./storage"))

    :else
    (do
      (info "This command will delete your SQLite database!")
      (info "Run this command again with --yes to confirm"))))


;; datomic

(def transactor-properties-target-path
  (str (fs/path "./datomic-pro" datomic-version "config/transactor.properties")))

(defn downloaded? []
  (fs/exists? (fs/path "./datomic-pro/" datomic-version)))

(defn clean [& _]
  (info "Deleting Datomic Pro at ./datomic-pro/")
  (run "rm -rf ./datomic-pro"))

(defn port-taken?
  "Returns true if host:port is taken, host defaults to localhost."
  ([port]
   (port-taken? "localhost" port))
  ([host port]
   (try
     (.close (Socket. host port))
     true
     (catch SocketException _
       false))))

(defn download [& _]
  (cond
    (nil? datomic-version)
    (info "Cannot determine Datomic Pro version.")

    (downloaded?)
    (info "Datomic Pro" datomic-version "already downloaded to ./datomic-pro/")

    :else
    (let [dv datomic-version
          sv sqlite-version]
      (info (format "Downloading Datomic Pro %s to ./datomic-pro/%s" dv dv))
      (run "mkdir -p ./datomic-pro")
      (run (format "curl -L https://datomic-pro-downloads.s3.amazonaws.com/%s/datomic-pro-%s.zip -o ./datomic-pro/%s.zip" dv dv dv ))
      (run (format "unzip -q ./datomic-pro/%s.zip -d datomic-pro/" dv))
      (run (format "mv datomic-pro/datomic-pro-%s datomic-pro/%s" dv dv))

      (when sqlite?
        (info (format "Downloading Datomic Pro SQLite driver %s to ./datomic-pro/%s/lib" sv dv))
        (run (format "curl -L https://github.com/xerial/sqlite-jdbc/releases/download/%s/sqlite-jdbc-%s.jar -o ./datomic-pro/%s/lib/sqlite-jdbc-%s.jar" sv sv dv sv))))))

(defn running? []
  (port-taken? 4334))

(defn up [_]
  (if (nil? datomic-version)
    (info "Cannot determine Datomic Pro version.")
    (do
      (when-not (downloaded?)
        (download))
      (when (and sqlite? (not (sqlite-exists?)))
        (sqlite-create))
      (when (or (not (fs/exists? transactor-properties-target-path))
                (not= (slurp (:datomic-transactor-properties-path config))
                      (slurp transactor-properties-target-path)))
        (info "Setting transactor properties")
        (run (str "cp " (:datomic-transactor-properties-path config) " " transactor-properties-target-path)))
      (if (running?)
        (info "Datomic is already running")
        (do
          (info "Starting Datomic")
          (run (format "./datomic-pro/%s/bin/transactor ./config/transactor.properties" datomic-version)))))))

(defn test [_]
  (let [db-uri (str/replace (:datomic-db-uri config) "{db-name}" "*")
        proc   #(process/shell {:continue     true
                                :pre-start-fn (fn [args] (apply log :purple "run  " (:cmd args)))}
                             "clojure"
                             "-Sdeps" {:deps {'com.datomic/peer          {:mvn/version datomic-version}
                                              'org.xerial/sqlite-jdbc    {:mvn/version sqlite-version}
                                              'org.postgresql/postgresql {:mvn/version postgresql-version}
                                              'org.slf4j/slf4j-nop       {:mvn/version "2.0.9"}}}
                             "-M" "--eval"
                             (format
                              "
(require '[datomic.api :as d])
(d/get-database-names \"%s\")
(shutdown-agents)"
                              db-uri))]
    (info (format "Testing connection to %s..." (:datomic-db-uri config)))
    (if (-> (proc) :exit (= 0))
      (info "Connection test successful")
      (do
        (error "Connection test failed")
        (System/exit 1)))))

(defn datomic-bin-relative-db-uri [db-name]
  (-> (:datomic-db-uri config)
      (str/replace "?jdbc:sqlite:./storage/sqlite.db" "?jdbc:sqlite:../../storage/sqlite.db")
      (str/replace "{db-name}" db-name)))

(defn console
  [_]
  ;; https://docs.datomic.com/resources/console.html
  ;; note that the transactor-url does not include db
  (info "Starting Datomic Console")
  (let [db-uri (datomic-bin-relative-db-uri "")]
    (run (format "./datomic-pro/%s/bin/console -p 4335 console %s" datomic-version db-uri))))

(defn db-name-req [db-name]
  (when-not db-name
    (error "db-name is required")
    (System/exit 1)))

(defn backup [{{:keys [db-name]} :opts}]
  (db-name-req db-name)
  (info "Backing up" db-name "to" (str "./backups/" db-name))
  (let [db-uri (datomic-bin-relative-db-uri db-name)
        backup-uri (str "file:" (fs/absolutize (fs/path  "backups" db-name)))]
    (run (format "./datomic-pro/%s/bin/datomic backup-db %s %s" datomic-version db-uri backup-uri))))

(defn restore [{{:keys [db-name]} :opts}]
  (db-name-req db-name)
  (when (running?)
    (info "Stop transactor before running restore")
    (System/exit 1))
  (info "Restoring" db-name "to" (str "./backups/" db-name))
  (let [db-uri (datomic-bin-relative-db-uri db-name)
        backup-uri (str "file:" (fs/absolutize (fs/path  "backups" db-name)))]
    (run (format "./datomic-pro/%s/bin/datomic restore-db %s %s" datomic-version backup-uri db-uri))))


;; CLI

(def help-info (partial log :blue))
(def help-command (partial log :olive))

(defn help [& _args]
  (println (bling/bling [:bold.green "Datomic Pro Manager (DPM)"]))
  (help-info "Datomic docs:" "https://docs.datomic.com")
  (help-info "Datomic Peer API docs:" "https://docs.datomic.com/clojure/index.html")
  (help-info "DPM docs:" "https://github.com/filipesilva/datomic-pro-manager")
  (help-info "Datomic Pro Version:" datomic-version)
  (help-info "Downloaded:" (downloaded?))
  (help-info "Running:" (running?))
  (help-info "DB URI:" (:datomic-db-uri config))
  (help-info "Deps:")
  (println (format "  com.datomic/peer          {:mvn/version \"%s\"}" datomic-version))
  (cond
    sqlite?
    (println (format "  org.xerial/sqlite-jdbc    {:mvn/version \"%s\"}" sqlite-version))

    postgresql?
    (println (format "  org.postgresql/postgresql {:mvn/version \"%s\"}" postgresql-version)))
  (help-info "Create a DB called 'app' and connect to it:")
  (println (format
            "  (require '[datomic.api :as d])
  (def db-uri \"%s\")
  (d/create-database db-uri)
  (def conn (d/connect db-uri))
  (d/db-stats (d/db conn))
  ;; {:datoms 268 ,,,}"
            (str/replace (:datomic-db-uri config) "{db-name}" "app")))
  (help-info "Available commands:")
  (help-command "  up           " "run datomic, downloading and setting it up if needed")
  (help-command "  test         " "test connectivity")
  (help-command "  download     " "download datomic pro")
  (help-command "  clean        " "remove downloaded datomic pro")
  (help-command "  console      " "start datomic console")
  (help-command "  backup <db>  " "backup db to ./backups/db")
  (help-command "  restore <db> " "restore db from ./backups/db")
  (when sqlite?
    (help-command "  sqlite create" "create sqlite db at ./storage")
    (help-command "  sqlite delete" "delete sqlite db at ./storage")))

(def commands
  [{:cmds [] :fn help}
   {:cmds ["up"] :fn up}
   {:cmds ["test"] :fn test}
   {:cmds ["download"] :fn download}
   {:cmds ["clean"] :fn clean}
   {:cmds ["console"] :fn console}
   {:cmds ["backup"] :fn backup, :args->opts [:db-name]}
   {:cmds ["restore"] :fn restore, :args->opts [:db-name]}
   {:cmds ["sqlite" "create"] :fn sqlite-create}
   {:cmds ["sqlite" "delete"] :fn sqlite-delete}])

(defn -main
  [& args]
  (cli/dispatch commands args))
