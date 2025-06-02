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

import io.lettuce.core.cluster.{ClusterClientOptions, RedisClusterClient}
import io.lettuce.core.resource.{ClientResources, ThreadFactoryProvider}
import io.lettuce.core.{AbstractRedisClient, ClientOptions, RedisClient, RedisURI, SslOptions, TimeoutOptions}
import jakarta.annotation.PreDestroy
import jakarta.inject.{Inject, Singleton}
import org.apache.james.filesystem.api.FileSystem
import org.apache.james.util.concurrent.NamedThreadFactory
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._

class RedisClientFactory @Singleton() @Inject()
(fileSystem: FileSystem, redisConfiguration: RedisConfiguration) {
  private val threadFactoryProvider: ThreadFactoryProvider = poolName => NamedThreadFactory.withName(s"redis-driver-$poolName")

  val rawRedisClient: AbstractRedisClient = redisConfiguration match {
    case standaloneRedisConfiguration: StandaloneRedisConfiguration => createStandaloneClient(standaloneRedisConfiguration)
    case masterReplicaRedisConfiguration: MasterReplicaRedisConfiguration => createMasterReplicaClient(masterReplicaRedisConfiguration)
    case clusterRedisConfiguration: ClusterRedisConfiguration => createClusterClient(clusterRedisConfiguration)
    case sentinelRedisConfiguration: SentinelRedisConfiguration => createSentinelClient(sentinelRedisConfiguration)
  }

  private def createStandaloneClient(standaloneRedisConfiguration: StandaloneRedisConfiguration): RedisClient =
    createRedisClient(standaloneRedisConfiguration, Some(standaloneRedisConfiguration.redisURI))

  private def createClusterClient(clusterRedisConfiguration: ClusterRedisConfiguration): RedisClusterClient = {
    val redisClient = RedisClusterClient.create(createClientResources(clusterRedisConfiguration),
      clusterRedisConfiguration.redisURI.value.asJava)
    redisClient.setOptions(ClusterClientOptions.builder(
        createClientOptions(clusterRedisConfiguration.useSSL, clusterRedisConfiguration.mayBeSSLConfiguration))
      .build())
    redisClient
  }

  private def createMasterReplicaClient(masterReplicaRedisConfiguration: MasterReplicaRedisConfiguration): RedisClient =
    createRedisClient(masterReplicaRedisConfiguration, maybeRedisUri = None)

  private def createSentinelClient(sentinelRedisConfiguration: SentinelRedisConfiguration): RedisClient =
    createRedisClient(sentinelRedisConfiguration, Some(sentinelRedisConfiguration.redisURI))

  private def createRedisClient(redisConfiguration: RedisConfiguration, maybeRedisUri: Option[RedisURI]): RedisClient = {
    val redisClient = maybeRedisUri.map(redisUri => RedisClient.create(createClientResources(redisConfiguration), redisUri))
      .getOrElse(RedisClient.create(createClientResources(redisConfiguration)))
    redisClient.setOptions(createClientOptions(redisConfiguration.useSSL, redisConfiguration.mayBeSSLConfiguration))
    redisClient
  }

  private def createClientResources(redisConfiguration: RedisConfiguration): ClientResources = {
    val resourceBuilder: ClientResources.Builder = ClientResources.builder()
      .threadFactoryProvider(threadFactoryProvider)
    redisConfiguration.ioThreads.foreach(value => resourceBuilder.ioThreadPoolSize(value))
    redisConfiguration.workerThreads.foreach(value => resourceBuilder.computationThreadPoolSize(value))
    resourceBuilder.build()
  }

  private def createClientOptions(useSSL: Boolean, mayBeSSLConfiguration: Option[SSLConfiguration]): ClientOptions = {
    val clientOptionsBuilder = ClientOptions.builder
    clientOptionsBuilder.timeoutOptions(TimeoutOptions.enabled)
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

  @PreDestroy
  def close(): Unit = {
    Mono.fromCompletionStage(rawRedisClient.shutdownAsync())
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()
  }
}
