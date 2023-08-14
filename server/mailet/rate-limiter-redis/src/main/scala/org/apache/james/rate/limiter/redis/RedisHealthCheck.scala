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

package org.apache.james.rate.limiter.redis

import java.time.Duration

import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.{RedisClient, RedisURI}
import javax.inject.Inject
import org.apache.james.core.healthcheck.{ComponentName, HealthCheck, Result}
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

class RedisHealthCheck @Inject()(redisConfiguration: RedisRateLimiterConfiguration) extends HealthCheck {
  private val redisComponent: ComponentName = new ComponentName("Redis")
  private val healthcheckTimeout = Duration.ofSeconds(3)

  override def componentName(): ComponentName = redisComponent

  override def check(): Publisher[Result] =
    connectRedis()
      .`then`(SMono.just(Result.healthy(redisComponent)))
      .onErrorResume(_ => SMono.just(Result.degraded(redisComponent, "Can not connect to Redis.")))

  private def connectRedis(): SMono[StatefulConnection[String, String]] =
    if (redisConfiguration.isCluster) {
      val redisUris = redisConfiguration.redisURI.value.asJava
      redisUris.forEach(redisUri => redisUri.setTimeout(healthcheckTimeout))

      SMono.fromPublisher(Mono.using(() => RedisClusterClient.create(redisUris),
        (redisClusterClient: RedisClusterClient) => Mono.fromCallable(() => redisClusterClient.connect()),
        (redisClusterClient: RedisClusterClient) => redisClusterClient.close()))
    } else {
      val redisUri: RedisURI = redisConfiguration.redisURI.value.last
      redisUri.setTimeout(healthcheckTimeout)

      SMono.fromPublisher(Mono.using(() => RedisClient.create(redisUri),
        (redisClient: RedisClient) => Mono.fromCallable(() => redisClient.connect()),
        (redisClient: RedisClient) => redisClient.close()))
    }
}
