#!/bin/bash

echo "Run Postgres"
/usr/local/bin/docker-entrypoint.sh postgres &

echo "Run spamd"
spamd --username debian-spamd \
      --nouser-config \
      --syslog stderr \
      --pidfile /var/run/spamd.pid \
      --helper-home-dir /var/lib/spamassassin \
      --ip-address \
      --allowed-ips 0.0.0.0/0 \
      --allow-tell \
      --debug bayes,learn
