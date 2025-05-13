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

import io.lettuce.core.api.reactive.RedisStringReactiveCommands
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.masterreplica.MasterReplica
import io.lettuce.core.{AbstractRedisClient, RedisClient}
import jakarta.inject.Inject
import org.apache.james.backends.redis.RedisHealthCheck.{healthCheckKey, healthCheckValue, redisComponent}
import org.apache.james.core.healthcheck.{ComponentName, HealthCheck, Result}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

object RedisHealthCheck {
  val redisComponent: ComponentName = new ComponentName("Redis")
  val healthCheckKey: String = "healthcheck"
  val healthCheckValue: String = "healthy"
}

class RedisHealthCheck @Inject()(redisClientFactory: RedisClientFactory, redisConfiguration: RedisConfiguration) extends HealthCheck {

  private val rawRedisClient: AbstractRedisClient = redisClientFactory.rawRedisClient
  private val redisCommand: RedisStringReactiveCommands[String, String] = redisConfiguration match {
    case _: StandaloneRedisConfiguration => rawRedisClient.asInstanceOf[RedisClient].connect().reactive()
    case _: ClusterRedisConfiguration => rawRedisClient.asInstanceOf[RedisClusterClient].connect().reactive()
    case masterReplicaRedisConfiguration: MasterReplicaRedisConfiguration => MasterReplica.connect(rawRedisClient.asInstanceOf[RedisClient],
        StringCodec.UTF8,
        masterReplicaRedisConfiguration.redisURI.value.asJava)
      .reactive()
    case sentinelRedisConfiguration: SentinelRedisConfiguration =>  MasterReplica.connect(rawRedisClient.asInstanceOf[RedisClient],
        StringCodec.UTF8,
        sentinelRedisConfiguration.redisURI)
      .reactive()
  }

  override def componentName(): ComponentName = redisComponent

  override def check(): Publisher[Result] =
    SMono(redisCommand.set(healthCheckKey, healthCheckValue)
        .`then`(redisCommand.getdel(healthCheckKey)))
      .map(_ => Result.healthy(redisComponent))
      .switchIfEmpty(SMono.just(Result.degraded(redisComponent, "Can not write to Redis.")))
      .onErrorResume(_ => SMono.just(Result.degraded(redisComponent, "Can not connect to Redis.")))
}
