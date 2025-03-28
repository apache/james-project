/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.rate.limiter

import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

import eu.timepit.refined.auto._
import org.apache.james.backends.redis.RedisSentinelExtension.RedisSentinelCluster
import org.apache.james.backends.redis.{RedisConnectionFactory, RedisSentinelExtension}
import org.apache.james.rate.limiter.RedisRateLimiterWithSentinelTest.{RULES, SLIDING_WIDOW_PRECISION}
import org.apache.james.rate.limiter.api._
import org.apache.james.rate.limiter.redis.RedisRateLimiterFactory
import org.apache.james.server.core.filesystem.FileSystemImpl
import org.assertj.core.api.Assertions.{assertThat, assertThatCode}
import org.awaitility.Awaitility
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}
import reactor.core.scala.publisher.SMono

object RedisRateLimiterWithSentinelTest {
  val SLIDING_WIDOW_PRECISION: Option[Duration] = Some(Duration.ofSeconds(1))
  val RULES: Rules = Rules(Seq(Rule(4L, Duration.ofSeconds(2))))
}

@ExtendWith(Array(classOf[RedisSentinelExtension]))
class RedisRateLimiterWithSentinelTest {
  var rateLimiter: RateLimiter = _

  @BeforeEach
  def setUp(redisClusterContainer: RedisSentinelCluster): Unit = {
    val redisConfiguration = redisClusterContainer.redisSentinelContainerList.getRedisConfiguration
    val rateLimiterFactory: RedisRateLimiterFactory = new RedisRateLimiterFactory(redisConfiguration,
      new RedisConnectionFactory(FileSystemImpl.forTesting(), redisConfiguration))
    rateLimiter = rateLimiterFactory.withSpecification(RULES, SLIDING_WIDOW_PRECISION)
  }

  @AfterEach
  def afterEach(redisClusterContainer: RedisSentinelCluster): Unit = {
    redisClusterContainer.redisSentinelContainerList().unPauseFirstNode()
    redisClusterContainer.redisMasterReplicaContainerList.unPauseMasterNode()
  }

  @Test
  def rateLimitShouldBeAcceptableWhenLimitIsAcceptable(): Unit = {
    val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key" + UUID.randomUUID().toString), 4)).block()
    assertThat(actual).isEqualTo(AcceptableRate)
  }

  @Test
  def rateLimitShouldWorkNormallyWhenLimitExceeded(): Unit = {
    val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key" + UUID.randomUUID().toString), 5)).block()
    assertThat(actual).isEqualTo(RateExceeded)
  }

  @Test
  def rateLimitShouldWorkNormallyAfterFailoverComplete(redisClusterContainer: RedisSentinelCluster): Unit = {
    // Before failover, the rate limit should be working normally
    assertThat(SMono(rateLimiter.rateLimit(TestKey("key" + UUID.randomUUID().toString), 5)).block())
      .isEqualTo(RateExceeded)

    // Pause first sentinel node
    redisClusterContainer.redisSentinelContainerList().pauseFirstNode()
    // Give stop redis-master node
    redisClusterContainer.redisMasterReplicaContainerList.pauseMasterNode()
    // Sleep for a while to let sentinel detect the failover. Here is 5 seconds
    Thread.sleep(5000)

    // After failover, the rate limit should be working normally
    Awaitility.await()
      .pollInterval(2, TimeUnit.SECONDS)
      .atMost(100, TimeUnit.SECONDS)
      .untilAsserted(() => assertThatCode(() => SMono(rateLimiter.rateLimit(TestKey("key" + UUID.randomUUID().toString), 1)).block())
        .doesNotThrowAnyException())

    assertThat(SMono(rateLimiter.rateLimit(TestKey("key" + UUID.randomUUID().toString), 10)).block())
      .isNotNull
    assertThat(SMono(rateLimiter.rateLimit(TestKey("key" + UUID.randomUUID().toString), 3)).block())
      .isEqualTo(AcceptableRate)
  }
}
