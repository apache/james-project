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

package org.apache.james;

import java.time.Duration;
import java.util.Optional;

import org.apache.james.backends.opensearch.DockerOpenSearch;
import org.apache.james.backends.opensearch.DockerOpenSearchSingleton;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

public class DockerOpenSearchExtension implements GuiceModuleTestExtension {

    private final DockerOpenSearch dockerOpenSearch;
    private Optional<Duration> requestTimeout;

    public DockerOpenSearchExtension() {
        this(DockerOpenSearchSingleton.INSTANCE);
    }

    public DockerOpenSearchExtension withRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = Optional.of(requestTimeout);
        return this;
    }

    public DockerOpenSearchExtension(DockerOpenSearch dockerOpenSearch) {
        this.dockerOpenSearch = dockerOpenSearch;
        requestTimeout = Optional.empty();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        getDockerOS().start();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        if (!getDockerOS().isRunning()) {
            getDockerOS().unpause();
        }
        await();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        getDockerOS().cleanUpData();
    }

    @Override
    public Module getModule() {
        return binder -> binder.bind(OpenSearchConfiguration.class)
            .toInstance(getOpenSearchConfigurationForDocker());
    }

    @Override
    public void await() {
        getDockerOS().flushIndices();
    }

    private OpenSearchConfiguration getOpenSearchConfigurationForDocker() {
        return OpenSearchConfiguration.builder()
            .addHost(getDockerOS().getHttpHost())
            .requestTimeout(requestTimeout)
            .build();
    }

    public DockerOpenSearch getDockerOS() {
        return dockerOpenSearch;
    }
}
