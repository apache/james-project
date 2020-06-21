#!/usr/bin/env bash
if [ "$GLOWROOT_ACTIVATED" == "true" ]; then
    GLOWROOT_OPTIONS=-javaagent:/root/glowroot/glowroot.jar
fi
java -Dlogback.configurationFile=/root/conf/logback.xml -Dworking.directory=/root/ $JVM_OPTIONS $GLOWROOT_OPTIONS -jar james-server-memory-guice.jar