#!/bin/sh
lein clean
lein uberjar

/usr/bin/java -Xmx256m -cp target/reading-room-0.1.0-SNAPSHOT-standalone.jar clojure.main -m reading-room.core config/prod/reading-room.edn
