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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.queue.rabbitmq.EnqueueId;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DeletedMailsDAOTest {

    private static final MailQueueName OUT_GOING_1 = MailQueueName.fromString("OUT_GOING_1");
    private static final MailQueueName OUT_GOING_2 = MailQueueName.fromString("OUT_GOING_2");
    private static final EnqueueId ENQUEUE_ID_1 = EnqueueId.ofSerialized("110e8400-e29b-11d4-a716-446655440000");
    private static final EnqueueId ENQUEUE_ID_2 = EnqueueId.ofSerialized("464765a0-e4e7-11e4-aba4-710c1de3782b");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraMailQueueViewModule.MODULE));

    private DeletedMailsDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new DeletedMailsDAO(cassandra.getConf());
    }

    @Test
    void markAsDeletedShouldWork() {
        Boolean isDeletedBeforeMark = testee
                .isDeleted(OUT_GOING_1, ENQUEUE_ID_1)
                .block();
        assertThat(isDeletedBeforeMark).isFalse();

        testee.markAsDeleted(OUT_GOING_1, ENQUEUE_ID_1).block();

        Boolean isDeletedAfterMark = testee
            .isDeleted(OUT_GOING_1, ENQUEUE_ID_1)
            .block();

        assertThat(isDeletedAfterMark).isTrue();
    }

    @Test
    void checkDeletedShouldReturnFalseWhenTableDoesntContainBothMailQueueAndMailKey() {
        testee.markAsDeleted(OUT_GOING_2, ENQUEUE_ID_2).block();

        Boolean isDeleted = testee
            .isDeleted(OUT_GOING_1, ENQUEUE_ID_1)
            .block();

        assertThat(isDeleted).isFalse();
    }

    @Test
    void checkDeletedShouldReturnFalseWhenTableContainsMailQueueButNotMailKey() {
        testee.markAsDeleted(OUT_GOING_1, ENQUEUE_ID_2).block();

        Boolean isDeleted = testee
            .isDeleted(OUT_GOING_1, ENQUEUE_ID_1)
            .block();

        assertThat(isDeleted).isFalse();
    }

    @Test
    void checkDeletedShouldReturnFalseWhenTableContainsMailKeyButNotMailQueue() {
        testee.markAsDeleted(OUT_GOING_2, ENQUEUE_ID_1).block();

        Boolean isDeleted = testee
            .isDeleted(OUT_GOING_1, ENQUEUE_ID_1)
            .block();

        assertThat(isDeleted).isFalse();
    }

    @Test
    void checkDeletedShouldReturnTrueWhenTableContainsMailItem() {
        testee.markAsDeleted(OUT_GOING_1, ENQUEUE_ID_1).block();

        Boolean isDeleted = testee
            .isDeleted(OUT_GOING_1, ENQUEUE_ID_1)
            .block();

        assertThat(isDeleted).isTrue();
    }

    @Test
    void isDeletedShouldReturnFalseAfterRemovingDeletedMark() {
        testee.markAsDeleted(OUT_GOING_1, ENQUEUE_ID_1).block();

        testee.removeDeletedMark(OUT_GOING_1, ENQUEUE_ID_1).block();

        Boolean isDeleted = testee
            .isDeleted(OUT_GOING_1, ENQUEUE_ID_1)
            .block();

        assertThat(isDeleted).isFalse();
    }
}