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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Test;

interface EventDeadLettersHealthCheckContract {

    ComponentName COMPONENT_NAME = new ComponentName("EventDeadLettersHealthCheck");
    String EXPECTED_DEGRADED_MESSAGE = "EventDeadLetters contain events. This might indicate transient failure on mailbox event processing.";

    Username USERNAME = Username.of("user");
    MailboxPath MAILBOX_PATH = new MailboxPath(MailboxConstants.USER_NAMESPACE, USERNAME, "mailboxName");
    MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(235);
    TestId MAILBOX_ID = TestId.of(563);
    Event.EventId EVENT_ID_1 = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");
    Event.EventId EVENT_ID_2 = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b5");
    MailboxListener.MailboxAdded EVENT_1 = new MailboxListener.MailboxAdded(SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, EVENT_ID_1);
    MailboxListener.MailboxAdded EVENT_2 = new MailboxListener.MailboxAdded(SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, EVENT_ID_2);

    Group GROUP_A = new EventBusTestFixture.GroupA();
    Group GROUP_B = new EventBusTestFixture.GroupB();

    EventDeadLettersHealthCheck testee();

    EventDeadLetters eventDeadLetters();

    void createErrorWhenDoingHealthCheck();

    void resolveErrorWhenDoingHealthCheck();

    @Test
    default void checkShouldReturnHealthyWhenEventDeadLetterEmpty() {
        assertThat(testee().checkReactive().block().isHealthy()).isTrue();
        assertThat(testee().checkReactive().block())
            .isEqualTo(Result.healthy(COMPONENT_NAME));
    }

    @Test
    default void checkShouldReturnDegradedWhenEventDeadLetterContainEvent() {
        eventDeadLetters().store(GROUP_A, EVENT_1).block();

        assertThat(testee().checkReactive().block().isDegraded()).isTrue();
        assertThat(testee().checkReactive().block())
            .isEqualTo(Result.degraded(COMPONENT_NAME, EXPECTED_DEGRADED_MESSAGE));
    }

    @Test
    default void checkShouldReturnDegradedWhenEventDeadLetterContainEvents() {
        eventDeadLetters().store(GROUP_A, EVENT_1).block();
        eventDeadLetters().store(GROUP_B, EVENT_2).block();

        assertThat(testee().checkReactive().block().isDegraded()).isTrue();
        assertThat(testee().checkReactive().block())
            .isEqualTo(Result.degraded(COMPONENT_NAME, EXPECTED_DEGRADED_MESSAGE));
    }

    @Test
    default void checkShouldReturnHealthyWhenRemovedAllEventDeadLetters() {
        EventDeadLetters.InsertionId insertionId1 = eventDeadLetters().store(GROUP_A, EVENT_1).block();
        EventDeadLetters.InsertionId insertionId2 = eventDeadLetters().store(GROUP_B, EVENT_2).block();

        assertThat(testee().checkReactive().block().isDegraded()).isTrue();
        assertThat(testee().checkReactive().block())
            .isEqualTo(Result.degraded(COMPONENT_NAME, EXPECTED_DEGRADED_MESSAGE));

        eventDeadLetters().remove(GROUP_A, insertionId1).block();
        eventDeadLetters().remove(GROUP_B, insertionId2).block();

        assertThat(testee().checkReactive().block().isHealthy()).isTrue();
        assertThat(testee().checkReactive().block())
            .isEqualTo(Result.healthy(COMPONENT_NAME));
    }

    @Test
    default void checkShouldReturnDegradedWhenRemovedSomeEventDeadLetters() {
        EventDeadLetters.InsertionId insertionId1 = eventDeadLetters().store(GROUP_A, EVENT_1).block();
        eventDeadLetters().store(GROUP_B, EVENT_2).block();

        assertThat(testee().checkReactive().block().isDegraded()).isTrue();
        assertThat(testee().checkReactive().block())
            .isEqualTo(Result.degraded(COMPONENT_NAME, EXPECTED_DEGRADED_MESSAGE));

        eventDeadLetters().remove(GROUP_A, insertionId1).block();

        assertThat(testee().checkReactive().block().isDegraded()).isTrue();
        assertThat(testee().checkReactive().block())
            .isEqualTo(Result.degraded(COMPONENT_NAME, EXPECTED_DEGRADED_MESSAGE));
    }

    @Test
    default void checkShouldReturnUnHealthyWhenEventDeadLetterError() {
        Result actualResult;
        try {
            createErrorWhenDoingHealthCheck();
            actualResult = testee().checkReactive().block();
        } finally {
            resolveErrorWhenDoingHealthCheck();
        }

        assertThat(actualResult.isUnHealthy()).isTrue();
    }
}
