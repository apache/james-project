/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.events

import java.util

import io.lettuce.core.api.reactive.RedisSetReactiveCommands
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.{AbstractRedisClient, RedisClient, RedisURI}
import javax.inject.{Inject, Singleton}
import org.apache.james.backends.redis.RedisConfiguration

import scala.jdk.CollectionConverters._

class RedisEventBusClientFactory @Singleton() @Inject()
(redisConfiguration: RedisConfiguration) {
  val rawRedisClient: AbstractRedisClient =
    if (redisConfiguration.isCluster) {
      val redisUris: util.List[RedisURI] = redisConfiguration.redisURI.value.asJava
      RedisClusterClient.create(redisUris)
    } else {
      RedisClient.create(redisConfiguration.redisURI.value.last)
    }

  def createRedisPubSubCommand(): RedisPubSubReactiveCommands[String, String] =
    if (redisConfiguration.isCluster) {
      rawRedisClient.asInstanceOf[RedisClusterClient]
        .connectPubSub().reactive()
    } else {
      rawRedisClient.asInstanceOf[RedisClient]
        .connectPubSub().reactive()
    }

  def createRedisSetCommand(): RedisSetReactiveCommands[String, String] =
    if (redisConfiguration.isCluster) {
      rawRedisClient.asInstanceOf[RedisClusterClient]
        .connect().reactive()
    } else {
      rawRedisClient.asInstanceOf[RedisClient]
        .connect().reactive()
    }
}
