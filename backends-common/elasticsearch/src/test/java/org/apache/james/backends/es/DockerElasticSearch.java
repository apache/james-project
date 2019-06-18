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

package org.apache.james.backends.es;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.http.HttpStatus;
import org.apache.james.util.Host;
import org.apache.james.util.docker.DockerGenericContainer;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.RateLimiters;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import feign.Feign;
import feign.Logger;
import feign.RequestLine;
import feign.Response;
import feign.slf4j.Slf4jLogger;

public class DockerElasticSearch {

    interface ElasticSearchAPI {

        static ElasticSearchAPI from(Host esHttpHost) {
            return Feign.builder()
                .logger(new Slf4jLogger(ElasticSearchAPI.class))
                .logLevel(Logger.Level.FULL)
                .target(ElasticSearchAPI.class, "http://" + esHttpHost.getHostName() + ":" + esHttpHost.getPort());
        }

        @RequestLine("DELETE /_all")
        Response deleteAllIndexes();

        @RequestLine("POST /_flush?force&wait_if_ongoing=true")
        Response flush();
    }

    private static final int ES_HTTP_PORT = 9200;

    private final DockerGenericContainer eSContainer;

    public DockerElasticSearch() {
        this(Images.ELASTICSEARCH_6);
    }

    public DockerElasticSearch(String imageName) {
        this.eSContainer = new DockerGenericContainer(imageName)
            .withTmpFs(ImmutableMap.of("/usr/share/elasticsearch/data", "rw,size=200m"))
            .withExposedPorts(ES_HTTP_PORT)
            .withEnv("discovery.type", "single-node")
            .withAffinityToContainer()
            .waitingFor(new HostPortWaitStrategy().withRateLimiter(RateLimiters.TWENTIES_PER_SECOND));
    }

    public void start() {
        if (!eSContainer.isRunning()) {
            eSContainer.start();
        }
    }

    public void stop() {
        eSContainer.stop();
    }

    public int getHttpPort() {
        return eSContainer.getMappedPort(ES_HTTP_PORT);
    }

    public String getIp() {
        return eSContainer.getHostIp();
    }

    public Host getHttpHost() {
        return Host.from(getIp(), getHttpPort());
    }

    public void pause() {
        eSContainer.pause();
    }

    public void unpause() {
        eSContainer.unpause();
    }

    public void cleanUpData() {
        assertThat(esAPI().deleteAllIndexes().status())
            .isEqualTo(HttpStatus.SC_OK);
    }

    public void awaitForElasticSearch() {
        assertThat(esAPI().flush().status())
            .isEqualTo(HttpStatus.SC_OK);
    }

    public ClientProvider clientProvider() {
        Optional<String> noClusterName = Optional.empty();
        return ClientProviderImpl.fromHosts(ImmutableList.of(getHttpHost()), noClusterName);
    }

    private ElasticSearchAPI esAPI() {
        return ElasticSearchAPI.from(getHttpHost());
    }
}
