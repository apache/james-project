package org.apache.james.backends.redis

import java.time.Duration

import io.lettuce.core.RedisClient
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.resource.ClientResources
import org.apache.james.util.concurrent.NamedThreadFactory

import scala.jdk.CollectionConverters._

object RedisClientFactory {
  def createStandaloneClient(redisConfiguration: StandaloneRedisConfiguration): RedisClient =
    createStandaloneClient(redisConfiguration, Option.empty)

  def createStandaloneClient(redisConfiguration: StandaloneRedisConfiguration, timeout: Duration): RedisClient =
    createStandaloneClient(redisConfiguration, Option.apply(timeout))

  def createStandaloneClient(redisConfiguration: StandaloneRedisConfiguration, maybeTimeout: Option[Duration]): RedisClient = {
    maybeTimeout.foreach(timeout => redisConfiguration.redisURI.setTimeout(timeout))
    RedisClient.create(redisConfiguration.redisURI)
  }

  def createClusterClient(redisConfiguration: ClusterRedisConfiguration): RedisClusterClient =
    createClusterClient(redisConfiguration, Option.empty)

  def createClusterClient(redisConfiguration: ClusterRedisConfiguration, timeout: Duration): RedisClusterClient =
    createClusterClient(redisConfiguration, Option.apply(timeout))

  def createClusterClient(redisConfiguration: ClusterRedisConfiguration, maybeTimeout: Option[Duration]): RedisClusterClient = {
    val resourceBuilder: ClientResources.Builder = ClientResources.builder()
      .threadFactoryProvider(poolName => NamedThreadFactory.withName(s"redis-driver-$poolName"))
    redisConfiguration.ioThreads.foreach(value => resourceBuilder.ioThreadPoolSize(value))
    redisConfiguration.workerThreads.foreach(value => resourceBuilder.computationThreadPoolSize(value))
    RedisClusterClient.create(resourceBuilder.build(),
      redisConfiguration.redisURI.value
        .map(rURI => {
          maybeTimeout.foreach(timeout => rURI.setTimeout(timeout))
          rURI
        }).asJava)
  }

  def createMasterReplicaClient(redisConfiguration: MasterReplicaRedisConfiguration): RedisClient =
    RedisClient.create

  def createSentinelClient(redisConfiguration: SentinelRedisConfiguration): RedisClient =
    RedisClient.create
}
