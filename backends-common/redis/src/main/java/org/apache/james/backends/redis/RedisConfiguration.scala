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

import com.google.common.base.Preconditions
import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import io.lettuce.core.RedisURI
import org.apache.commons.configuration2.Configuration
import org.apache.james.backends.redis.RedisUris.RedisUris
import org.slf4j.{Logger, LoggerFactory}

object RedisConfiguration {
  val STANDALONE_TOPOLOGY = "standalone"
  val CLUSTER_TOPOLOGY = "cluster"
  val MASTER_REPLICA_TOPOLOGY = "master-replica"

  val LOGGER: Logger = LoggerFactory.getLogger(classOf[RedisConfiguration])

  def from(config: Configuration): RedisConfiguration = {
    val configuration = from(config.getStringArray("redisURL"),
      config.getString("redis.topology", STANDALONE_TOPOLOGY) match {
        case STANDALONE_TOPOLOGY => Standalone
        case CLUSTER_TOPOLOGY => Cluster
        case MASTER_REPLICA_TOPOLOGY => MasterReplica
        case _ => throw new NotImplementedError()
      },
      Option(config.getInteger("redis.ioThreads", null)).map(Integer2int),
      Option(config.getInteger("redis.workerThreads", null)).map(Integer2int))

    LOGGER.info("Redis was loaded with configuration: \n" +
      "redisURL: {}\n" +
      "redisTopology: {}\n" +
      "redis.ioThreads: {}\n" +
      "redis.workerThreads: {}", configuration.redisURI.value.map(_.toString).mkString(";"),
      configuration.redisTopology, configuration.ioThreads, configuration.workerThreads)

    configuration
  }

  def from(redisUri: String, redisTopology: RedisTopology, ioThreads: Option[Int], workerThreads: Option[Int]): RedisConfiguration =
    from(Array(redisUri), redisTopology, ioThreads, workerThreads)

  def from(redisUris: Array[String], redisTopology: RedisTopology, ioThreads: Option[Int], workerThreads: Option[Int]): RedisConfiguration = {
    Preconditions.checkArgument(redisUris != null && redisUris.length > 0)
    RedisConfiguration(RedisUris.from(redisUris), redisTopology, ioThreads, workerThreads)
  }

  def from(redisUri: String, redisTopology: RedisTopology): RedisConfiguration = from(redisUri, redisTopology, None, None)
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

sealed trait RedisTopology

case object Standalone extends RedisTopology

case object Cluster extends RedisTopology

case object MasterReplica extends RedisTopology

case class RedisConfiguration(redisURI: RedisUris, redisTopology: RedisTopology, ioThreads: Option[Int], workerThreads:Option[Int])
