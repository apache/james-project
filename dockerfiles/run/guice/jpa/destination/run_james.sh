#!/usr/bin/env bash
if [ "$GLOWROOT_ACTIVATED" == "true" ]; then
    GLOWROOT_OPTIONS=-javaagent:/root/glowroot/glowroot.jar
fi
java -classpath '/root/james-server.jar:/root/james-server-jpa-guice.lib/*' -javaagent:/root/james-server-jpa-guice.lib/openjpa-3.1.2.jar $GLOWROOT_OPTIONS -Dlogback.configurationFile=/root/conf/logback.xml -Dworking.directory=/root/ $JVM_OPTIONS org.apache.james.JPAJamesServerMain
