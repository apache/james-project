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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.TestAggregateId;
import org.apache.james.eventsourcing.TestEvent;
import org.junit.jupiter.api.Test;

import scala.compat.java8.OptionConverters;

class HistoryTest {

    @Test
    void emptyShouldGenerateAnEmptyHistory() {
        assertThat(History.empty())
            .isEqualTo(History.of());
    }

    @Test
    void getVersionShouldReturnEmptyWhenEmpty() {
        assertThat(OptionConverters.toJava(History.empty()
            .getVersion()))
            .isEmpty();
    }

    @Test
    void getVersionShouldReturnSingleEventIdWhenSingleEvent() {
        assertThat(OptionConverters.toJava(History
            .of(new TestEvent(EventId.first(),
                TestAggregateId.testId(42),
                "any"))
            .getVersion()))
            .contains(EventId.first());
    }

    @Test
    void getVersionShouldReturnHighestEventId() {
        TestEvent event1 = new TestEvent(EventId.first(),
            TestAggregateId.testId(42),
            "any");
        TestEvent event2 = new TestEvent(event1.eventId().next(),
            TestAggregateId.testId(42),
            "any");

        assertThat(OptionConverters.toJava(History.of(event1, event2)
            .getVersion()))
            .contains(event2.eventId());
    }

    @Test
    void duplicateHistoryShouldThrow() {
        TestEvent event1 = new TestEvent(EventId.first(),
            TestAggregateId.testId(42),
            "any");
        TestEvent event2 = new TestEvent(EventId.first(),
            TestAggregateId.testId(42),
            "any");

        assertThatThrownBy(() -> History.of(event1, event2))
            .isInstanceOf(EventStoreFailedException.class);
    }

}
