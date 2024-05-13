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

import com.google.common.base.{MoreObjects, Preconditions}
import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import io.lettuce.core.{ReadFrom, RedisURI}
import org.apache.commons.configuration2.Configuration
import org.apache.james.backends.redis.RedisConfiguration.{CLUSTER_TOPOLOGY, MASTER_REPLICA_TOPOLOGY, STANDALONE_TOPOLOGY}
import org.apache.james.backends.redis.RedisUris.RedisUris
import org.slf4j.{Logger, LoggerFactory}

object RedisConfiguration {
  val STANDALONE_TOPOLOGY = "standalone"
  val CLUSTER_TOPOLOGY = "cluster"
  val MASTER_REPLICA_TOPOLOGY = "master-replica"

  val LOGGER: Logger = LoggerFactory.getLogger(classOf[RedisConfiguration])

  def redisIoThreadsFrom(config: Configuration): Option[Int] = Option(config.getInteger("redis.ioThreads", null)).map(Integer2int)

  def redisWorkerThreadsFrom(config: Configuration): Option[Int] = Option(config.getInteger("redis.workerThreads", null)).map(Integer2int)

  def from(config: Configuration): RedisConfiguration = {
    val redisConfiguration: RedisConfiguration = config.getString("redis.topology", STANDALONE_TOPOLOGY) match {
      case STANDALONE_TOPOLOGY => StandaloneRedisConfiguration.from(config)
      case CLUSTER_TOPOLOGY => ClusterRedisConfiguration.from(config)
      case MASTER_REPLICA_TOPOLOGY => MasterReplicaRedisConfiguration.from(config)
      case _ => throw new IllegalArgumentException("Invalid topology")
    }

    LOGGER.info(s"Configured Redis with: ${redisConfiguration.asString}")
    redisConfiguration
  }
}

object RedisUris {
  type RedisUrisConstraint = NonEmpty
  type RedisUris = List[RedisURI] Refined RedisUrisConstraint

  def validate(value: List[RedisURI]): Either[IllegalArgumentException, RedisUris] =
    refined.refineV[RedisUrisConstraint](value) match {
      case Right(value) => Right(value)
      case Left(error) => Left(new IllegalArgumentException(error))
    }

  def liftOrThrow(value: List[RedisURI]): RedisUris =
    validate(value) match {
      case Right(value) => value
      case Left(error) => throw error
    }

  def from(value: String): RedisUris = liftOrThrow(value.split(',').toList.map(RedisURI.create))

  def from(value: Array[String]): RedisUris = liftOrThrow(value.toList.map(RedisURI.create))
}

trait RedisConfiguration {
  def ioThreads: Option[Int]

  def workerThreads: Option[Int]

  def asString: String
}

object StandaloneRedisConfiguration {
  def from(config: Configuration): StandaloneRedisConfiguration = from(
    config.getString("redisURL"),
    RedisConfiguration.redisIoThreadsFrom(config),
    RedisConfiguration.redisWorkerThreadsFrom(config))

  def from(redisUri: String): StandaloneRedisConfiguration = from(redisUri, None, None)

  def from(redisUri: String, ioThreads: Option[Int] = None, workerThreads: Option[Int] = None): StandaloneRedisConfiguration =
    StandaloneRedisConfiguration(RedisURI.create(redisUri), ioThreads, workerThreads)
}

case class StandaloneRedisConfiguration(redisURI: RedisURI, ioThreads: Option[Int], workerThreads: Option[Int]) extends RedisConfiguration {
  override def asString: String = MoreObjects.toStringHelper(this)
    .add("topology", STANDALONE_TOPOLOGY)
    .add("redisURI", redisURI.toString)
    .add("redis.ioThreads", ioThreads)
    .add("redis.workerThreads", workerThreads)
    .toString
}

object MasterReplicaRedisConfiguration {
  def from(config: Configuration): MasterReplicaRedisConfiguration =
    from(config.getStringArray("redisURL"),
      Option(config.getString("redis.readFrom", null)).map(ReadFrom.valueOf).getOrElse(ReadFrom.MASTER),
      RedisConfiguration.redisIoThreadsFrom(config),
      RedisConfiguration.redisWorkerThreadsFrom(config))

  def from(redisUris: Array[String],
           readFrom: ReadFrom,
           ioThreads: Option[Int] = None,
           workerThreads: Option[Int] = None): MasterReplicaRedisConfiguration = {
    Preconditions.checkArgument(redisUris != null && redisUris.length > 0)
    MasterReplicaRedisConfiguration(RedisUris.from(redisUris),
      readFrom,
      ioThreads, workerThreads)
  }
}

case class MasterReplicaRedisConfiguration(redisURI: RedisUris, readFrom: ReadFrom, ioThreads: Option[Int], workerThreads: Option[Int]) extends RedisConfiguration {
  override def asString: String = MoreObjects.toStringHelper(this)
    .add("topology", MASTER_REPLICA_TOPOLOGY)
    .add("redisURI", redisURI.value.map(_.toString).mkString(";"))
    .add("redis.ioThreads", ioThreads)
    .add("redis.workerThreads", workerThreads)
    .toString
}

object ClusterRedisConfiguration {
  def from(config: Configuration): ClusterRedisConfiguration =
    from(config.getStringArray("redisURL"),
      RedisConfiguration.redisIoThreadsFrom(config),
      RedisConfiguration.redisWorkerThreadsFrom(config))

  def from(redisUris: Array[String],
           ioThreads: Option[Int] = None,
           workerThreads: Option[Int] = None): ClusterRedisConfiguration = {
    Preconditions.checkArgument(redisUris != null && redisUris.length > 0)
    ClusterRedisConfiguration(RedisUris.from(redisUris), ioThreads, workerThreads)
  }
}

case class ClusterRedisConfiguration(redisURI: RedisUris, ioThreads: Option[Int], workerThreads: Option[Int]) extends RedisConfiguration {
  override def asString: String = MoreObjects.toStringHelper(this)
    .add("topology", CLUSTER_TOPOLOGY)
    .add("redisURI", redisURI.value.map(_.toString).mkString(";"))
    .add("redis.ioThreads", ioThreads)
    .add("redis.workerThreads", workerThreads)
    .toString
}