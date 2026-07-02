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

package org.apache.james.backends.redis;

import java.util.List;

import jakarta.inject.Inject;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.masterreplica.MasterReplica;
import io.lettuce.core.masterreplica.StatefulRedisMasterReplicaConnection;

public class RedisReactiveCommandsFactory {
    public interface CommandsFactory<T> {
        T create(RedisReactiveCommands<String, String> commands);
    }

    public interface ClusterCommandsFactory<T> {
        T create(RedisClusterReactiveCommands<String, String> commands);
    }

    private final RedisClientFactory redisClientFactory;
    private final RedisConfiguration redisConfiguration;

    @Inject
    public RedisReactiveCommandsFactory(RedisClientFactory redisClientFactory, RedisConfiguration redisConfiguration) {
        this.redisClientFactory = redisClientFactory;
        this.redisConfiguration = redisConfiguration;
    }

    public <T> T create(CommandsFactory<T> commandsFactory, ClusterCommandsFactory<T> clusterCommandsFactory) {
        AbstractRedisClient rawClient = redisClientFactory.rawRedisClient();

        return switch (redisConfiguration) {
            case StandaloneRedisConfiguration ignored ->
                commandsFactory.create(((RedisClient) rawClient).connect(StringCodec.UTF8).reactive());

            case ClusterRedisConfiguration clusterConfiguration -> {
                RedisClusterClient client = (RedisClusterClient) rawClient;
                StatefulRedisClusterConnection<String, String> connection = client.connect(StringCodec.UTF8);
                connection.setReadFrom(clusterConfiguration.readFrom());
                yield clusterCommandsFactory.create(connection.reactive());
            }

            case SentinelRedisConfiguration sentinelConfiguration -> {
                StatefulRedisMasterReplicaConnection<String, String> connection = MasterReplica.connect(
                    (RedisClient) rawClient, StringCodec.UTF8, sentinelConfiguration.redisURI());
                connection.setReadFrom(sentinelConfiguration.readFrom());
                yield commandsFactory.create(connection.reactive());
            }

            case MasterReplicaRedisConfiguration masterReplicaConfiguration -> {
                List<RedisURI> uris = RedisConfigurationUtils.asJavaRedisUris(masterReplicaConfiguration.redisURI());
                StatefulRedisMasterReplicaConnection<String, String> connection = MasterReplica.connect(
                    (RedisClient) rawClient, StringCodec.UTF8, uris);
                connection.setReadFrom(masterReplicaConfiguration.readFrom());
                yield commandsFactory.create(connection.reactive());
            }

            default ->
                throw new RuntimeException("Unknown redis configuration type: " + redisConfiguration.getClass().getName());
        };
    }
}
