#!/bin/sh

cp /usr/local/etc/redis/sentinel.conf.template /usr/local/etc/redis/sentinel.conf
chmod 777 /usr/local/etc/redis/sentinel.conf

redis-sentinel /usr/local/etc/redis/sentinel.conf
