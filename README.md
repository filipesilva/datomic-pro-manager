# Datomic Pro Manager (DPM)

Download, setup, and run [Datomic Pro](https://docs.datomic.com) backed by SQLite in a single command.

When you want to move away from SQLite or DPM, just restore a backup to the new storage.

DPM is not exposing anything that a Datomic Pro install doesn't do.
It's just packaging a subset of functionality neatly, and will show you the commands it is running so you can do them yourself without DPM.


## Installation

You can install DPM globally as `dpm` using [bbin](https://github.com/babashka/bbin):

```
bbin install https://github.com/filipesilva/datomic-pro-manager.git
```

Or as a local `deps.edn` alias that you call with `clojure -M:dpm`:

``` clojure
{:aliases {:dpm
           {:deps      {io.github.filipesilva/datomic-pro-manager {:git/tag "v1.0.0" :git/sha "742c396"}}
            :main-opts ["-m" "filipesilva.datomic-pro-manager"]}}}
```

You will probably want to add these folders to your `.gitignore` if you're running `dpm` locally:

```
# dpm datomic-pro download
/datomic-pro
# dpm sqlite db
/storage
# dpm backups
/backups
```

The rest of the README assumes a global install, but all commands can be called with the local install too.


## Usage

`dpm up` will download, configure, and run a Datomic Pro transactor:

```
info  Downloading Datomic Pro 1.0.7277 to ./datomic-pro/1.0.7277
run   mkdir -p ./datomic-pro
run   curl https://datomic-pro-downloads.s3.amazonaws.com/1.0.7277/datomic-pro-1.0.7277.zip -o ./datomic-pro/1.0.7277.zip
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100  511M  100  511M    0     0  19.3M      0  0:00:26  0:00:26 --:--:-- 21.4M
run   unzip -q ./datomic-pro/1.0.7277.zip -d datomic-pro/
run   mv datomic-pro/datomic-pro-1.0.7277 datomic-pro/1.0.7277
info  Downloading Datomic Pro SQLite driver 3.47.0.0 to ./datomic-pro/1.0.7277/lib
run   curl -L https://github.com/xerial/sqlite-jdbc/releases/download/3.47.0.0/sqlite-jdbc-3.47.0.0.jar -o ./datomic-pro/1.0.7277/lib/sqlite-jdbc-3.47.0.0.jar
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
  0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0
100 13.5M  100 13.5M    0     0  6150k      0  0:00:02  0:00:02 --:--:-- 10.9M
info  Creating SQLite db at ./storage/sqlite.db
run   mkdir -p ./storage
run   sqlite3 ./storage/sqlite.db -init /Users/filipesilva/repos/personal/datomic-pro-manager/resources/filipesilva/datomic-pro-manager/sqlite/init.sql .exit
-- Loading resources from /Users/filipesilva/repos/personal/datomic-pro-manager/resources/filipesilva/datomic-pro-manager/sqlite/init.sql
wal
134217728
67108864
info  Setting transactor properties
run   cp /Users/filipesilva/repos/personal/datomic-pro-manager/resources/filipesilva/datomic-pro-manager/sqlite/transactor.properties ./datomic-pro/1.0.7277/config/transactor.properties
info  Starting Datomic
run   ./datomic-pro/1.0.7277/bin/transactor ./config/transactor.properties
Launching with Java options -server -Xms1g -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=50
System started
```

Running `dpm up` again will use the downloaded Datomic Pro:

```
info  Starting Datomic
run   ./datomic-pro/1.0.7277/bin/transactor ./config/transactor.properties
Launching with Java options -server -Xms1g -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=50
System started
```

`dpm` will show you help and instructions to get started:

```
Datomic Pro Manager (DPM)
Datomic docs: https://docs.datomic.com
Datomic Peer API docs: https://docs.datomic.com/clojure/index.html
DPM docs: https://github.com/filipesilva/datomic-pro-manager
Datomic Pro Version: 1.0.7277
Downloaded: true
Running: true
DB URI: datomic:sql://{db-name}?jdbc:sqlite:./storage/sqlite.db
Deps:
  com.datomic/peer       {:mvn/version "1.0.7277"}
  org.xerial/sqlite-jdbc {:mvn/version "3.47.0.0"}
Create a DB called 'app' and connect to it:
  (require '[datomic.api :as d])
  (def db-uri "datomic:sql://app?jdbc:sqlite:./storage/sqlite.db")
  (d/create-database db-uri)
  (def conn (d/connect db-uri))
  (d/db-stats (d/db conn))
  ;; {:datoms 268 ,,,}
Available commands:
  up            run datomic, downloading and setting it up if needed
  test          test connectivity
  download      download datomic pro
  clean         remove downloaded datomic pro
  console       start datomic console
  backup <db>   backup db to ./backups/db
  restore <db>  restore db from ./backups/db
  sqlite create create sqlite db at ./storage
  sqlite delete delete sqlite db at ./storage
```

You'll need to add the listed deps to your project to connect, and then you can follow the example to create a db called `app`.
If you have those dependencies already in your deps, DPM will use their versions instead of the default.

You can call `dpm test` to test the connection first if you want:

```
info  Testing connection to datomic:sql://{db-name}?jdbc:sqlite:./storage/sqlite.db...
run   clojure -Sdeps {:deps {com.datomic/peer {:mvn/version "1.0.7277"}, org.xerial/sqlite-jdbc {:mvn/version "3.47.0.0"}, org.slf4j/slf4j-nop {:mvn/version "2.0.9"}}} -M --eval
(require '[datomic.api :as d])
(d/get-database-names "datomic:sql://*?jdbc:sqlite:./storage/sqlite.db")
(shutdown-agents)
info  Connection test successful
```

The connection test will work even if the transactor is not running, since it doesn't need the transactor to list databases.


## Backups

After you create a database, you can create a backup with `dpm backup <db>`:

```
info  Backing up app to ./backups/app
run   ./datomic-pro/1.0.7277/bin/datomic backup-db datomic:sql://app?jdbc:sqlite:../../storage/sqlite.db file:/Users/filipesilva/repos/personal/datomic-pro-manager/backups/app
Copied 0 segments, skipped 0 segments.
Copied 32 segments, skipped 0 segments.
:succeeded
```

Datomic Pro backups are incremental, so a second `dpm backup <db>` will only skip copied segments:

```
info  Backing up app to ./backups/app
run   ./datomic-pro/1.0.7277/bin/datomic backup-db datomic:sql://app?jdbc:sqlite:../../storage/sqlite.db file:/Users/filipesilva/repos/personal/datomic-pro-manager/backups/app
Copied 0 segments, skipped 0 segments.
Copied 0 segments, skipped 2 segments.
:succeeded
```

You can verify the backup works by:
- stopping the `dpm up` process and any peers [as per the docs](https://docs.datomic.com/operation/backup.html#restoring)
- deleting the sqlite db with `dpm sqlite delete --yes`
```
info  Deleting ./storage
run   rm -rf ./storage
```

- creating an empty sqlite db `dpm sqlite create`
```
info  Creating SQLite db at ./storage/sqlite.db
run   mkdir -p ./storage
run   sqlite3 ./storage/sqlite.db -init /Users/filipesilva/repos/personal/datomic-pro-manager/resources/filipesilva/datomic-pro-manager/sqlite/init.sql .exit
-- Loading resources from /Users/filipesilva/repos/personal/datomic-pro-manager/resources/filipesilva/datomic-pro-manager/sqlite/init.sql
wal
134217728
67108864
```

- restoring the backup with `dpm restore app`
```
info  Restoring app to ./backups/app
run   ./datomic-pro/1.0.7277/bin/datomic restore-db file:/Users/filipesilva/repos/personal/datomic-pro-manager/backups/app datomic:sql://app?jdbc:sqlite:../../storage/sqlite.db
Copied 0 segments, skipped 0 segments.
Copied 32 segments, skipped 0 segments.
:succeeded
{:event :restore, :db app, :basis-t 66, :inst #inst "1970-01-01T00:00:00.000-00:00"}
```

- finally starting `dpm up` again

Learn more about backups in the [official Datomic Pro docs](https://docs.datomic.com/operation/backup.html).


## Moving away from SQLite

SQLite works pretty well for single a machine, but if you need a multiple machines then you'll want to use a dedicated database like PostgreSQL.
A single machine might get you pretty far though, and is much simpler, so don't rush it unless you know you need to.

It's easy to switch over to something else because Datomic Pro is awesome.
Just make a backup, then restore that backup on your new transactor.
The [Datomic Pro Backup and Restore](https://docs.datomic.com/operation/backup.html) docs are your friend here.

We can try this locally.
Assuming a db called `app`, stop `dpm up`, then make a backup with `dpm backup app`:

```
info  Backing up app to ./backups/app
run   ./datomic-pro/1.0.7277/bin/datomic backup-db datomic:sql://app?jdbc:sqlite:../../storage/sqlite.db file:/Users/filipesilva/repos/personal/datomic-pro-manager/backups/app
Copied 0 segments, skipped 0 segments.
Copied 32 segments, skipped 0 segments.
:succeeded
```

You can still use DPM with Postgres by using a `dpm.edn` configuration file in the directory where you run `dpm`:

``` clojure
{:datomic-transactor-properties-path "./datomic-pro/1.0.7277/config/samples/sql-transactor-template.properties"
 :datomic-db-uri                     "datomic:sql://{db-name}?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"
 :storage-type                       :postgresql}
```

This configuration uses the sample Postgres transactor properties, on the default downloaded version.
You don't have to use DPM though, the backup is just a normal Datomic Pro backup, and you can restore against any Datomic Pro transactor by following the [Datomic Pro Backup and Restore docs](https://docs.datomic.com/operation/backup.html).

Now we'll a local postgres docker container, with the Datomic Pro sql scripts dir mounted:

```
docker run --name datomic-pro-postgres -v ./datomic-pro/1.0.7277/bin/sql:/datomic/bin/sql -e POSTGRES_PASSWORD=datomic -p 5432:5432 postgres
```

You'll have to provision it as per the [Datomic Pro Storage docs](https://docs.datomic.com/operation/storage.html#sql-database):

```
docker exec datomic-pro-postgres bash -c "
    psql -f datomic/bin/sql/postgres-db.sql -U postgres &&
    psql -f datomic/bin/sql/postgres-table.sql -U postgres -d datomic &&
    psql -f datomic/bin/sql/postgres-user.sql -U postgres -d datomic"
```

Now you can restore the backup to Postgres with `dpm restore app`:

```
info  Restoring app to ./backups/app
run   ./datomic-pro/1.0.7277/bin/datomic restore-db file:/Users/filipesilva/repos/personal/datomic-pro-manager/backups/app datomic:sql://app?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic
Copied 0 segments, skipped 0 segments.
Copied 32 segments, skipped 0 segments.
:succeeded
{:event :restore, :db app, :basis-t 66, :inst #inst "1970-01-01T00:00:00.000-00:00"}
```

Finally run `dpm up` again to start the transactor.
The `dpm` command should now show instructions on how to connect to the Postgres storage:

```
Datomic Pro Manager (DPM)
Datomic docs: https://docs.datomic.com
Datomic Peer API docs: https://docs.datomic.com/clojure/index.html
DPM docs: https://github.com/filipesilva/datomic-pro-manager
Datomic Pro Version: 1.0.7277
Downloaded: true
Running: true
DB URI: datomic:sql://{db-name}?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic
Deps:
  com.datomic/peer          {:mvn/version "1.0.7277"}
  org.postgresql/postgresql {:mvn/version "42.7.5"}
Create a DB called 'app' and connect to it:
  (require '[datomic.api :as d])
  (def db-uri "datomic:sql://app?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic")
  (d/create-database db-uri)
  (def conn (d/connect db-uri))
  (d/db-stats (d/db conn))
  ;; {:datoms 268 ,,,}
Available commands:
  up            run datomic, downloading and setting it up if needed
  test          test connectivity
  download      download datomic pro
  clean         remove downloaded datomic pro
  console       start datomic console
  backup <db>   backup db to ./backups/db
  restore <db>  restore db from ./backups/db
```

The remaining commands will also work against the new Postgres storage.
