/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.backends.redis

import org.apache.james.backends.redis.RedisClusterExtension.RedisClusterContainer
import org.apache.james.server.core.filesystem.FileSystemImpl
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.{AfterEach, BeforeEach, Disabled, Test}

@ExtendWith(Array(classOf[RedisClusterExtension]))
class RedisClusterHealthCheckTest extends RedisHealthCheckTest {
  var redisHealthCheck: RedisHealthCheck = _
  var redisClusterContainer: RedisClusterContainer = _

  @BeforeEach
  def setup(redis: RedisClusterContainer): Unit = {
    redisHealthCheck = new RedisHealthCheck(redis.getRedisConfiguration, new RedisClientFactory(FileSystemImpl.forTesting()))
    redisClusterContainer = redis
  }

  @AfterEach
  def afterEach(): Unit = {
    redisClusterContainer.unPauseOne();
  }

  @Override
  def getRedisHealthCheck(): RedisHealthCheck = redisHealthCheck

  @Override
  def pauseRedis(): Unit = {
    redisClusterContainer.pauseOne()
    Thread.sleep(4000) // cluster-node-timeout 3000
  }

  @Override
  def unpauseRedis(): Unit = {
    redisClusterContainer.unPauseOne()
  }

  @Disabled("Flaky test with Cluster Redis")
  @Test
  override def multipleCheckInShortPeriodShouldReturnMixedResultWhenRedisIsUnstable(): Unit = {
  }
}
