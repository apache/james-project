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

public class KvrocksExtension implements GuiceModuleTestExtension {
    private static final DockerKvrocks DOCKER_KVROCKS_SINGLETON = new DockerKvrocks();

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        DOCKER_KVROCKS_SINGLETON.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        DOCKER_KVROCKS_SINGLETON.stop();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        DOCKER_KVROCKS_SINGLETON.flushAll();
    }

    @Override
    public Module getModule() {
        return new AbstractModule() {
            @Provides
            @Singleton
            public RedisConfiguration provideConfig() {
                return StandaloneRedisConfiguration.from(dockerKvrocks().redisURI().toString());
            }
        };
    }

    public DockerKvrocks dockerKvrocks() {
        return DOCKER_KVROCKS_SINGLETON;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == DockerKvrocks.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return dockerKvrocks();
    }
}
