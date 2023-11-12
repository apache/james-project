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
import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Provides, Scopes}
import es.moki.ratelimitj.core.limiter.request.{AbstractRequestRateLimiterFactory, ReactiveRequestRateLimiter, RequestLimitRule}
import es.moki.ratelimitj.redis.request.{RedisClusterRateLimiterFactory, RedisSlidingWindowRequestRateLimiter, RedisRateLimiterFactory => RedisSingleInstanceRateLimitjFactory}
import io.lettuce.core.RedisClient
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.resource.ClientResources
import org.apache.james.backends.redis.{RedisConfiguration, RedisHealthCheck}

import javax.inject.Inject
import org.apache.james.core.healthcheck.HealthCheck
import org.apache.james.rate.limiter.api.Increment.Increment
import org.apache.james.rate.limiter.api.{AcceptableRate, RateExceeded, RateLimiter, RateLimiterFactory, RateLimitingKey, RateLimitingResult, Rule, Rules}
import org.apache.james.util.concurrent.NamedThreadFactory
import org.apache.james.utils.PropertiesProvider
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

class RedisRateLimiterModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[RateLimiterFactory])
      .to(classOf[RedisRateLimiterFactory])

    bind(classOf[RedisRateLimiterFactory]).in(Scopes.SINGLETON)

    Multibinder.newSetBinder(binder(), classOf[HealthCheck])
      .addBinding()
      .to(classOf[RedisHealthCheck])
  }

  @Provides
  def provideConfig(propertiesProvider: PropertiesProvider): RedisConfiguration =
    RedisConfiguration.from(propertiesProvider.getConfiguration("redis"))
}

class RedisRateLimiterFactory @Inject()(redisConfiguration: RedisConfiguration) extends RateLimiterFactory {
  val rateLimitjFactory: AbstractRequestRateLimiterFactory[RedisSlidingWindowRequestRateLimiter] =
    if (redisConfiguration.isCluster) {
      val resourceBuilder = ClientResources.builder()
        .threadFactoryProvider(poolName => NamedThreadFactory.withName(s"redis-driver-$poolName"))
      redisConfiguration.ioThreads.foreach(value => resourceBuilder.ioThreadPoolSize(value))
      redisConfiguration.workerThreads.foreach(value =>resourceBuilder.computationThreadPoolSize(value))
      new RedisClusterRateLimiterFactory(RedisClusterClient.create(resourceBuilder.build(),
        redisConfiguration.redisURI.value.asJava))
    } else {
      new RedisSingleInstanceRateLimitjFactory(RedisClient.create(redisConfiguration.redisURI.value.last))
    }

  override def withSpecification(rules: Rules, precision: Option[Duration]): RateLimiter =
    RedisRateLimiter(rateLimitjFactory.getInstanceReactive(rules.rules
      .map(convert)
      .map(withPrecision(_, precision))
      .toSet.asJava))

  private def withPrecision(rule: RequestLimitRule, precision: Option[Duration]): RequestLimitRule =
    precision.map(rule.withPrecision).getOrElse(rule)

  private def convert(rule: Rule): RequestLimitRule = RequestLimitRule.of(rule.duration, rule.quantity.value)
}

case class RedisRateLimiter(limiter: ReactiveRequestRateLimiter) extends RateLimiter {
  override def rateLimit(key: RateLimitingKey, increaseQuantity: Increment): Publisher[RateLimitingResult] =
    SMono.fromPublisher(limiter.overLimitWhenIncrementedReactive(key.asString(), increaseQuantity.value))
      .filter(isOverLimit => !isOverLimit)
      .map(_ => AcceptableRate)
      .switchIfEmpty(SMono.just(RateExceeded))

}
