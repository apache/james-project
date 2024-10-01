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

import org.apache.james.backends.redis.RedisTLSExtension.RedisContainer
import org.apache.james.server.core.filesystem.FileSystemImpl
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.{AfterEach, BeforeEach}

@ExtendWith(Array(classOf[RedisTLSExtension]))
class RedisTLSStandaloneHealthCheckTest extends RedisHealthCheckTest {
  var redisHealthCheck: RedisHealthCheck = _
  var redisContainer: RedisContainer = _

  @BeforeEach
  def setup(redisContainer: RedisContainer): Unit = {
    redisHealthCheck = new RedisHealthCheck(redisContainer.getConfiguration, new RedisClientFactory(FileSystemImpl.forTesting()))
    this.redisContainer = redisContainer;
  }

  @AfterEach
  def afterEach(): Unit = {
    redisContainer.unPause();
  }

  @Override
  def getRedisHealthCheck(): RedisHealthCheck = redisHealthCheck

  @Override
  def pauseRedis(): Unit = {
    redisContainer.pause()
  }

  @Override
  def unpauseRedis(): Unit = {
    redisContainer.unPause()
  }
}
