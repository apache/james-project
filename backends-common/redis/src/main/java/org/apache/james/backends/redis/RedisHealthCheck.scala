/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.backends.redis

import java.time.Duration

import io.lettuce.core.RedisClient
import io.lettuce.core.api.reactive.RedisReactiveCommands
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.reactive.RedisAdvancedClusterReactiveCommands
import jakarta.annotation.PreDestroy
import jakarta.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.apache.james.backends.redis.RedisHealthCheck.redisComponent
import org.apache.james.core.healthcheck.{ComponentName, HealthCheck, Result}
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

object RedisHealthCheck {
  val redisComponent: ComponentName = new ComponentName("Redis")
}

class RedisHealthCheck @Inject()(redisConfiguration: RedisConfiguration, redisClientFactory: RedisClientFactory, redisConnectionFactory: RedisConnectionFactory) extends HealthCheck {

  private val healthcheckTimeout: Duration = Duration.ofSeconds(3)
  private val healthcheckPerform: RedisHealthcheckPerform = redisConfiguration match {
    case standaloneConfiguration: StandaloneRedisConfiguration => new RedisStandaloneHealthCheckPerform(standaloneConfiguration, redisClientFactory, redisConnectionFactory, healthcheckTimeout)
    case clusterConfiguration: ClusterRedisConfiguration => new RedisClusterHealthCheckPerform(clusterConfiguration, redisClientFactory, redisConnectionFactory, healthcheckTimeout)
    case masterReplicaConfiguration: MasterReplicaRedisConfiguration => new RedisMasterReplicaHealthCheckPerform(masterReplicaConfiguration, redisClientFactory, redisConnectionFactory, healthcheckTimeout)
    case sentinelRedisConfiguration: SentinelRedisConfiguration => new RedisSentinelHealthCheckPerform(sentinelRedisConfiguration, redisClientFactory, redisConnectionFactory, healthcheckTimeout)
    case _ => throw new NotImplementedError()
  }

  override def componentName(): ComponentName = redisComponent

  override def check(): Publisher[Result] =
    healthcheckPerform.check()
      .onErrorResume(_ => SMono.just(Result.degraded(redisComponent, "Can not connect to Redis.")))

  @PreDestroy
  def close(): Unit = healthcheckPerform.close()
}

sealed trait RedisHealthcheckPerform {
  def check(): SMono[Result]

  def close(): Unit
}

class RedisStandaloneHealthCheckPerform(val redisConfiguration: StandaloneRedisConfiguration,
                                        val redisClientFactory: RedisClientFactory,
                                        val redisConnectionFactory: RedisConnectionFactory,
                                        val healthcheckTimeout: Duration) extends RedisHealthcheckPerform {

  private val PING_SUCCESS_RESPONSE = "PONG"

  private val redisClient: RedisClient =
    redisClientFactory.createStandaloneClient(redisConfiguration, healthcheckTimeout)

  private val redisCommand: RedisReactiveCommands[String, String] = redisConnectionFactory.getConnection(redisConfiguration, redisClient)

  override def check(): SMono[Result] =
    SMono(redisCommand.ping())
      .timeout(healthcheckTimeout.toScala)
      .filter(_ == PING_SUCCESS_RESPONSE)
      .map(_ => Result.healthy(redisComponent))
      .switchIfEmpty(SMono.just(Result.degraded(redisComponent, "Can not PING to Redis.")))

  override def close(): Unit =
    Mono.fromCompletionStage(redisClient.shutdownAsync())
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

}

class RedisClusterHealthCheckPerform(val redisConfiguration: ClusterRedisConfiguration,
                                     val redisClientFactory: RedisClientFactory,
                                     val redisConnectionFactory: RedisConnectionFactory,
                                     val healthcheckTimeout: Duration) extends RedisHealthcheckPerform {

  private val CLUSTER_STATUS_OK: String = "ok"
  private val redisClusterClient: RedisClusterClient = redisClientFactory.createClusterClient(redisConfiguration, healthcheckTimeout)

  private val redisCommand: RedisAdvancedClusterReactiveCommands[String, String] = redisConnectionFactory.getClusterConnection(redisClusterClient)

  override def check(): SMono[Result] =
    SMono(redisCommand.clusterInfo())
      .timeout(healthcheckTimeout.toScala)
      .map(clusterInfo => StringUtils.substringBetween(clusterInfo, "cluster_state:", "\n").trim)
      .map {
        case CLUSTER_STATUS_OK => Result.healthy(redisComponent)
        case unExpectedState => Result.degraded(redisComponent, "Redis cluster state: " + unExpectedState)
      }

  override def close(): Unit = Mono.fromCompletionStage(redisClusterClient.shutdownAsync())
    .subscribeOn(Schedulers.boundedElastic())
    .subscribe()
}

class RedisMasterReplicaHealthCheckPerform(val redisConfiguration: MasterReplicaRedisConfiguration,
                                           val redisClientFactory: RedisClientFactory,
                                           val redisConnectionFactory: RedisConnectionFactory,
                                           val healthcheckTimeout: Duration) extends RedisHealthcheckPerform {

  private val PING_SUCCESS_RESPONSE = "PONG"

  private val redisClient: RedisClient = redisClientFactory.createMasterReplicaClient(redisConfiguration)

  private val redisCommand: RedisReactiveCommands[String, String] = {
    redisConfiguration.redisURI.value
      .map(rURI => {
        rURI.setTimeout(healthcheckTimeout)
        rURI
      }).asJava

    redisConnectionFactory.getConnection(redisConfiguration, redisClient)
  }

  override def check(): SMono[Result] =
    SMono(redisCommand.ping())
      .timeout(healthcheckTimeout.toScala)
      .filter(_ == PING_SUCCESS_RESPONSE)
      .map(_ => Result.healthy(redisComponent))
      .switchIfEmpty(SMono.just(Result.degraded(redisComponent, "Can not PING to Redis.")))

  override def close(): Unit =
    Mono.fromCompletionStage(redisClient.shutdownAsync())
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

}

class RedisSentinelHealthCheckPerform(val redisConfiguration: SentinelRedisConfiguration,
                                      val redisClientFactory: RedisClientFactory,
                                      val redisConnectionFactory: RedisConnectionFactory,
                                      val healthcheckTimeout: Duration) extends RedisHealthcheckPerform {

  private val PING_SUCCESS_RESPONSE = "PONG"

  private val redisClient: RedisClient = redisClientFactory.createSentinelClient(redisConfiguration)

  private val redisCommand: RedisReactiveCommands[String, String] = {
    redisConfiguration.redisURI.setTimeout(healthcheckTimeout)
    redisConnectionFactory.getConnection(redisConfiguration, redisClient)
  }

  override def check(): SMono[Result] =
    SMono(redisCommand.ping())
      .timeout(healthcheckTimeout.toScala)
      .filter(_ == PING_SUCCESS_RESPONSE)
      .map(_ => Result.healthy(redisComponent))
      .switchIfEmpty(SMono.just(Result.degraded(redisComponent, "Can not PING to Redis.")))

  override def close(): Unit =
    Mono.fromCompletionStage(redisClient.shutdownAsync())
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()

}
