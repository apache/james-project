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
package org.apache.james.backends.es.v7;

import static org.assertj.core.api.Assertions.assertThat;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlocks;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.RoutingTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class ElasticSearchHealthCheckTest {
    private ElasticSearchHealthCheck healthCheck;

    @BeforeEach
    void setup() {
        healthCheck = new ElasticSearchHealthCheck(null, ImmutableSet.of());
    }

    @Test
    void checkShouldReturnHealthyWhenElasticSearchClusterHealthStatusIsGreen() {
        FakeClusterHealthResponse response = new FakeClusterHealthResponse(ClusterHealthStatus.GREEN);

        assertThat(healthCheck.toHealthCheckResult(response).isHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnUnHealthyWhenElasticSearchClusterHealthStatusIsRed() {
        FakeClusterHealthResponse response = new FakeClusterHealthResponse(ClusterHealthStatus.RED);

        assertThat(healthCheck.toHealthCheckResult(response).isUnHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnHealthyWhenElasticSearchClusterHealthStatusIsYellow() {
        FakeClusterHealthResponse response = new FakeClusterHealthResponse(ClusterHealthStatus.YELLOW);

        assertThat(healthCheck.toHealthCheckResult(response).isHealthy()).isTrue();
    }

    private static class FakeClusterHealthResponse extends ClusterHealthResponse {
        private final ClusterHealthStatus status;

        private FakeClusterHealthResponse(ClusterHealthStatus clusterHealthStatus) {
            super("fake-cluster", new String[0],
                new ClusterState(new ClusterName("fake-cluster"), 0, null, null, RoutingTable.builder().build(),
                    DiscoveryNodes.builder().build(),
                    ClusterBlocks.builder().build(), null, false));
            this.status = clusterHealthStatus;
        }

        @Override
        public ClusterHealthStatus getStatus() {
            return this.status;
        }
    }
}
