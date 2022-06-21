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

package org.apache.james.backends.opensearch;

import java.util.concurrent.TimeUnit;

import org.apache.james.backends.opensearch.ElasticSearchClusterExtension.ElasticSearchCluster;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

interface ClientProviderImplConnectionContract {

    Logger LOGGER = LoggerFactory.getLogger(ClientProviderImplConnectionContract.class);

    @Test
    default void connectingASingleServerShouldWork(ElasticSearchCluster esCluster) {
        ElasticSearchConfiguration configuration = configurationBuilder()
            .addHost(esCluster.es1.getHttpHost())
            .build();

        Awaitility.await()
            .atMost(1, TimeUnit.MINUTES)
            .pollInterval(5, TimeUnit.SECONDS)
            .until(() -> isConnected(new ClientProvider(configuration)));
    }

    @Test
    default void connectingAClusterShouldWork(ElasticSearchCluster esCluster) {
        ElasticSearchConfiguration configuration = configurationBuilder()
            .addHosts(esCluster.getHosts())
            .build();

        Awaitility.await()
            .atMost(1, TimeUnit.MINUTES)
            .pollInterval(5, TimeUnit.SECONDS)
            .until(() -> isConnected(new ClientProvider(configuration)));
    }

    @Test
    default void connectingAClusterWithAFailedNodeShouldWork(ElasticSearchCluster esCluster) {
        ElasticSearchConfiguration configuration = configurationBuilder()
            .addHosts(esCluster.getHosts())
            .build();

        esCluster.es2.stop();

        Awaitility.await()
            .atMost(1, TimeUnit.MINUTES)
            .pollInterval(5, TimeUnit.SECONDS)
            .until(() -> isConnected(new ClientProvider(configuration)));
    }

    default boolean isConnected(ClientProvider clientProvider) {
        try (ReactorElasticSearchClient client = clientProvider.get()) {
            client.search(
                new SearchRequest.Builder()
                    .query(new MatchAllQuery.Builder().build()._toQuery())
                    .build()).block();
            return true;
        } catch (Exception e) {
            LOGGER.info("Caught exception while trying to connect", e);
            return false;
        }
    }

    default ElasticSearchConfiguration.Builder configurationBuilder() {
        return ElasticSearchConfiguration.builder();
    }
}

