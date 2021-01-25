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

package org.apache.james.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraEventDeadLettersDAOTest {

    @RegisterExtension
    static CassandraClusterExtension cassandraClusterExtension = new CassandraClusterExtension(CassandraEventDeadLettersModule.MODULE);

    private CassandraEventDeadLettersDAO cassandraEventDeadLettersDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandraCluster) {
        EventSerializer eventSerializer = new EventSerializer(new TestId.Factory(), new TestMessageId.Factory(), new DefaultUserQuotaRootResolver.DefaultQuotaRootDeserializer());
        cassandraEventDeadLettersDAO = new CassandraEventDeadLettersDAO(cassandraCluster.getConf(), eventSerializer);
    }

    @Test
    void removeEventShouldSucceededWhenRemoveStoredEvent() {
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_A, EventDeadLettersContract.EVENT_1, EventDeadLettersContract.INSERTION_ID_1).block();

        cassandraEventDeadLettersDAO.removeEvent(EventDeadLettersContract.GROUP_A, EventDeadLettersContract.INSERTION_ID_1).block();

        assertThat(cassandraEventDeadLettersDAO
                .retrieveInsertionIdsWithGroup(EventDeadLettersContract.GROUP_A)
                .collectList().block())
            .isEmpty();
    }

    @Test
    void retrieveFailedEventShouldReturnEmptyWhenDefault() {
        assertThat(cassandraEventDeadLettersDAO
                .retrieveFailedEvent(EventDeadLettersContract.GROUP_A, EventDeadLettersContract.INSERTION_ID_1)
                .blockOptional().isPresent())
            .isFalse();
    }

    @Test
    void retrieveFailedEventShouldReturnStoredEvent() {
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_A, EventDeadLettersContract.EVENT_1, EventDeadLettersContract.INSERTION_ID_1).block();
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.EVENT_2, EventDeadLettersContract.INSERTION_ID_2).block();

        assertThat(cassandraEventDeadLettersDAO
                .retrieveFailedEvent(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.INSERTION_ID_2)
                .blockOptional().get())
            .isEqualTo(EventDeadLettersContract.EVENT_2);
    }

    @Test
    void retrieveInsertionIdsWithGroupShouldReturnEmptyWhenDefault() {
        assertThat(cassandraEventDeadLettersDAO
                .retrieveInsertionIdsWithGroup(EventDeadLettersContract.GROUP_A)
                .collectList().block())
            .isEmpty();
    }

    @Test
    void retrieveInsertionIdsWithGroupShouldReturnStoredInsertionId() {
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.EVENT_1, EventDeadLettersContract.INSERTION_ID_1).block();
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.EVENT_2, EventDeadLettersContract.INSERTION_ID_2).block();
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.EVENT_3, EventDeadLettersContract.INSERTION_ID_3).block();

        assertThat(cassandraEventDeadLettersDAO
                .retrieveInsertionIdsWithGroup(EventDeadLettersContract.GROUP_B)
                .collectList().block())
            .containsOnly(EventDeadLettersContract.INSERTION_ID_1, EventDeadLettersContract.INSERTION_ID_2, EventDeadLettersContract.INSERTION_ID_3);
    }

    @Test
    void shouldReturnTrueWhenEventStored() {
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.EVENT_1, EventDeadLettersContract.INSERTION_ID_1).block();
        assertThat(cassandraEventDeadLettersDAO.containEvents().block()).isTrue();
    }

    @Test
    void shouldReturnTrueWhenNoEventStored() {
        assertThat(cassandraEventDeadLettersDAO.containEvents().block()).isFalse();
    }

    @Test
    void shouldReturnTrueWhenEventsStoredAndRemovedSome() {
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.EVENT_1, EventDeadLettersContract.INSERTION_ID_1).block();
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.EVENT_1, EventDeadLettersContract.INSERTION_ID_2).block();
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.EVENT_1, EventDeadLettersContract.INSERTION_ID_3).block();

        assertThat(cassandraEventDeadLettersDAO.containEvents().block()).isTrue();

        cassandraEventDeadLettersDAO.removeEvent(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.INSERTION_ID_3).block();

        assertThat(cassandraEventDeadLettersDAO.containEvents().block()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenRemovedAllEventsStored() {
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.EVENT_1, EventDeadLettersContract.INSERTION_ID_1).block();
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.EVENT_1, EventDeadLettersContract.INSERTION_ID_2).block();
        cassandraEventDeadLettersDAO.store(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.EVENT_1, EventDeadLettersContract.INSERTION_ID_3).block();

        assertThat(cassandraEventDeadLettersDAO.containEvents().block()).isTrue();

        cassandraEventDeadLettersDAO.removeEvent(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.INSERTION_ID_3).block();
        cassandraEventDeadLettersDAO.removeEvent(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.INSERTION_ID_2).block();
        cassandraEventDeadLettersDAO.removeEvent(EventDeadLettersContract.GROUP_B, EventDeadLettersContract.INSERTION_ID_1).block();

        assertThat(cassandraEventDeadLettersDAO.containEvents().block()).isFalse();
    }

}
