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

package org.apache.james.mailbox.events;

import static org.apache.james.mailbox.events.EventDeadLettersContract.EVENT_1;
import static org.apache.james.mailbox.events.EventDeadLettersContract.EVENT_2;
import static org.apache.james.mailbox.events.EventDeadLettersContract.EVENT_3;
import static org.apache.james.mailbox.events.EventDeadLettersContract.GROUP_A;
import static org.apache.james.mailbox.events.EventDeadLettersContract.GROUP_B;
import static org.apache.james.mailbox.events.EventDeadLettersContract.INSERTION_ID_1;
import static org.apache.james.mailbox.events.EventDeadLettersContract.INSERTION_ID_2;
import static org.apache.james.mailbox.events.EventDeadLettersContract.INSERTION_ID_3;
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
        cassandraEventDeadLettersDAO.store(GROUP_A, EVENT_1, INSERTION_ID_1).block();

        cassandraEventDeadLettersDAO.removeEvent(GROUP_A, INSERTION_ID_1).block();

        assertThat(cassandraEventDeadLettersDAO
                .retrieveInsertionIdsWithGroup(GROUP_A)
                .collectList().block())
            .isEmpty();
    }

    @Test
    void retrieveFailedEventShouldReturnEmptyWhenDefault() {
        assertThat(cassandraEventDeadLettersDAO
                .retrieveFailedEvent(GROUP_A, INSERTION_ID_1)
                .blockOptional().isPresent())
            .isFalse();
    }

    @Test
    void retrieveFailedEventShouldReturnStoredEvent() {
        cassandraEventDeadLettersDAO.store(GROUP_A, EVENT_1, INSERTION_ID_1).block();
        cassandraEventDeadLettersDAO.store(GROUP_B, EVENT_2, INSERTION_ID_2).block();

        assertThat(cassandraEventDeadLettersDAO
                .retrieveFailedEvent(GROUP_B, INSERTION_ID_2)
                .blockOptional().get())
            .isEqualTo(EVENT_2);
    }

    @Test
    void retrieveInsertionIdsWithGroupShouldReturnEmptyWhenDefault() {
        assertThat(cassandraEventDeadLettersDAO
                .retrieveInsertionIdsWithGroup(GROUP_A)
                .collectList().block())
            .isEmpty();
    }

    @Test
    void retrieveInsertionIdsWithGroupShouldReturnStoredInsertionId() {
        cassandraEventDeadLettersDAO.store(GROUP_B, EVENT_1, INSERTION_ID_1).block();
        cassandraEventDeadLettersDAO.store(GROUP_B, EVENT_2, INSERTION_ID_2).block();
        cassandraEventDeadLettersDAO.store(GROUP_B, EVENT_3, INSERTION_ID_3).block();

        assertThat(cassandraEventDeadLettersDAO
                .retrieveInsertionIdsWithGroup(GROUP_B)
                .collectList().block())
            .containsOnly(INSERTION_ID_1, INSERTION_ID_2, INSERTION_ID_3);
    }

    @Test
    void shouldReturnTrueWhenEventStored() {
        cassandraEventDeadLettersDAO.store(GROUP_B, EVENT_1, INSERTION_ID_1).block();
        assertThat(cassandraEventDeadLettersDAO.containEvents().block()).isTrue();
    }

    @Test
    void shouldReturnTrueWhenNoEventStored() {
        assertThat(cassandraEventDeadLettersDAO.containEvents().block()).isFalse();
    }

    @Test
    void shouldReturnTrueWhenEventsStoredAndRemovedSome() {
        cassandraEventDeadLettersDAO.store(GROUP_B, EVENT_1, INSERTION_ID_1).block();
        cassandraEventDeadLettersDAO.store(GROUP_B, EVENT_1, INSERTION_ID_2).block();
        cassandraEventDeadLettersDAO.store(GROUP_B, EVENT_1, INSERTION_ID_3).block();

        assertThat(cassandraEventDeadLettersDAO.containEvents().block()).isTrue();

        cassandraEventDeadLettersDAO.removeEvent(GROUP_B, INSERTION_ID_3).block();

        assertThat(cassandraEventDeadLettersDAO.containEvents().block()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenRemovedAllEventsStored() {
        cassandraEventDeadLettersDAO.store(GROUP_B, EVENT_1, INSERTION_ID_1).block();
        cassandraEventDeadLettersDAO.store(GROUP_B, EVENT_1, INSERTION_ID_2).block();
        cassandraEventDeadLettersDAO.store(GROUP_B, EVENT_1, INSERTION_ID_3).block();

        assertThat(cassandraEventDeadLettersDAO.containEvents().block()).isTrue();

        cassandraEventDeadLettersDAO.removeEvent(GROUP_B, INSERTION_ID_3).block();
        cassandraEventDeadLettersDAO.removeEvent(GROUP_B, INSERTION_ID_2).block();
        cassandraEventDeadLettersDAO.removeEvent(GROUP_B, INSERTION_ID_1).block();

        assertThat(cassandraEventDeadLettersDAO.containEvents().block()).isFalse();
    }

}
