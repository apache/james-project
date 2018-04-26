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

package org.apache.james.eventsourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class HistoryTest {

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(EventStore.History.class)
            .verify();
    }

    @Test
    public void emptyShouldGenerateAnEmptyHistory() {
        assertThat(EventStore.History.empty())
            .isEqualTo(EventStore.History.of());
    }

    @Test
    public void getVersionShouldReturnEmptyWhenEmpty() {
        assertThat(EventStore.History.empty()
            .getVersion())
            .isEmpty();
    }

    @Test
    public void getVersionShouldReturnSingleEventIdWhenSingleEvent() {
        assertThat(EventStore.History
            .of(new TestEvent(EventId.first(),
                TestAggregateId.testId(42),
                "any"))
            .getVersion())
            .contains(EventId.first());
    }

    @Test
    public void getVersionShouldReturnHighestEventId() {
        TestEvent event1 = new TestEvent(EventId.first(),
            TestAggregateId.testId(42),
            "any");
        TestEvent event2 = new TestEvent(event1.eventId().next(),
            TestAggregateId.testId(42),
            "any");

        assertThat(EventStore.History.of(event1, event2)
            .getVersion())
            .contains(event2.eventId());
    }

    @Test
    public void duplicateHistoryShouldThrow() {
        TestEvent event1 = new TestEvent(EventId.first(),
            TestAggregateId.testId(42),
            "any");
        TestEvent event2 = new TestEvent(EventId.first(),
            TestAggregateId.testId(42),
            "any");

        assertThatThrownBy(() -> EventStore.History.of(event1, event2))
            .isInstanceOf(EventStore.EventStoreFailedException.class);
    }

}
