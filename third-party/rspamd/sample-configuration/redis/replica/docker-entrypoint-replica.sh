#!/bin/sh

cp /usr/local/etc/redis/redis.conf.template /usr/local/etc/redis/redis.conf
chmod 777 /usr/local/etc/redis/redis.conf

redis-server /usr/local/etc/redis/redis.conf
