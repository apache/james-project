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

import static org.apache.james.mailbox.events.RetryBackoffConfiguration.DEFAULT_JITTER_FACTOR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public interface EventBusTestFixture {

    class MailboxListenerCountingSuccessfulExecution implements MailboxListener {
        private final AtomicInteger calls = new AtomicInteger(0);

        @Override
        public boolean isHandling(Event event) {
            return true;
        }

        @Override
        public void event(Event event) {
            calls.incrementAndGet();
        }

        public int numberOfEventCalls() {
            return calls.get();
        }
    }

    class EventMatcherThrowingListener extends MailboxListenerCountingSuccessfulExecution {
        private final ImmutableSet<Event> eventsCauseThrowing;

        EventMatcherThrowingListener(ImmutableSet<Event> eventsCauseThrowing) {
            this.eventsCauseThrowing = eventsCauseThrowing;
        }

        @Override
        public boolean isHandling(Event event) {
            return true;
        }

        @Override
        public void event(Event event) {
            if (eventsCauseThrowing.contains(event)) {
                throw new RuntimeException("event triggers throwing");
            }
            super.event(event);
        }
    }

    class GroupA extends Group {

    }

    class GroupB extends Group {

    }

    class GroupC extends Group {

    }

    MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(42);
    Username USERNAME = Username.of("user");
    MailboxPath MAILBOX_PATH = new MailboxPath(MailboxConstants.USER_NAMESPACE, USERNAME, "mailboxName");
    TestId TEST_ID = TestId.of(18);
    Event.EventId EVENT_ID = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");
    Event.EventId EVENT_ID_2 = Event.EventId.of("5a7a9f3f-5f03-44be-b457-a51e93760645");
    MailboxListener.MailboxEvent EVENT = new MailboxListener.MailboxAdded(SESSION_ID, USERNAME, MAILBOX_PATH, TEST_ID, EVENT_ID);
    MailboxListener.MailboxEvent EVENT_2 = new MailboxListener.MailboxAdded(SESSION_ID, USERNAME, MAILBOX_PATH, TEST_ID, EVENT_ID_2);
    MailboxListener.MailboxRenamed EVENT_UNSUPPORTED_BY_LISTENER = new MailboxListener.MailboxRenamed(SESSION_ID, USERNAME, MAILBOX_PATH, TEST_ID, MAILBOX_PATH, EVENT_ID_2);

    java.time.Duration ONE_SECOND = java.time.Duration.ofSeconds(1);
    java.time.Duration FIVE_HUNDRED_MS = java.time.Duration.ofMillis(500);
    MailboxId ID_1 = TEST_ID;
    MailboxId ID_2 = TestId.of(24);
    MailboxId ID_3 = TestId.of(36);
    ImmutableSet<RegistrationKey> NO_KEYS = ImmutableSet.of();
    MailboxIdRegistrationKey KEY_1 = new MailboxIdRegistrationKey(ID_1);
    MailboxIdRegistrationKey KEY_2 = new MailboxIdRegistrationKey(ID_2);
    MailboxIdRegistrationKey KEY_3 = new MailboxIdRegistrationKey(ID_3);
    GroupA GROUP_A = new GroupA();
    GroupB GROUP_B = new GroupB();
    GroupC GROUP_C = new GroupC();
    List<Group> ALL_GROUPS = ImmutableList.of(GROUP_A, GROUP_B, GROUP_C);

    java.time.Duration DEFAULT_FIRST_BACKOFF = java.time.Duration.ofMillis(5);
    //Retry backoff configuration for testing with a shorter first backoff to accommodate the shorter retry interval in tests
    RetryBackoffConfiguration RETRY_BACKOFF_CONFIGURATION = RetryBackoffConfiguration.builder()
        .maxRetries(3)
        .firstBackoff(DEFAULT_FIRST_BACKOFF)
        .jitterFactor(DEFAULT_JITTER_FACTOR)
        .build();

    static MailboxListener newListener() {
        MailboxListener listener = mock(MailboxListener.class);
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
        when(listener.isHandling(any(MailboxListener.MailboxAdded.class))).thenReturn(true);
        return listener;
    }

    static MailboxListener newAsyncListener() {
        MailboxListener listener = mock(MailboxListener.class);
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
        when(listener.isHandling(any(MailboxListener.MailboxAdded.class))).thenReturn(true);
        return listener;
    }
}
