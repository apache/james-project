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

import java.io.FileNotFoundException;
import java.time.Duration;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.backends.redis.RedisReactiveCommandsFactory;
import org.apache.james.oidc.OidcTokenCache;
import org.apache.james.oidc.OidcTokenCacheConfiguration;
import org.apache.james.oidc.TokenInfoResolver;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

public class RedisOidcTokenCacheModule extends AbstractModule {
    public static final Logger LOGGER = LoggerFactory.getLogger(RedisOidcTokenCacheModule.class);

    @Override
    protected void configure() {
        bind(OidcTokenCache.class).to(RedisOidcTokenCache.class)
            .in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    public RedisOidcTokenCache provideRedisOIDCTokenCache(RedisReactiveCommandsFactory redisReactiveCommandsFactory,
                                                          OidcTokenCacheConfiguration oidcTokenCacheConfiguration,
                                                          RedisOidcTokenCacheConfiguration redisOidcTokenCacheConfiguration,
                                                          TokenInfoResolver tokenInfoResolver,
                                                          RedisOidcTokenCacheKeyPrefix keyPrefix) {
        Duration commandTimeout = redisOidcTokenCacheConfiguration.commandTimeout();
        RedisTokenCacheCommands redisReactiveCommands = redisReactiveCommandsFactory.create(
            commands -> RedisTokenCacheCommands.of(commands, commandTimeout),
            commands -> RedisTokenCacheCommands.of(commands, commandTimeout));

        return new RedisOidcTokenCache(tokenInfoResolver, oidcTokenCacheConfiguration, redisReactiveCommands, keyPrefix);
    }

    @Provides
    @Singleton
    public RedisConfiguration redisConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        try {
            return RedisConfiguration.from(propertiesProvider.getConfiguration("redis"));
        } catch (FileNotFoundException e) {
            LOGGER.error("Missing `redis.properties` configuration file for Redis OIDC token cache usage.");
            throw e;
        }
    }

    @Provides
    @Singleton
    public RedisOidcTokenCacheConfiguration redisOidcTokenCacheConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return RedisOidcTokenCacheConfiguration.from(propertiesProvider.getConfiguration("redis"));
        } catch (FileNotFoundException e) {
            LOGGER.info("Missing `redis.properties` configuration file -> using default RedisOidcTokenCacheConfiguration");
            return RedisOidcTokenCacheConfiguration.DEFAULT;
        }
    }
}
