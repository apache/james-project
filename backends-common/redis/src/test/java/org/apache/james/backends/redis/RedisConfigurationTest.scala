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

import io.lettuce.core.{ReadFrom, RedisURI}
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RedisConfigurationTest extends AnyFlatSpec with Matchers {

  "RedisConfiguration" should "parse Redis URI from config" in {
    val config = new PropertiesConfiguration()
    config.addProperty("redisURL", "redis://localhost:6379")
    config.addProperty("redis.topology", "master-replica")
    config.addProperty("redis.readFrom", "any")
    config.addProperty("redis.ioThreads", 16)
    config.addProperty("redis.workerThreads", 32)

    val redisConfig = RedisConfiguration.from(config)
    redisConfig shouldEqual MasterReplicaRedisConfiguration.from(Array("redis://localhost:6379"), ReadFrom.ANY, Some(16), Some(32))
  }

  it should "parse multiple Redis URIs from config" in {
    val config = new PropertiesConfiguration()
    config.setListDelimiterHandler(new DefaultListDelimiterHandler(','))
    config.addProperty("redisURL", "redis://localhost:6379,redis://localhost:6380")
    config.addProperty("redis.topology", "cluster")
    config.addProperty("redis.ioThreads", 16)
    config.addProperty("redis.workerThreads", 32)

    val redisConfig = RedisConfiguration.from(config)
    redisConfig shouldEqual ClusterRedisConfiguration(RedisUris.liftOrThrow(List(RedisURI.create("redis://localhost:6379"), RedisURI.create("redis://localhost:6380"))), useSSL = false, mayBeSSLConfiguration = None, Some(16), Some(32))
  }

  it should "use default values for missing config values" in {
    val config = new PropertiesConfiguration()
    config.addProperty("redisURL", "redis://localhost:6379")

    val redisConfig = RedisConfiguration.from(config)
    redisConfig shouldEqual StandaloneRedisConfiguration(RedisURI.create("redis://localhost:6379"), useSSL = false, mayBeSSLConfiguration = None, None, None)
  }

  it should "throw exception for invalid Redis URI" in {
    val config = new PropertiesConfiguration()
    config.addProperty("redisURL", "invalid://localhost:6379")

    intercept[IllegalArgumentException] {
      RedisConfiguration.from(config)
    }
  }

  it should "throw exception for invalid Redis topology" in {
    val config = new PropertiesConfiguration()
    config.addProperty("redisURL", "redis://localhost:6379")
    config.addProperty("redis.topology", "invalid")

    intercept[IllegalArgumentException] {
      RedisConfiguration.from(config)
    }
  }

  it should "throw exception for invalid Redis readFrom" in {
    val config = new PropertiesConfiguration()
    config.addProperty("redisURL", "redis://localhost:6379")
    config.addProperty("redis.topology", "master-replica")
    config.addProperty("redis.readFrom", "invalid")

    intercept[IllegalArgumentException] {
      RedisConfiguration.from(config)
    }
  }

  it should "parse redisURL when multiple sentinel endpoint" in {
    val config = new PropertiesConfiguration()
    config.setListDelimiterHandler(new DefaultListDelimiterHandler(','))
    config.addProperty("redisURL", "redis-sentinel://secret1@redis-sentinel-1:26379,redis-sentinel-2:26379,redis-sentinel-3:26379?sentinelMasterId=mymaster")
    config.addProperty("redis.topology", "sentinel")

    val redisConfig: RedisConfiguration = RedisConfiguration.from(config)
    redisConfig.isInstanceOf[SentinelRedisConfiguration] shouldEqual (true)
    val redisConfiguration = redisConfig.asInstanceOf[SentinelRedisConfiguration]

    redisConfiguration.redisURI.toString shouldEqual "redis-sentinel://*******@redis-sentinel-1,redis-sentinel-2,redis-sentinel-3?sentinelMasterId=mymaster"
  }

  it should "parse redisURL when single sentinel endpoint" in {
    val config = new PropertiesConfiguration()
    config.setListDelimiterHandler(new DefaultListDelimiterHandler(','))
    config.addProperty("redisURL", "redis-sentinel://secret1@redis-sentinel-1:26379?sentinelMasterId=mymaster")
    config.addProperty("redis.topology", "sentinel")
    config.addProperty("redis.sentinelPassword", "sentinelpass")

    val redisConfig: RedisConfiguration = RedisConfiguration.from(config)
    redisConfig.isInstanceOf[SentinelRedisConfiguration] shouldEqual (true)
    val redisConfiguration = redisConfig.asInstanceOf[SentinelRedisConfiguration]

    redisConfiguration.redisURI.toString shouldEqual "redis-sentinel://*******@redis-sentinel-1?sentinelMasterId=mymaster"
    redisConfiguration.redisURI.getSentinels.get(0).getCredentialsProvider.resolveCredentials().block().getPassword shouldEqual "sentinelpass".toCharArray
  }

  it should "parse tls redisURL" in {
    val config = new PropertiesConfiguration()
    config.setListDelimiterHandler(new DefaultListDelimiterHandler(','))
    config.addProperty("redisURL", "rediss://secret1@redis-1:6379?verifyPeer=NONE")
    config.addProperty("redis.topology", "standalone")
    config.addProperty("redis.ssl.enabled", true)

    val redisConfig: RedisConfiguration = RedisConfiguration.from(config)
    redisConfig.isInstanceOf[StandaloneRedisConfiguration] shouldEqual (true)
    val redisConfiguration = redisConfig.asInstanceOf[StandaloneRedisConfiguration]

    redisConfiguration.redisURI.toString shouldEqual "rediss://*******@redis-1:6379?verifyPeer=NONE"
    redisConfiguration.useSSL shouldEqual true
    redisConfiguration.mayBeSSLConfiguration.get.ignoreCertificateCheck shouldEqual true
    redisConfiguration.mayBeSSLConfiguration.get.maybeKeyStore shouldEqual None
  }

  it should "parse tls redisURL when master replica topology" in {
    val config = new PropertiesConfiguration()
    config.setListDelimiterHandler(new DefaultListDelimiterHandler(','))
    config.addProperty("redisURL", "rediss://secret1@redis-1:6379?verifyPeer=NONE")
    config.addProperty("redis.topology", "master-replica")
    config.addProperty("redis.ssl.enabled", true)

    val redisConfig: RedisConfiguration = RedisConfiguration.from(config)
    redisConfig.isInstanceOf[MasterReplicaRedisConfiguration] shouldEqual (true)
    val redisConfiguration = redisConfig.asInstanceOf[MasterReplicaRedisConfiguration]

    redisConfiguration.redisURI.value.size shouldEqual 1
    redisConfiguration.redisURI.value.head.toString shouldEqual "rediss://*******@redis-1:6379?verifyPeer=NONE"
  }

  it should "parse tls redisURL when multiple sentinel endpoint" in {
    val config = new PropertiesConfiguration()
    config.setListDelimiterHandler(new DefaultListDelimiterHandler(','))
    config.addProperty("redisURL", "rediss-sentinel://secret1@redis-sentinel-1:26379,redis-sentinel-2:26379,redis-sentinel-3:26379?sentinelMasterId=mymaster&verifyPeer=NONE")
    config.addProperty("redis.topology", "sentinel")
    config.addProperty("redis.ssl.enabled", true)
    config.addProperty("redis.ignoreCertificateCheck", false)
    config.addProperty("redis.keystore.file.path", "/etc/redis/file.p12")
    config.addProperty("redis.keystore.password", "123")

    val redisConfig: RedisConfiguration = RedisConfiguration.from(config)
    redisConfig.isInstanceOf[SentinelRedisConfiguration] shouldEqual (true)
    val redisConfiguration = redisConfig.asInstanceOf[SentinelRedisConfiguration]

    redisConfiguration.redisURI.toString shouldEqual "rediss-sentinel://*******@redis-sentinel-1,redis-sentinel-2,redis-sentinel-3?verifyPeer=NONE&sentinelMasterId=mymaster"
    redisConfiguration.mayBeSSLConfiguration.get.ignoreCertificateCheck shouldEqual false
    redisConfiguration.mayBeSSLConfiguration.get.maybeKeyStore.get.keyStoreFilePath shouldEqual "/etc/redis/file.p12"
    redisConfiguration.mayBeSSLConfiguration.get.maybeKeyStore.get.keyStorePassword shouldEqual "123"
  }

  it should "parse tls redisURL when single sentinel endpoint" in {
    val config = new PropertiesConfiguration()
    config.setListDelimiterHandler(new DefaultListDelimiterHandler(','))
    config.addProperty("redisURL", "rediss-sentinel://secret1@redis-sentinel-1:26379?sentinelMasterId=mymaster&verifyPeer=NONE")
    config.addProperty("redis.topology", "sentinel")
    config.addProperty("redis.ssl.enabled", true)

    val redisConfig: RedisConfiguration = RedisConfiguration.from(config)
    redisConfig.isInstanceOf[SentinelRedisConfiguration] shouldEqual (true)
    val redisConfiguration = redisConfig.asInstanceOf[SentinelRedisConfiguration]

    redisConfiguration.redisURI.toString shouldEqual "rediss-sentinel://*******@redis-sentinel-1?verifyPeer=NONE&sentinelMasterId=mymaster"
  }
}