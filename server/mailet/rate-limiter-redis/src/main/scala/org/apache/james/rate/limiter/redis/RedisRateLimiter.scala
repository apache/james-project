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

import com.google.inject.AbstractModule
import es.moki.ratelimitj.core.limiter.request.{AbstractRequestRateLimiterFactory, ReactiveRequestRateLimiter, RequestLimitRule}
import es.moki.ratelimitj.redis.request.{RedisClusterRateLimiterFactory, RedisSlidingWindowRequestRateLimiter, RedisRateLimiterFactory => RedisSingleInstanceRateLimitjFactory}
import io.lettuce.core.RedisClient
import io.lettuce.core.cluster.RedisClusterClient
import org.apache.james.rate.limiter.api.Increment.Increment
import org.apache.james.rate.limiter.api.{AcceptableRate, RateExceeded, RateLimiter, RateLimiterFactory, RateLimiterFactoryProvider, RateLimitingKey, RateLimitingResult, Rule, Rules}
import org.apache.mailet.MailetConfig
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

class RedisRateLimiterModule() extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[RateLimiterFactoryProvider])
      .to(classOf[RedisRateLimiterFactoryProvider])
}

class RedisRateLimiterFactoryProvider extends RateLimiterFactoryProvider {
  override def create(mailetConfig: MailetConfig): RateLimiterFactory = new RedisRateLimiterFactory(RedisRateLimiterConfiguration.from(mailetConfig))
}

class RedisRateLimiterFactory(redisConfiguration: RedisRateLimiterConfiguration) extends RateLimiterFactory {
  val rateLimitjFactory: AbstractRequestRateLimiterFactory[RedisSlidingWindowRequestRateLimiter] =
    if (redisConfiguration.redisURI.value.size > 1) {
      new RedisClusterRateLimiterFactory(RedisClusterClient.create(redisConfiguration.redisURI.value.asJava))
    } else {
      new RedisSingleInstanceRateLimitjFactory(RedisClient.create(redisConfiguration.redisURI.value.last))
    }

  override def withSpecification(rules: Rules): RateLimiter =
    RedisRateLimiter(rateLimitjFactory.getInstanceReactive(rules.rules
      .map(convert)
      .map(withPrecision)
      .toSet.asJava))

  private def withPrecision(rule: RequestLimitRule): RequestLimitRule =
    redisConfiguration.windowPrecision
      .map(rule.withPrecision)
      .getOrElse(rule)

  private def convert(rule: Rule): RequestLimitRule = RequestLimitRule.of(rule.duration, rule.quantity.value)
}

case class RedisRateLimiter(limiter: ReactiveRequestRateLimiter) extends RateLimiter {
  override def rateLimit(key: RateLimitingKey, increaseQuantity: Increment): Publisher[RateLimitingResult] =
    SMono.fromPublisher(limiter.overLimitWhenIncrementedReactive(key.asString(), increaseQuantity.value))
      .filter(isOverLimit => !isOverLimit)
      .map(_ => AcceptableRate)
      .switchIfEmpty(SMono.just(RateExceeded))

}
