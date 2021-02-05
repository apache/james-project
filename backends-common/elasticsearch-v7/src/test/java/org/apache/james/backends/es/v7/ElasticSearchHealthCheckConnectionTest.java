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

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;

class ElasticSearchHealthCheckConnectionTest {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    @RegisterExtension
    public DockerElasticSearchExtension elasticSearch = new DockerElasticSearchExtension();
    private ElasticSearchHealthCheck elasticSearchHealthCheck;

    @BeforeEach
    void setUp() {
        ReactorElasticSearchClient client = elasticSearch.getDockerElasticSearch().clientProvider(REQUEST_TIMEOUT).get();

        elasticSearchHealthCheck = new ElasticSearchHealthCheck(client, ImmutableSet.of());
    }

    @Test
    void checkShouldSucceedWhenElasticSearchIsRunning() {
        assertThat(elasticSearchHealthCheck.check().block().isHealthy()).isTrue();
    }

    @Test
    void checkShouldFailWhenElasticSearchIsPaused() {

        elasticSearch.getDockerElasticSearch().pause();

        try {
            assertThat(elasticSearchHealthCheck.check().block().isUnHealthy()).isTrue();
        } finally {
            elasticSearch.getDockerElasticSearch().unpause();
        }
    }
}
