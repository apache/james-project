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

import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.{RedisClient, RedisURI}
import jakarta.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.apache.james.backends.redis.RedisHealthCheck.redisComponent
import org.apache.james.core.healthcheck.{ComponentName, HealthCheck, Result}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

object RedisHealthCheck {
  val redisComponent: ComponentName = new ComponentName("Redis")
}

class RedisHealthCheck @Inject()(redisConfiguration: RedisConfiguration) extends HealthCheck {

  private val healthcheckTimeout: Duration = Duration.ofSeconds(3)
  private val healthcheckPerform: RedisHealthcheckPerform = redisConfiguration.isCluster match {
    case true => new RedisClusterHealthCheckPerform(redisConfiguration, healthcheckTimeout)
    case false => new RedisStandaloneHealthCheckPerform(redisConfiguration, healthcheckTimeout)
  }

  override def componentName(): ComponentName = redisComponent

  override def check(): Publisher[Result] =
    healthcheckPerform.check()
      .onErrorResume(_ => SMono.just(Result.degraded(redisComponent, "Can not connect to Redis.")))
}

trait RedisHealthcheckPerform {
  def check(): SMono[Result]
}

class RedisClusterHealthCheckPerform(val redisConfiguration: RedisConfiguration,
                                     val healthcheckTimeout: Duration) extends RedisHealthcheckPerform {

  private val CLUSTER_STATUS_OK: String = "ok"

  override def check(): SMono[Result] =
    SFlux.fromIterable(redisConfiguration.redisURI.value)
      .doOnNext(redisUri => redisUri.setTimeout(healthcheckTimeout))
      .collectSeq()
      .map(redisUris => RedisClusterClient.create(redisUris.asJava))
      .flatMap(getClusterInfo)
      .map {
        case CLUSTER_STATUS_OK => Result.healthy(redisComponent)
        case unExpectedState => Result.degraded(redisComponent, "Redis cluster state: " + unExpectedState)
      }

  private def getClusterInfo(redisClusterClient: RedisClusterClient): SMono[String] =
    SMono.fromCallable(() => redisClusterClient.getPartitions)
      .flatMap(_ => SMono.fromFuture(redisClusterClient.connectAsync(StringCodec.UTF8).asScala)
        .flatMap(con => SMono.fromPublisher(con.reactive().clusterInfo())))
      .map(clusterInfo => StringUtils.substringBetween(clusterInfo, "cluster_state:", "\n").trim)
      .doOnTerminate(() => redisClusterClient.shutdownAsync())

}

class RedisStandaloneHealthCheckPerform(val redisConfiguration: RedisConfiguration,
                                        val healthcheckTimeout: Duration) extends RedisHealthcheckPerform {

  override def check(): SMono[Result] =
    SMono.just(redisConfiguration.redisURI.value.last)
      .doOnNext(redisUri => redisUri.setTimeout(healthcheckTimeout))
      .map(redisUri => (RedisClient.create(redisUri), redisUri))
      .flatMap {
        case (redisClient: RedisClient, redisUri: RedisURI) => SMono.fromFuture(redisClient.connectAsync(StringCodec.UTF8, redisUri).asScala)
          .doOnTerminate(() => redisClient.shutdownAsync())
      }.`then`(SMono.just(Result.healthy(redisComponent)))
}
