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

package org.apache.james.eventsourcing.eventstore;

import static org.apache.james.eventsourcing.TestAggregateId.testId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.TestAggregateId;
import org.apache.james.eventsourcing.TestEvent;
import org.junit.jupiter.api.Test;

public interface EventStoreTest {

    TestAggregateId AGGREGATE_1 = testId(1);
    TestAggregateId AGGREGATE_2 = testId(2);

    @Test
    default void getEventsOfAggregateShouldThrowOnNullAggregateId(EventStore testee) {
        assertThatThrownBy(() -> testee.getEventsOfAggregate(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void appendShouldThrowWhenEventFromSeveralAggregates(EventStore testee) {
        TestEvent event1 = new TestEvent(EventId.first(), AGGREGATE_1, "first");
        TestEvent event2 = new TestEvent(event1.eventId().next(), AGGREGATE_2, "second");
        assertThatThrownBy(() -> testee.appendAll(event1, event2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void appendShouldDoNothingOnEmptyEventList(EventStore testee) {
        assertThatCode(testee::appendAll).doesNotThrowAnyException();
    }

    @Test
    default void appendShouldThrowWhenTryingToRewriteHistory(EventStore testee) {
        TestEvent event1 = new TestEvent(EventId.first(), AGGREGATE_1, "first");
        testee.append(event1);
        TestEvent event2 = new TestEvent(EventId.first(), AGGREGATE_1, "second");
        assertThatThrownBy(() -> testee.append(event2)).isInstanceOf(EventStoreFailedException.class);
    }

    @Test
    default void getEventsOfAggregateShouldReturnEmptyHistoryWhenUnknown(EventStore testee) {
        assertThat(testee.getEventsOfAggregate(AGGREGATE_1)).isEqualTo(History.empty());
    }

    @Test
    default void getEventsOfAggregateShouldReturnAppendedEvent(EventStore testee) {
        TestEvent event = new TestEvent(EventId.first(), AGGREGATE_1, "first");
        testee.append(event);
        assertThat(testee.getEventsOfAggregate(AGGREGATE_1))
            .isEqualTo(History.of(event));
    }

    @Test
    default void getEventsOfAggregateShouldReturnAppendedEvents(EventStore testee) {
        TestEvent event1 = new TestEvent(EventId.first(), AGGREGATE_1, "first");
        TestEvent event2 = new TestEvent(event1.eventId().next(), AGGREGATE_1, "second");
        testee.append(event1);
        testee.append(event2);
        assertThat(testee.getEventsOfAggregate(AGGREGATE_1))
            .isEqualTo(History.of(event1, event2));
    }

}