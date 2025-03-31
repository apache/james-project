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
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.reactive.RedisAdvancedClusterReactiveCommands
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.masterreplica.MasterReplica
import io.lettuce.core.{AbstractRedisClient, RedisClient}
import jakarta.annotation.PreDestroy
import jakarta.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.apache.james.backends.redis.RedisHealthCheck.{healthcheckTimeout, redisComponent}
import org.apache.james.core.healthcheck.{ComponentName, HealthCheck, Result}
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

object RedisHealthCheck {
  val redisComponent: ComponentName = new ComponentName("Redis")
  val healthcheckTimeout: Duration = Duration.ofSeconds(3)
}

class RedisHealthCheck @Inject()(redisClientFactory: RedisClientFactory, redisConfiguration: RedisConfiguration) extends HealthCheck {

  private val rawRedisClient: AbstractRedisClient = redisClientFactory.createRawRedisClient()
  private val healthcheckPerform: RedisHealthcheckPerform = rawRedisClient match {
    case redisClusterClient: RedisClusterClient => new RedisClusterHealthCheckPerform(redisClusterClient)
    case redisClient: RedisClient => new RedisRegularHealthCheckPerform(redisClient, redisConfiguration)
    case _ => throw new NotImplementedError()
  }

  override def componentName(): ComponentName = redisComponent

  override def check(): Publisher[Result] =
    healthcheckPerform.check()
      .onErrorResume(_ => SMono.just(Result.degraded(redisComponent, "Can not connect to Redis.")))

  @PreDestroy
  def close(): Unit =
    Mono.fromCompletionStage(rawRedisClient.shutdownAsync())
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()
}

sealed trait RedisHealthcheckPerform {
  def check(): SMono[Result]
}

class RedisRegularHealthCheckPerform(val redisClient: RedisClient, redisConfiguration: RedisConfiguration) extends RedisHealthcheckPerform {

  private val PING_SUCCESS_RESPONSE = "PONG"

  private val redisCommand: RedisReactiveCommands[String, String] = redisConfiguration match {
    case _: StandaloneRedisConfiguration => redisClient.connect().reactive()
    case masterReplicaRedisConfiguration: MasterReplicaRedisConfiguration => MasterReplica.connect(redisClient,
        StringCodec.UTF8,
        masterReplicaRedisConfiguration.redisURI.value.asJava)
      .reactive()
    case sentinelRedisConfiguration: SentinelRedisConfiguration =>  MasterReplica.connect(redisClient,
        StringCodec.UTF8,
        sentinelRedisConfiguration.redisURI)
      .reactive()
  }

  override def check(): SMono[Result] =
    SMono(redisCommand.ping())
      .timeout(healthcheckTimeout.toScala)
      .filter(_ == PING_SUCCESS_RESPONSE)
      .map(_ => Result.healthy(redisComponent))
      .switchIfEmpty(SMono.just(Result.degraded(redisComponent, "Can not PING to Redis.")))
}

class RedisClusterHealthCheckPerform(val redisClusterClient: RedisClusterClient) extends RedisHealthcheckPerform {

  private val CLUSTER_STATUS_OK: String = "ok"

  private val redisCommand: RedisAdvancedClusterReactiveCommands[String, String] = {
    redisClusterClient.getPartitions
    redisClusterClient.connect().reactive()
  }

  override def check(): SMono[Result] =
    SMono(redisCommand.clusterInfo())
      .timeout(healthcheckTimeout.toScala)
      .map(clusterInfo => StringUtils.substringBetween(clusterInfo, "cluster_state:", "\n").trim)
      .map {
        case CLUSTER_STATUS_OK => Result.healthy(redisComponent)
        case unExpectedState => Result.degraded(redisComponent, "Redis cluster state: " + unExpectedState)
      }
}
