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

package org.apache.james.backends.es.v6;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.james.util.docker.DockerGenericContainer;
import org.apache.james.util.docker.Images;
import org.awaitility.Awaitility;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientProviderImplConnectionTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProviderImplConnectionTest.class);
    private static final int ES_APPLICATIVE_PORT = 9200;

    @ClassRule
    public static DockerGenericContainer es1 = new DockerGenericContainer(Images.ELASTICSEARCH_6)
        .withEnv("discovery.type", "single-node")
        .withAffinityToContainer()
        .withExposedPorts(ES_APPLICATIVE_PORT);

    @Rule
    public DockerGenericContainer es2 = new DockerGenericContainer(Images.ELASTICSEARCH_6)
        .withEnv("discovery.type", "single-node")
        .withAffinityToContainer()
        .withExposedPorts(ES_APPLICATIVE_PORT);

    @Test
    public void connectingASingleServerShouldWork() {
        Awaitility.await()
            .atMost(1, TimeUnit.MINUTES)
            .pollInterval(5, TimeUnit.SECONDS)
            .until(() -> isConnected(ClientProviderImpl.forHost(es1.getContainerIp(), ES_APPLICATIVE_PORT, Optional.empty())));
    }

    @Test
    public void connectingAClusterShouldWork() {
        Awaitility.await()
            .atMost(1, TimeUnit.MINUTES)
            .pollInterval(5, TimeUnit.SECONDS)
            .until(() -> isConnected(
                ClientProviderImpl.fromHostsString(
                    es1.getContainerIp() + ":" + ES_APPLICATIVE_PORT + ","
                        + es2.getContainerIp() + ":" + ES_APPLICATIVE_PORT,
                    Optional.empty())));
    }

    @Test
    public void connectingAClusterWithAFailedNodeShouldWork() {
        String es1Ip = es1.getContainerIp();
        String es2Ip = es2.getContainerIp();
        es2.stop();

        Awaitility.await()
            .atMost(1, TimeUnit.MINUTES)
            .pollInterval(5, TimeUnit.SECONDS)
            .until(() -> isConnected(
                ClientProviderImpl.fromHostsString(
                    es1Ip + ":" + ES_APPLICATIVE_PORT + ","
                        + es2Ip + ":" + ES_APPLICATIVE_PORT,
                    Optional.empty())));
    }

    private boolean isConnected(ClientProvider clientProvider) {
        try (RestHighLevelClient client = clientProvider.get()) {
            client.search(
                new SearchRequest()
                    .source(new SearchSourceBuilder().query(QueryBuilders.existsQuery("any"))),
                RequestOptions.DEFAULT);
            return true;
        } catch (Exception e) {
            LOGGER.info("Caught exception while trying to connect", e);
            return false;
        }
    }
}
