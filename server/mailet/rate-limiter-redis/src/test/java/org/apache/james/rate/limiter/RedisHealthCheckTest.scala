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

package org.apache.james.rate.limiter

import org.apache.james.core.healthcheck.Result
import org.apache.james.rate.limiter.redis.{RedisHealthCheck, RedisRateLimiterConfiguration, RedisRateLimiterFactory}
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}
import reactor.core.scala.publisher.SMono

@ExtendWith(Array(classOf[RedisExtension]))
class RedisHealthCheckTest {

  var redisHealthCheck: RedisHealthCheck = _

  @BeforeEach
  def setup(redis: DockerRedis): Unit = {
    val redisRateLimiterFactory: RedisRateLimiterFactory = new RedisRateLimiterFactory(
      RedisRateLimiterConfiguration.from(redis.redisURI().toString, false))

    redisHealthCheck = new RedisHealthCheck(redisRateLimiterFactory)
  }

  @AfterEach
  def cleaner(redis: DockerRedis): Unit =
    if (redis.isPaused) {
      redis.unPause()
    }

  @Test
  def checkShouldReturnHealthyWhenRedisIsRunning(): Unit = {
    val result: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    assertThat(result.isHealthy).isTrue
  }

  @Test
  def multipleCheckInShortPeriodShouldReturnHealthyWhenRedisIsRunning(): Unit = {
    val check1: Result = SMono.fromPublisher(redisHealthCheck.check()).block()
    val check2: Result = SMono.fromPublisher(redisHealthCheck.check()).block()
    val check3: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(check1.isHealthy).isTrue
      softly.assertThat(check2.isHealthy).isTrue
      softly.assertThat(check3.isHealthy).isTrue
    })
  }

  @Test
  def checkShouldReturnUnhealthyWhenRedisIsDown(redis: DockerRedis): Unit = {
    redis.pause()
    val result: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    assertThat(result.isUnHealthy).isTrue
  }

  @Test
  def checkShouldReturnHealthyWhenRedisIsRecovered(redis: DockerRedis): Unit = {
    redis.pause()
    redis.unPause()
    val result: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    assertThat(result.isHealthy).isTrue
  }

  @Test
  def multipleCheckInShortPeriodShouldReturnMixedResultWhenRedisIsUnstable(redis: DockerRedis): Unit = {
    val check1: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    redis.pause()
    val check2: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    redis.unPause()
    val check3: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(check1.isHealthy).isTrue
      softly.assertThat(check2.isUnHealthy).isTrue
      softly.assertThat(check3.isHealthy).isTrue
    })
  }

}
