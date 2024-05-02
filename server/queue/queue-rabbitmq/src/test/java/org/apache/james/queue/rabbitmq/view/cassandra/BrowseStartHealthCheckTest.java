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

package org.apache.james.queue.rabbitmq.view.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

class BrowseStartHealthCheckTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraSchemaVersionModule.MODULE,CassandraMailQueueViewModule.MODULE));

    private BrowseStartHealthCheck testee;
    private BrowseStartDAO browseStartDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        browseStartDAO = new BrowseStartDAO(cassandra.getConf());
        testee = new BrowseStartHealthCheck(browseStartDAO, Clock.systemUTC());
    }

    @Test
    void checkShouldReturnHealthyWhenEmpty() {
        assertThat(Mono.from(testee.check()).block().getStatus())
            .isEqualTo(ResultStatus.HEALTHY);
    }

    @Test
    void checkShouldReturnHealthyWhenSingleValue() {
        browseStartDAO.insertInitialBrowseStart(MailQueueName.fromString("abc"),
            Clock.systemUTC().instant().minus(Duration.ofDays(6))).block();

        assertThat(Mono.from(testee.check()).block().getStatus())
            .isEqualTo(ResultStatus.HEALTHY);
    }

    @Test
    void checkShouldReturnHealthyWhenSingleFutureValue() {
        browseStartDAO.insertInitialBrowseStart(MailQueueName.fromString("abc"),
            Clock.systemUTC().instant().plus(Duration.ofDays(6))).block();

        assertThat(Mono.from(testee.check()).block().getStatus())
            .isEqualTo(ResultStatus.HEALTHY);
    }

    @Test
    void checkShouldReturnDegradedWhenSingleOldValue() {
        browseStartDAO.insertInitialBrowseStart(MailQueueName.fromString("abc"),
            Clock.systemUTC().instant().minus(Duration.ofDays(8))).block();

        assertThat(Mono.from(testee.check()).block().getStatus())
            .isEqualTo(ResultStatus.DEGRADED);
    }

    @Test
    void checkShouldReturnDegradedWhenMixed() {
        browseStartDAO.insertInitialBrowseStart(MailQueueName.fromString("abc"),
            Clock.systemUTC().instant().minus(Duration.ofDays(8))).block();
        browseStartDAO.insertInitialBrowseStart(MailQueueName.fromString("abc"),
            Clock.systemUTC().instant().minus(Duration.ofDays(6))).block();

        assertThat(Mono.from(testee.check()).block().getStatus())
            .isEqualTo(ResultStatus.DEGRADED);
    }
}