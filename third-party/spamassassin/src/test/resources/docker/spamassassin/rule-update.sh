#!/bin/bash

while true; do
    sleep 1m
    su debian-spamd -c 'sa-update' && kill -HUP `cat /var/run/spamd.pid`
    sleep 1d
done
