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

package org.apache.james.oidc.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.backends.redis.RedisReactiveCommandsFactory;
import org.apache.james.core.Username;
import org.apache.james.oidc.OidcTokenCache;
import org.apache.james.oidc.OidcTokenCacheConfiguration;
import org.apache.james.oidc.OidcTokenCacheContract;
import org.apache.james.oidc.Token;
import org.apache.james.oidc.TokenInfo;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;

public abstract class RedisOidcTokenCacheContract extends OidcTokenCacheContract {
    public abstract void pauseRedis();

    private RedisOidcTokenCache redisOidcTokenCache;
    private RedisTokenCacheCommands redisCommands;

    @BeforeEach
    void setUp() {
        token = newToken();
        token2 = newToken();
        mockTokenInfoResolverSuccess(token, TOKEN_INFO);

        RedisConfiguration redisConfiguration = redisConfiguration();
        RedisClientFactory redisClientFactory = new RedisClientFactory(FileSystemImpl.forTesting(), redisConfiguration);
        RedisReactiveCommandsFactory redisReactiveCommandsFactory = new RedisReactiveCommandsFactory(redisClientFactory, redisConfiguration);
        RedisTokenCacheCommands redisReactiveCommands = redisReactiveCommandsFactory.create(
            commands -> RedisTokenCacheCommands.of(commands, RedisOidcTokenCacheConfiguration.DEFAULT.commandTimeout()),
            commands -> RedisTokenCacheCommands.of(commands, RedisOidcTokenCacheConfiguration.DEFAULT.commandTimeout()));
        redisCommands = redisReactiveCommands;
        redisOidcTokenCache = new RedisOidcTokenCache(tokenInfoResolver,
            OidcTokenCacheConfiguration.DEFAULT, redisReactiveCommands, RedisOidcTokenCacheKeyPrefix.JAMES_DEFAULT);
    }

    @Override
    public OidcTokenCache testee() {
        return redisOidcTokenCache;
    }

    public abstract RedisConfiguration redisConfiguration();

    @Override
    public Optional<Username> getUsernameFromCache(Token token) {
        return redisOidcTokenCache.getTokenInfoFromCache("james_oidc_hash_" + Hashing.sha512().hashString(token.value(), StandardCharsets.UTF_8))
            .map(TokenInfo::email)
            .map(Username::of)
            .blockOptional();
    }

    @Test
    public void entriesWithoutTheAudienceListShouldBeCacheMisses() {
        redisCommands.hset("james_oidc_hash_" + Hashing.sha512().hashString(token.value(), StandardCharsets.UTF_8), ImmutableMap.of(
                "email", EMAIL,
                "exp", String.valueOf(EXPIRES_AT.getEpochSecond()),
                "aud", "",
                "sid", SID_STRING))
            .block();

        testee().associatedInformation(token).block();

        verify(tokenInfoResolver, times(1)).apply(token);
    }

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @Test
    public void associatedUsernameShouldStillReturnValueWhenRedisIsDown() {
        mockTokenInfoResolverSuccess(token, TOKEN_INFO);

        pauseRedis();

        assertThat(testee().associatedInformation(token).block())
            .isEqualTo(TOKEN_INFO);
    }

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @Test
    public void invalidateShouldSwallowRedisError() {
        pauseRedis();
        assertThatCode(() -> testee().invalidate(SID).block())
            .doesNotThrowAnyException();
    }

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @Test
    public void shouldFallbackGracefullyWhenRedisIsDown() {
        mockTokenInfoResolverSuccess(token, TOKEN_INFO);
        testee().associatedInformation(token).block();

        pauseRedis();

        // Another session: a logout recorded in Redis refuses the tokens of its own session
        assertThatCode(() -> testee().invalidate(SID_2).block())
            .doesNotThrowAnyException();

        assertThat(testee().associatedInformation(token).block())
            .isEqualTo(TOKEN_INFO);
    }
}
