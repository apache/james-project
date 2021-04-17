#!/usr/bin/env bash
if [ "$GLOWROOT_ACTIVATED" == "true" ]; then
    GLOWROOT_OPTIONS=-javaagent:/root/glowroot/glowroot.jar
fi


java -javaagent:james-server-jpa-smtp-guice.lib/openjpa-3.1.2.jar \
  -Dworking.directory=. \
  $GLOWROOT_OPTIONS \
  $JVM_OPTIONS \
  -Dlogback.configurationFile=conf/logback.xml -jar james-server-jpa-smtp-guice.jar