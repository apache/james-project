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

import io.lettuce.core.api.reactive.RedisReactiveCommands
import io.lettuce.core.cluster.api.reactive.RedisAdvancedClusterReactiveCommands
import jakarta.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.apache.james.backends.redis.RedisHealthCheck.{pingSuccessResponse, redisComponent}
import org.apache.james.core.healthcheck.{ComponentName, HealthCheck, Result}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

object RedisHealthCheck {
  val pingSuccessResponse: String = "PONG"
  val redisComponent: ComponentName = new ComponentName("Redis")
}

class RedisHealthCheck @Inject()(redisConfiguration: RedisConfiguration, redisConnectionFactory: RedisConnectionFactory) extends HealthCheck {

  private val healthcheckTimeout: Duration = Duration.ofSeconds(3)
  private val healthcheckPerform: RedisHealthcheckPerform = redisConfiguration match {
    case standaloneConfiguration: StandaloneRedisConfiguration => new RedisStandaloneHealthCheckPerform(standaloneConfiguration, redisConnectionFactory, healthcheckTimeout)
    case clusterConfiguration: ClusterRedisConfiguration => new RedisClusterHealthCheckPerform(clusterConfiguration, redisConnectionFactory, healthcheckTimeout)
    case masterReplicaConfiguration: MasterReplicaRedisConfiguration => new RedisMasterReplicaHealthCheckPerform(masterReplicaConfiguration, redisConnectionFactory, healthcheckTimeout)
    case sentinelRedisConfiguration: SentinelRedisConfiguration => new RedisSentinelHealthCheckPerform(sentinelRedisConfiguration, redisConnectionFactory, healthcheckTimeout)
    case _ => throw new NotImplementedError()
  }

  override def componentName(): ComponentName = redisComponent

  override def check(): Publisher[Result] =
    healthcheckPerform.check()
      .onErrorResume(_ => SMono.just(Result.degraded(redisComponent, "Can not connect to Redis.")))
}

sealed trait RedisHealthcheckPerform {
  def check(): SMono[Result]
}

class RedisStandaloneHealthCheckPerform(val redisConfiguration: StandaloneRedisConfiguration,
                                        val redisConnectionFactory: RedisConnectionFactory,
                                        val healthcheckTimeout: Duration) extends RedisHealthcheckPerform {
  private val redisCommand: RedisReactiveCommands[String, String] = redisConnectionFactory.getConnection()
    .asInstanceOf[RedisReactiveCommands[String, String]]

  override def check(): SMono[Result] =
    SMono(redisCommand.ping())
      .timeout(healthcheckTimeout.toScala)
      .filter(_ == pingSuccessResponse)
      .map(_ => Result.healthy(redisComponent))
      .switchIfEmpty(SMono.just(Result.degraded(redisComponent, "Can not PING to Redis.")))
}

class RedisClusterHealthCheckPerform(val redisConfiguration: ClusterRedisConfiguration,
                                     val redisConnectionFactory: RedisConnectionFactory,
                                     val healthcheckTimeout: Duration) extends RedisHealthcheckPerform {

  private val CLUSTER_STATUS_OK: String = "ok"

  private val redisCommand: RedisAdvancedClusterReactiveCommands[String, String] = redisConnectionFactory.getConnection()
    .asInstanceOf[RedisAdvancedClusterReactiveCommands[String, String]]

  override def check(): SMono[Result] =
    SMono(redisCommand.clusterInfo())
      .timeout(healthcheckTimeout.toScala)
      .map(clusterInfo => StringUtils.substringBetween(clusterInfo, "cluster_state:", "\n").trim)
      .map {
        case CLUSTER_STATUS_OK => Result.healthy(redisComponent)
        case unExpectedState => Result.degraded(redisComponent, "Redis cluster state: " + unExpectedState)
      }
}

class RedisMasterReplicaHealthCheckPerform(val redisConfiguration: MasterReplicaRedisConfiguration,
                                           val redisConnectionFactory: RedisConnectionFactory,
                                           val healthcheckTimeout: Duration) extends RedisHealthcheckPerform {
  private val redisCommand: RedisReactiveCommands[String, String] = {
    redisConfiguration.redisURI.value
      .map(rURI => {
        rURI.setTimeout(healthcheckTimeout)
        rURI
      }).asJava

    redisConnectionFactory.getConnection().asInstanceOf[RedisReactiveCommands[String, String]]
  }

  override def check(): SMono[Result] =
    SMono(redisCommand.ping())
      .timeout(healthcheckTimeout.toScala)
      .filter(_ == pingSuccessResponse)
      .map(_ => Result.healthy(redisComponent))
      .switchIfEmpty(SMono.just(Result.degraded(redisComponent, "Can not PING to Redis.")))
}

class RedisSentinelHealthCheckPerform(val redisConfiguration: SentinelRedisConfiguration,
                                      val redisConnectionFactory: RedisConnectionFactory,
                                      val healthcheckTimeout: Duration) extends RedisHealthcheckPerform {
  private val redisCommand: RedisReactiveCommands[String, String] = {
    redisConfiguration.redisURI.setTimeout(healthcheckTimeout)
    redisConnectionFactory.getConnection().asInstanceOf[RedisReactiveCommands[String, String]]
  }

  override def check(): SMono[Result] =
    SMono(redisCommand.ping())
      .timeout(healthcheckTimeout.toScala)
      .filter(_ == pingSuccessResponse)
      .map(_ => Result.healthy(redisComponent))
      .switchIfEmpty(SMono.just(Result.degraded(redisComponent, "Can not PING to Redis.")))
}
