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

package org.apache.james.jmap.oidc.redis;

import java.time.Duration;
import java.util.Map;

import io.lettuce.core.KeyValue;
import io.lettuce.core.api.reactive.RedisHashReactiveCommands;
import io.lettuce.core.api.reactive.RedisKeyReactiveCommands;
import io.lettuce.core.api.reactive.RedisListReactiveCommands;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RedisTokenCacheCommands {

    public static RedisTokenCacheCommands of(RedisReactiveCommands<String, String> commands, Duration commandTimeout) {
        return new RedisTokenCacheCommands(commands, commands, commands, commandTimeout);
    }

    public static RedisTokenCacheCommands of(RedisClusterReactiveCommands<String, String> commands, Duration commandTimeout) {
        return new RedisTokenCacheCommands(commands, commands, commands, commandTimeout);
    }

    private final RedisKeyReactiveCommands<String, String> keyCommand;
    private final RedisListReactiveCommands<String, String> listCommand;
    private final RedisHashReactiveCommands<String, String> hashCommand;
    private final Duration commandTimeout;

    public RedisTokenCacheCommands(RedisKeyReactiveCommands<String, String> keyCommand,
                                   RedisListReactiveCommands<String, String> listCommand,
                                   RedisHashReactiveCommands<String, String> hashCommand,
                                   Duration commandTimeout) {
        this.keyCommand = keyCommand;
        this.listCommand = listCommand;
        this.hashCommand = hashCommand;
        this.commandTimeout = commandTimeout;
    }

    public Flux<String> lrange(String key) {
        return listCommand.lrange(key, 0, -1)
            .timeout(commandTimeout);
    }

    public Mono<Long> rpush(String key, String... values) {
        return listCommand.rpush(key, values)
            .timeout(commandTimeout);
    }

    public Mono<Void> del(String... key) {
        return keyCommand.del(key)
            .timeout(commandTimeout)
            .then();
    }

    public Mono<Void> expire(String key, Duration duration) {
        return keyCommand.expire(key, duration)
            .timeout(commandTimeout)
            .then();
    }

    public Mono<Void> hset(String key, Map<String, String> map) {
        return hashCommand.hset(key, map)
            .timeout(commandTimeout)
            .then();
    }

    public Flux<KeyValue<String, String>> hgetall(String key) {
        return hashCommand.hgetall(key)
            .timeout(commandTimeout);
    }
}
