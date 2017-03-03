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

import java.util.concurrent.TimeUnit;

import org.apache.james.util.streams.SwarmGenericContainer;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.awaitility.Awaitility;

@Ignore("JAMES-1952")
public class ClientProviderImplConnectionTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProviderImplConnectionTest.class);
    private static final String DOCKER_ES_IMAGE = "elasticsearch:2.2.1";

    @Rule
    public SwarmGenericContainer es1 = new SwarmGenericContainer(DOCKER_ES_IMAGE)
        .withAffinityToContainer();

    @Rule
    public SwarmGenericContainer es2 = new SwarmGenericContainer(DOCKER_ES_IMAGE)
        .withAffinityToContainer();

    @Test
    public void connectingASingleServerShouldWork() throws Exception {
        Awaitility.await()
            .atMost(1, TimeUnit.MINUTES)
            .pollInterval(5, TimeUnit.SECONDS)
            .until(() -> isConnected(ClientProviderImpl.forHost(es1.getIp(), 9300)));
    }

    @Test
    public void connectingAClusterShouldWork() throws Exception {
        Awaitility.await()
            .atMost(1, TimeUnit.MINUTES)
            .pollInterval(5, TimeUnit.SECONDS)
            .until(() ->isConnected(
                ClientProviderImpl.fromHostsString(
                    es1.getIp() + ":" + 9300 + ","
                    + es2.getIp() + ":" + 9300)));
    }

    @Test
    public void connectingAClusterWithAFailedNodeShouldWork() throws Exception {
        es2.stop();

        Awaitility.await()
            .atMost(1, TimeUnit.MINUTES)
            .pollInterval(5, TimeUnit.SECONDS)
            .until(() -> isConnected(
                ClientProviderImpl.fromHostsString(
                    es1.getIp() + ":" + 9300 + ","
                    + es2.getIp() + ":" + 9300)));
    }

    private boolean isConnected(ClientProvider clientProvider) {
        try (Client client = clientProvider.get()) {
            client.prepareSearch()
                .setQuery(QueryBuilders.existsQuery("any"))
                .get();
            return true;
        } catch (Exception e) {
            LOGGER.info("Caught exception while trying to connect", e);
            return false;
        }
    }
}
