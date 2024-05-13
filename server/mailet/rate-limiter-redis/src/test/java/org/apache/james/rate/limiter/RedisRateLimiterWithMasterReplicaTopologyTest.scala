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

import eu.timepit.refined.auto._
import org.apache.james.backends.redis.RedisMasterReplicaExtension
import org.apache.james.backends.redis.RedisMasterReplicaExtension.RedisMasterReplicaContainer
import org.apache.james.rate.limiter.RedisRateLimiterWithMasterReplicaTopologyTest.{RULES, SLIDING_WIDOW_PRECISION}
import org.apache.james.rate.limiter.api.{AcceptableRate, RateLimitingKey, RateLimitingResult, Rule, Rules}
import org.apache.james.rate.limiter.redis.RedisRateLimiterFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.scala.publisher.SMono

object RedisRateLimiterWithMasterReplicaTopologyTest {
  val SLIDING_WIDOW_PRECISION: Option[Duration] = Some(Duration.ofSeconds(1))
  val RULES = Rules(Seq(Rule(4L, Duration.ofSeconds(2))))
}

@ExtendWith(Array(classOf[RedisMasterReplicaExtension]))
class RedisRateLimiterWithMasterReplicaTopologyTest {
  @Test
  def rateLimitShouldWorkNormally(redisClusterContainer: RedisMasterReplicaContainer): Unit = {
    val rateLimiterFactory: RedisRateLimiterFactory = new RedisRateLimiterFactory(redisClusterContainer.getRedisConfiguration)
    val rateLimiter = rateLimiterFactory.withSpecification(RULES, SLIDING_WIDOW_PRECISION)
    val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key1"), 4)).block()
    assertThat(actual).isEqualTo(AcceptableRate)
  }
}

case class TestKey(value: String) extends RateLimitingKey {
  override def asString: String = value
}
