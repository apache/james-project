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

package org.apache.james.eventsourcing.eventstore.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.TestEvent;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.EventStoreContract;
import org.apache.james.eventsourcing.eventstore.History;
import org.apache.james.eventsourcing.eventstore.dto.SnapshotEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import reactor.core.publisher.Mono;

@ExtendWith(PostgresEventStoreExtensionForTestEvents.class)
public class PostgresEventStoreTest implements EventStoreContract {
    @Test
    void getEventsOfAggregateShouldResumeFromSnapshot(EventStore testee) {
        Event event1 = new TestEvent(EventId.first(), EventStoreContract.AGGREGATE_1(), "first");
        Event event2 = new SnapshotEvent(EventId.first().next(), EventStoreContract.AGGREGATE_1(), "second");
        Event event3 = new TestEvent(EventId.first().next().next(), EventStoreContract.AGGREGATE_1(), "third");

        Mono.from(testee.append(event1)).block();
        Mono.from(testee.append(event2)).block();
        Mono.from(testee.append(event3)).block();

        assertThat(Mono.from(testee.getEventsOfAggregate(EventStoreContract.AGGREGATE_1())).block())
            .isEqualTo(History.of(event2, event3));
    }

    @Test
    void getEventsOfAggregateShouldResumeFromLatestSnapshot(EventStore testee) {
        Event event1 = new SnapshotEvent(EventId.first(), EventStoreContract.AGGREGATE_1(), "first");
        Event event2 = new TestEvent(EventId.first().next(), EventStoreContract.AGGREGATE_1(), "second");
        Event event3 = new SnapshotEvent(EventId.first().next().next(), EventStoreContract.AGGREGATE_1(), "third");

        Mono.from(testee.append(event1)).block();
        Mono.from(testee.append(event2)).block();
        Mono.from(testee.append(event3)).block();

        assertThat(Mono.from(testee.getEventsOfAggregate(EventStoreContract.AGGREGATE_1())).block())
            .isEqualTo(History.of(event3));
    }
}