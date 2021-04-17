#!/usr/bin/env bash
if [ "$GLOWROOT_ACTIVATED" == "true" ]; then
    GLOWROOT_OPTIONS=-javaagent:/root/glowroot/glowroot.jar
fi

java -Dworking.directory=. \
  $GLOWROOT_OPTIONS \
  $JVM_OPTIONS \
  -Dlogback.configurationFile=conf/logback.xml -jar james-server-cassandra-guice.jar