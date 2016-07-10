#!/bin/sh
lein clean
lein uberjar

sudo mkdir -p /usr/local/lib/reading-room
sudo cp target/*standalone.jar /usr/local/lib/reading-room/reading-room.jar
sudo chown -R reading-room.media /usr/local/lib/reading-room

sudo cp reading-room.service /lib/systemd/system
sudo systemctl daemon-reload


