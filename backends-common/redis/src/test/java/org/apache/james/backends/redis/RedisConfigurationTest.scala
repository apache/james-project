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

import io.lettuce.core.RedisURI
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RedisConfigurationTest extends AnyFlatSpec with Matchers {

  "RedisConfiguration" should "parse Redis URI from config" in {
    val config = new PropertiesConfiguration()
    config.addProperty("redisURL", "redis://localhost:6379")
    config.addProperty("cluster.enabled", true)
    config.addProperty("redis.ioThreads", 16)
    config.addProperty("redis.workerThreads", 32)

    val redisConfig = RedisConfiguration.from(config)

    redisConfig.redisURI.value should have length 1
    redisConfig.redisURI.value should contain theSameElementsAs List(RedisURI.create("redis://localhost:6379"))
    redisConfig.isCluster shouldEqual true
    redisConfig.ioThreads shouldEqual Some(16)
    redisConfig.workerThreads shouldEqual Some(32)
  }

  it should "parse multiple Redis URIs from config" in {
    val config = new PropertiesConfiguration()
    config.setListDelimiterHandler(new DefaultListDelimiterHandler(','))
    config.addProperty("redisURL", "redis://localhost:6379,redis://localhost:6380")
    config.addProperty("cluster.enabled", true)
    config.addProperty("redis.ioThreads", 16)
    config.addProperty("redis.workerThreads", 32)

    val redisConfig = RedisConfiguration.from(config)

    redisConfig.redisURI.value should have length 2
    redisConfig.redisURI.value should contain theSameElementsAs List(RedisURI.create("redis://localhost:6379"), RedisURI.create("redis://localhost:6380"))
    redisConfig.isCluster shouldEqual true
    redisConfig.ioThreads shouldEqual Some(16)
    redisConfig.workerThreads shouldEqual Some(32)
  }

  it should "use default values for missing config values" in {
    val config = new PropertiesConfiguration()
    config.addProperty("redisURL", "redis://localhost:6379")

    val redisConfig = RedisConfiguration.from(config)

    redisConfig.redisURI.value should have length 1
    redisConfig.redisURI.value should contain theSameElementsAs List(RedisURI.create("redis://localhost:6379"))
    redisConfig.isCluster shouldEqual false
    redisConfig.ioThreads shouldEqual None
    redisConfig.workerThreads shouldEqual None
  }

  it should "throw exception for invalid Redis URI" in {
    val config = new PropertiesConfiguration()
    config.addProperty("redisURL", "invalid://localhost:6379")

    intercept[IllegalArgumentException] {
      RedisConfiguration.from(config)
    }
  }
}