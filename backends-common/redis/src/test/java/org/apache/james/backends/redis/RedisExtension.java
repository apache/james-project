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

import jakarta.inject.Singleton;

import org.apache.james.GuiceModuleTestExtension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

public class RedisExtension implements GuiceModuleTestExtension {
    private static final DockerRedis DOCKER_REDIS_SINGLETON = new DockerRedis();

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        DOCKER_REDIS_SINGLETON.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        DOCKER_REDIS_SINGLETON.stop();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        DOCKER_REDIS_SINGLETON.flushAll();
    }

    @Override
    public Module getModule() {
        return new AbstractModule() {
            @Provides
            @Singleton
            public  RedisConfiguration provideConfig() {
                return RedisConfiguration.from(dockerRedis().redisURI().toString(), false);
            }
        };
    }

    public DockerRedis dockerRedis() {
        return DOCKER_REDIS_SINGLETON;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == DockerRedis.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return dockerRedis();
    }
}
