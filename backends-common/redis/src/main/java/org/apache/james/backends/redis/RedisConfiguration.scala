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
  val CLUSTER_ENABLED_DEFAULT = false

  val LOGGER: Logger = LoggerFactory.getLogger(classOf[RedisConfiguration])

  def from(config: Configuration): RedisConfiguration = {
    val configuration = from(config.getStringArray("redisURL"),
      config.getBoolean("cluster.enabled", CLUSTER_ENABLED_DEFAULT),
      Option(config.getInteger("redis.ioThreads", null)).map(Integer2int),
      Option(config.getInteger("redis.workerThreads", null)).map(Integer2int))

    LOGGER.info("Redis was loaded with configuration: \n" +
      "redisURL: {}\n" +
      "isCluster: {}\n" +
      "redis.ioThreads: {}\n" +
      "redis.workerThreads: {}", configuration.redisURI.value.map(_.toString).mkString(";"),
      configuration.isCluster, configuration.ioThreads, configuration.workerThreads)

    configuration
  }

  def from(redisUri: String, isCluster: Boolean, ioThreads: Option[Int], workerThreads: Option[Int]): RedisConfiguration =
    from(Array(redisUri), isCluster, ioThreads, workerThreads)

  def from(redisUris: Array[String], isCluster: Boolean, ioThreads: Option[Int], workerThreads: Option[Int]): RedisConfiguration = {
    Preconditions.checkArgument(redisUris != null && redisUris.length > 0)
    Preconditions.checkNotNull(isCluster)
    RedisConfiguration(RedisUris.from(redisUris), isCluster, ioThreads, workerThreads)
  }

  def from(redisUri: String, isCluster: Boolean): RedisConfiguration = from(redisUri, isCluster, None, None)
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

case class RedisConfiguration(redisURI: RedisUris, isCluster: Boolean, ioThreads: Option[Int], workerThreads:Option[Int])
