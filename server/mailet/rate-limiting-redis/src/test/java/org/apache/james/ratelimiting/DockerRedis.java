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

package org.apache.james.ratelimiting;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.utils.URIBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.sync.RedisCommands;

public class DockerRedis {
    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("redis");
    private static final String DEFAULT_TAG = "6.2.6";
    private static final int DEFAULT_PORT = 6379;

    private final GenericContainer<?> container;

    public DockerRedis() {
        this.container = new GenericContainer<>(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG))
            .withExposedPorts(DEFAULT_PORT);
    }

    public Integer getPort() {
        return container.getMappedPort(DEFAULT_PORT);
    }

    public URI redisURI() {
        try {
            return new URIBuilder()
                .setScheme("redis")
                .setHost(container.getHost())
                .setPort(getPort())
                .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Error when build redis uri. ", e);
        }
    }

    public void start() {
        if (!container.isRunning()) {
            container.start();
        }
    }

    public void stop() {
        container.stop();
    }

    public RedisCommands<String, String> createClient() {
        return RedisClient.create(redisURI().toString())
            .connect().sync();
    }
}
