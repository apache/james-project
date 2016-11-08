#!/bin/bash

wait-for-it.sh --host=localhost --port=9999 --strict --timeout=0 -- ./initialdata.sh &

./wrapper-linux-x86-64 ../conf/wrapper.conf wrapper.syslog.ident=james wrapper.pidfile=../var/james.pid wrapper.daemonize=FALSE
