#!/bin/bash

wait-for-it.sh --host=localhost --port=9999 --strict --timeout=0 -- ./initialdata.sh &

java -classpath '/root/james-server.jar:/root/james-server-jpa-guice.lib/*' -javaagent:/root/james-server-cli.lib/openjpa-2.4.2.jar -Dlogback.configurationFile=/root/conf/logback.xml -Dworking.directory=/root/ org.apache.james.JPAJamesServerMain