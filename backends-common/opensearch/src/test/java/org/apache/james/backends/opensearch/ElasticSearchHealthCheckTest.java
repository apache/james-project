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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch.cluster.HealthResponse;

import com.google.common.collect.ImmutableSet;

class ElasticSearchHealthCheckTest {
    private static HealthResponse fakeHealthResponse(HealthStatus status) {
        return new HealthResponse.Builder()
            .clusterName("fake-cluster")
            .activePrimaryShards(0)
            .activeShards(0)
            .activeShardsPercentAsNumber("0")
            .delayedUnassignedShards(0)
            .initializingShards(0)
            .numberOfDataNodes(0)
            .numberOfInFlightFetch(0)
            .numberOfNodes(0)
            .numberOfPendingTasks(0)
            .relocatingShards(0)
            .taskMaxWaitingInQueueMillis(String.valueOf(System.currentTimeMillis()))
            .timedOut(false)
            .unassignedShards(0)
            .status(status)
            .build();
    }

    private ElasticSearchHealthCheck healthCheck;

    @BeforeEach
    void setup() {
        healthCheck = new ElasticSearchHealthCheck(null, ImmutableSet.of());
    }

    @Test
    void checkShouldReturnHealthyWhenElasticSearchClusterHealthStatusIsGreen() {
        HealthResponse response = fakeHealthResponse(HealthStatus.Green);

        assertThat(healthCheck.toHealthCheckResult(response).isHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnUnHealthyWhenElasticSearchClusterHealthStatusIsRed() {
        HealthResponse response = fakeHealthResponse(HealthStatus.Red);

        assertThat(healthCheck.toHealthCheckResult(response).isUnHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnHealthyWhenElasticSearchClusterHealthStatusIsYellow() {
        HealthResponse response = fakeHealthResponse(HealthStatus.Yellow);

        assertThat(healthCheck.toHealthCheckResult(response).isHealthy()).isTrue();
    }
}
