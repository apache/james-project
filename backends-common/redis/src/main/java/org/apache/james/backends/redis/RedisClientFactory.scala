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

import java.time.Duration

import io.lettuce.core.{ClientOptions, RedisClient, SslOptions}
import io.lettuce.core.cluster.{ClusterClientOptions, RedisClusterClient}
import io.lettuce.core.resource.ClientResources
import jakarta.inject.{Inject, Singleton}
import org.apache.james.filesystem.api.FileSystem
import org.apache.james.util.concurrent.NamedThreadFactory

import scala.jdk.CollectionConverters._

class RedisClientFactory @Singleton() @Inject()
(fileSystem: FileSystem) {
  def createStandaloneClient(redisConfiguration: StandaloneRedisConfiguration): RedisClient =
    createStandaloneClient(redisConfiguration, Option.empty)

  def createStandaloneClient(redisConfiguration: StandaloneRedisConfiguration, timeout: Duration): RedisClient =
    createStandaloneClient(redisConfiguration, Option.apply(timeout))

  def createStandaloneClient(redisConfiguration: StandaloneRedisConfiguration, maybeTimeout: Option[Duration]): RedisClient = {
    maybeTimeout.foreach(timeout => redisConfiguration.redisURI.setTimeout(timeout))
    val redisClient = RedisClient.create(redisConfiguration.redisURI)
    redisClient.setOptions(createClientOptions(redisConfiguration.useSSL, redisConfiguration.mayBeSSLConfiguration))
    redisClient
  }

  def createClusterClient(redisConfiguration: ClusterRedisConfiguration): RedisClusterClient =
    createClusterClient(redisConfiguration, Option.empty)

  def createClusterClient(redisConfiguration: ClusterRedisConfiguration, timeout: Duration): RedisClusterClient =
    createClusterClient(redisConfiguration, Option.apply(timeout))

  def createClusterClient(redisConfiguration: ClusterRedisConfiguration, maybeTimeout: Option[Duration]): RedisClusterClient = {
    val resourceBuilder: ClientResources.Builder = ClientResources.builder()
      .threadFactoryProvider(poolName => NamedThreadFactory.withName(s"redis-driver-$poolName"))
    redisConfiguration.ioThreads.foreach(value => resourceBuilder.ioThreadPoolSize(value))
    redisConfiguration.workerThreads.foreach(value => resourceBuilder.computationThreadPoolSize(value))
    val redisClient = RedisClusterClient.create(resourceBuilder.build(),
      redisConfiguration.redisURI.value
        .map(rURI => {
          maybeTimeout.foreach(timeout => rURI.setTimeout(timeout))
          rURI
        }).asJava)
    redisClient.setOptions(ClusterClientOptions.builder(
        createClientOptions(redisConfiguration.useSSL, redisConfiguration.mayBeSSLConfiguration))
      .build())
    redisClient
  }

  def createMasterReplicaClient(redisConfiguration: MasterReplicaRedisConfiguration): RedisClient = {
    val redisClient = RedisClient.create
    redisClient.setOptions(createClientOptions(redisConfiguration.useSSL, redisConfiguration.mayBeSSLConfiguration))
    redisClient
  }

  def createSentinelClient(redisConfiguration: SentinelRedisConfiguration): RedisClient = {
    val redisClient = RedisClient.create
    redisClient.setOptions(createClientOptions(redisConfiguration.useSSL, redisConfiguration.mayBeSSLConfiguration))
    redisClient
  }

  private def createClientOptions(useSSL: Boolean, mayBeSSLConfiguration: Option[SSLConfiguration]): ClientOptions = {
    val clientOptionsBuilder = ClientOptions.builder
    if (useSSL) {
      mayBeSSLConfiguration.foreach(sslConfig => {
        if (!sslConfig.ignoreCertificateCheck) {
          sslConfig.maybeKeyStore.foreach(redisKeyStore => {
            val sslOptions = SslOptions.builder.jdkSslProvider.keystore(fileSystem.getFile(redisKeyStore.keyStoreFilePath), redisKeyStore.keyStorePassword.toCharArray).build
            clientOptionsBuilder.sslOptions(sslOptions)
          })
        }
      })
    }
    clientOptionsBuilder.build()
  }
}
