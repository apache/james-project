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

import org.apache.james.core.Username;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.events.EventBusTestFixture.TestEvent;
import org.junit.jupiter.api.Test;

interface EventDeadLettersHealthCheckContract {

    ComponentName COMPONENT_NAME = new ComponentName("EventDeadLettersHealthCheck");
    String EXPECTED_DEGRADED_MESSAGE = "EventDeadLetters contain events. This might indicate transient failure on mailbox event processing.";

    Username USERNAME = Username.of("user");

    Event.EventId EVENT_ID_1 = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");
    Event.EventId EVENT_ID_2 = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b5");
    Event EVENT_1 = new TestEvent(EVENT_ID_1, USERNAME);
    Event EVENT_2 = new TestEvent(EVENT_ID_2, USERNAME);

    Group GROUP_A = new EventBusTestFixture.GroupA();
    Group GROUP_B = new EventBusTestFixture.GroupB();

    EventDeadLettersHealthCheck testee();

    EventDeadLetters eventDeadLetters();

    void createErrorWhenDoingHealthCheck();

    void resolveErrorWhenDoingHealthCheck();

    @Test
    default void checkShouldReturnHealthyWhenEventDeadLetterEmpty() {
        assertThat(testee().check().block().isHealthy()).isTrue();
        assertThat(testee().check().block())
            .isEqualTo(Result.healthy(COMPONENT_NAME));
    }

    @Test
    default void checkShouldReturnDegradedWhenEventDeadLetterContainEvent() {
        eventDeadLetters().store(GROUP_A, EVENT_1).block();

        assertThat(testee().check().block().isDegraded()).isTrue();
        assertThat(testee().check().block())
            .isEqualTo(Result.degraded(COMPONENT_NAME, EXPECTED_DEGRADED_MESSAGE));
    }

    @Test
    default void checkShouldReturnDegradedWhenEventDeadLetterContainEvents() {
        eventDeadLetters().store(GROUP_A, EVENT_1).block();
        eventDeadLetters().store(GROUP_B, EVENT_2).block();

        assertThat(testee().check().block().isDegraded()).isTrue();
        assertThat(testee().check().block())
            .isEqualTo(Result.degraded(COMPONENT_NAME, EXPECTED_DEGRADED_MESSAGE));
    }

    @Test
    default void checkShouldReturnHealthyWhenRemovedAllEventDeadLetters() {
        EventDeadLetters.InsertionId insertionId1 = eventDeadLetters().store(GROUP_A, EVENT_1).block();
        EventDeadLetters.InsertionId insertionId2 = eventDeadLetters().store(GROUP_B, EVENT_2).block();

        assertThat(testee().check().block().isDegraded()).isTrue();
        assertThat(testee().check().block())
            .isEqualTo(Result.degraded(COMPONENT_NAME, EXPECTED_DEGRADED_MESSAGE));

        eventDeadLetters().remove(GROUP_A, insertionId1).block();
        eventDeadLetters().remove(GROUP_B, insertionId2).block();

        assertThat(testee().check().block().isHealthy()).isTrue();
        assertThat(testee().check().block())
            .isEqualTo(Result.healthy(COMPONENT_NAME));
    }

    @Test
    default void checkShouldReturnDegradedWhenRemovedSomeEventDeadLetters() {
        EventDeadLetters.InsertionId insertionId1 = eventDeadLetters().store(GROUP_A, EVENT_1).block();
        eventDeadLetters().store(GROUP_B, EVENT_2).block();

        assertThat(testee().check().block().isDegraded()).isTrue();
        assertThat(testee().check().block())
            .isEqualTo(Result.degraded(COMPONENT_NAME, EXPECTED_DEGRADED_MESSAGE));

        eventDeadLetters().remove(GROUP_A, insertionId1).block();

        assertThat(testee().check().block().isDegraded()).isTrue();
        assertThat(testee().check().block())
            .isEqualTo(Result.degraded(COMPONENT_NAME, EXPECTED_DEGRADED_MESSAGE));
    }

    @Test
    default void checkShouldReturnUnHealthyWhenEventDeadLetterError() {
        Result actualResult;
        try {
            createErrorWhenDoingHealthCheck();
            actualResult = testee().check().block();
        } finally {
            resolveErrorWhenDoingHealthCheck();
        }

        assertThat(actualResult.isUnHealthy()).isTrue();
    }
}
