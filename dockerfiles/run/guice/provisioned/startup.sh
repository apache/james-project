#!/bin/bash

wait-for-it.sh --host=localhost --port=9999 --strict --timeout=0 -- ./initialdata.sh &

java -Dlogback.configurationFile=/root/conf/logback.xml -Dworking.directory=/root/ -jar james-server.jar