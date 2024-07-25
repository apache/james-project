# Rate limiting for Apache James on top of Redis sentinel

## Introduction
This guide provides step-by-step instructions for deploying a rate limiter for Apache James using Redis Sentinel.

The redis-sentinel in lab environment is structured with 6 nodes:
- 1 master node (M1)
- 2 replica nodes (R1, R2)
- 3 sentinel nodes (S1, S2, S3)

## Configuration file
- `redis.properties` file sample:

```
redisURL=redis-sentinel://your_password@redis-sentinel-1:26379,redis-sentinel-2:26379,redis-sentinel-3:26379?sentinelMasterId=mymaster
redis.topology=master-replica
```

- Another configuration file of Apache James is similar to the one in the [rate-limiter-redis](../README.adoc) guide.

### How to run 

Run docker-compose with the following command:

```bash
docker-compose up -d -f docker-compose-with-redis-sentinel.yml
```