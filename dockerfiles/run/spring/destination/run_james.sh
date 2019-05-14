#!/usr/bin/env bash
if [ "$GLOWROOT_ACTIVATED" == "true" ]; then
    GLOWROOT_OPTIONS=wrapper.java.additional.15=-javaagent:/root/glowroot/glowroot.jar
fi
./wrapper-linux-x86-64 ../conf/wrapper.conf wrapper.syslog.ident=james wrapper.pidfile=../var/james.pid wrapper.daemonize=FALSE $GLOWROOT_OPTIONS
