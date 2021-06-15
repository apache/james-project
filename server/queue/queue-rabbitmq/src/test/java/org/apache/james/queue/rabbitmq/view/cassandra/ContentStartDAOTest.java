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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

class ContentStartDAOTest {

    private static final MailQueueName OUT_GOING_1 = MailQueueName.fromString("OUT_GOING_1");
    private static final MailQueueName OUT_GOING_2 = MailQueueName.fromString("OUT_GOING_2");
    private static final Instant NOW = Instant.now();
    private static final Instant NOW_PLUS_TEN_SECONDS = NOW.plusSeconds(10);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
            CassandraModule.aggregateModules(CassandraSchemaVersionModule.MODULE,CassandraMailQueueViewModule.MODULE));

    private ContentStartDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new ContentStartDAO(cassandra.getConf());
    }

    @Test
    void findContentStartShouldReturnEmptyWhenTableDoesntContainQueueName() {
        testee.updateContentStart(OUT_GOING_1, NOW).block();

        Mono<Instant> firstEnqueuedItemFromQueue2 = testee.findContentStart(OUT_GOING_2);
        assertThat(firstEnqueuedItemFromQueue2.flux().collectList().block())
            .isEmpty();
    }

    @Test
    void findContentStartShouldReturnInstantWhenTableContainsQueueName() {
        testee.updateContentStart(OUT_GOING_1, NOW).block();
        testee.updateContentStart(OUT_GOING_2, NOW).block();

        Mono<Instant> firstEnqueuedItemFromQueue2 = testee.findContentStart(OUT_GOING_2);
        assertThat(firstEnqueuedItemFromQueue2.flux().collectList().block())
            .isNotEmpty();
    }

    @Test
    void updateFirstEnqueuedTimeShouldWork() {
        testee.updateContentStart(OUT_GOING_1, NOW).block();

        assertThat(testee.selectOne(OUT_GOING_1).flux().collectList().block())
            .isNotEmpty();
    }

    @Test
    void insertInitialContentStartShouldInsertFirstInstant() {
        testee.insertInitialContentStart(OUT_GOING_1, NOW).block();
        testee.insertInitialContentStart(OUT_GOING_1, NOW_PLUS_TEN_SECONDS).block();

        assertThat(testee.findContentStart(OUT_GOING_1).flux().collectList().block())
            .contains(NOW.truncatedTo(ChronoUnit.MILLIS));
    }
}