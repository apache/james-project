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
import java.util.concurrent.TimeUnit

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.core.healthcheck.{ComponentName, HealthCheck, Result}
import org.apache.james.rate.limiter.api.{RateLimitingKey, Rule, Rules}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import scala.concurrent.duration.FiniteDuration

sealed class RedisHealthCheckKey() extends RateLimitingKey {
  override def asString(): String = "RedisHealthCheck"
}

class RedisHealthCheck @Inject()(redisRateLimiterFactory: RedisRateLimiterFactory) extends HealthCheck {
  private val redisComponent: ComponentName = new ComponentName("Redis")
  private val redisHealthcheckKey: RedisHealthCheckKey = new RedisHealthCheckKey
  private val flexibleRateLimitRule = Rules(Seq(Rule(100000L, Duration.ofSeconds(60))))
  private val redisHealthcheckRateLimiter = redisRateLimiterFactory.withSpecification(flexibleRateLimitRule, None)
  private val healthcheckTimeout = new FiniteDuration(3, TimeUnit.SECONDS)

  override def componentName(): ComponentName = redisComponent

  override def check(): Publisher[Result] =
    SMono.fromPublisher(redisHealthcheckRateLimiter.rateLimit(redisHealthcheckKey, 1))
      .timeout(healthcheckTimeout)
      .`then`(SMono.just(Result.healthy(redisComponent)))
      .onErrorResume(e => SMono.just(Result.unhealthy(redisComponent, "Can not connect to Redis.", e)))
}
