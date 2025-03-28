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

import io.lettuce.core.RedisClient
import io.lettuce.core.api.reactive.RedisReactiveCommands
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.reactive.RedisAdvancedClusterReactiveCommands
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.masterreplica.MasterReplica
import jakarta.inject.Singleton

import scala.jdk.CollectionConverters._

class RedisConnectionFactory @Singleton() {
  def getConnection(redisConfiguration: RedisConfiguration, redisClient: RedisClient): RedisReactiveCommands[String, String] =
    redisConfiguration match {
      case redisMasterReplicaConfiguration: MasterReplicaRedisConfiguration => getMasterReplicaConnection(redisMasterReplicaConfiguration, redisClient)
      case _: StandaloneRedisConfiguration => getStandaloneConnection(redisClient)
      case redisSentinelConfiguration: SentinelRedisConfiguration => getSentinelConnection(redisSentinelConfiguration, redisClient)
      case _ => throw new NotImplementedError()
    }

  def getClusterConnection(redisClusterClient: RedisClusterClient): RedisAdvancedClusterReactiveCommands[String, String] = {
    redisClusterClient.getPartitions
    redisClusterClient.connect().reactive()
  }

  private def getMasterReplicaConnection(redisConfiguration: MasterReplicaRedisConfiguration, redisClient: RedisClient): RedisReactiveCommands[String, String] =
    MasterReplica.connect(redisClient,
        StringCodec.UTF8,
        redisConfiguration.redisURI.value.asJava)
      .reactive()

  private def getStandaloneConnection(redisClient: RedisClient): RedisReactiveCommands[String, String] =
    redisClient.connect().reactive()

  private def getSentinelConnection(redisConfiguration: SentinelRedisConfiguration, redisClient: RedisClient): RedisReactiveCommands[String, String] =
    MasterReplica.connect(redisClient,
        StringCodec.UTF8,
        redisConfiguration.redisURI)
      .reactive()
}
