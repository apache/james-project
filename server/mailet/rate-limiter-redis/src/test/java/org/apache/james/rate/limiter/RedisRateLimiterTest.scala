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

import org.apache.james.backends.redis.{DockerRedis, RedisConfiguration, RedisExtension}

import java.time.Duration
import org.apache.james.rate.limiter.api.{RateLimiterContract, RateLimiterFactory}
import org.apache.james.rate.limiter.redis.RedisRateLimiterFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(Array(classOf[RedisExtension]))
class RedisRateLimiterTest extends RateLimiterContract {

  var redisRateLimiterConfiguration: RedisConfiguration = _

  @BeforeEach
  def setup(redis: DockerRedis): Unit = {
    redisRateLimiterConfiguration = RedisConfiguration.from(redis.redisURI().toString, false)
  }

  override def testee(): RateLimiterFactory = new RedisRateLimiterFactory(redisRateLimiterConfiguration)

  override def sleep(duration: Duration): Unit = Thread.sleep(duration.toMillis)
}
