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

import static org.apache.james.events.RetryBackoffConfiguration.DEFAULT_JITTER_FACTOR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.core.Username;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public interface EventBusTestFixture {

    class EventListenerCountingSuccessfulExecution implements EventListener {
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

    class EventMatcherThrowingListener extends EventListenerCountingSuccessfulExecution {
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

    class TestEvent implements Event {
        private final EventId eventId;
        private final Username username;

        public TestEvent(EventId eventId, Username username) {
            this.eventId = eventId;
            this.username = username;
        }

        @Override
        public Username getUsername() {
            return username;
        }

        @Override
        public boolean isNoop() {
            return username.asString().equals("noop");
        }

        @Override
        public EventId getEventId() {
            return eventId;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof TestEvent) {
                TestEvent that = (TestEvent) o;

                return Objects.equals(this.eventId, that.eventId)
                    && Objects.equals(this.username, that.username);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, username);
        }
    }

    class UnsupportedEvent implements Event {
        private final EventId eventId;
        private final Username username;

        public UnsupportedEvent(EventId eventId, Username username) {
            this.eventId = eventId;
            this.username = username;
        }

        @Override
        public Username getUsername() {
            return username;
        }

        @Override
        public boolean isNoop() {
            return false;
        }

        @Override
        public EventId getEventId() {
            return eventId;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof UnsupportedEvent) {
                UnsupportedEvent that = (UnsupportedEvent) o;

                return Objects.equals(this.eventId, that.eventId)
                    && Objects.equals(this.username, that.username);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, username);
        }
    }

    class TestEventSerializer implements EventSerializer {
        @Override
        public String toJson(Event event) {
            Preconditions.checkArgument(event instanceof TestEvent || event instanceof UnsupportedEvent);
            return event.getClass().getCanonicalName() + "&" + event.getEventId().getId().toString() + "&" + event.getUsername().asString();
        }

        @Override
        public Event asEvent(String serialized) {
            Preconditions.checkArgument(serialized.contains("&"));
            List<String> parts = Splitter.on("&").splitToList(serialized);
            Preconditions.checkArgument(parts.get(0).equals(TestEvent.class.getCanonicalName()));

            Event.EventId eventId = Event.EventId.of(UUID.fromString(parts.get(1)));
            Username username = Username.of(Joiner.on("&").join(parts.stream().skip(2).collect(Guavate.toImmutableList())));
            return new TestEvent(eventId, username);
        }
    }

    class TestRegistrationKey implements RegistrationKey {
        private final String value;

        public TestRegistrationKey(String value) {
            this.value = value;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof TestRegistrationKey) {
                TestRegistrationKey that = (TestRegistrationKey) o;

                return Objects.equals(this.value, that.value);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    class TestRegistrationKeyFactory implements RegistrationKey.Factory {
        @Override
        public Class<? extends RegistrationKey> forClass() {
            return TestRegistrationKey.class;
        }

        @Override
        public RegistrationKey fromString(String asString) {
            return new TestRegistrationKey(asString);
        }
    }

    Username USERNAME = Username.of("user");
    Event.EventId EVENT_ID = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");
    Event.EventId EVENT_ID_2 = Event.EventId.of("5a7a9f3f-5f03-44be-b457-a51e93760645");
    Event EVENT = new TestEvent(EVENT_ID, USERNAME);
    Event EVENT_2 = new TestEvent(EVENT_ID_2, USERNAME);
    Event EVENT_UNSUPPORTED_BY_LISTENER = new UnsupportedEvent(EVENT_ID_2, USERNAME);

    java.time.Duration ONE_SECOND = java.time.Duration.ofSeconds(1);
    java.time.Duration FIVE_HUNDRED_MS = java.time.Duration.ofMillis(500);

    ImmutableSet<RegistrationKey> NO_KEYS = ImmutableSet.of();
    RegistrationKey KEY_1 = new TestRegistrationKey("a");
    RegistrationKey KEY_2 = new TestRegistrationKey("b");
    RegistrationKey KEY_3 = new TestRegistrationKey("c");
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

    static EventListener newListener() {
        EventListener listener = mock(EventListener.class);
        when(listener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.SYNCHRONOUS);
        when(listener.isHandling(any(TestEvent.class))).thenReturn(true);
        return listener;
    }

    static EventListener newAsyncListener() {
        EventListener listener = mock(EventListener.class);
        when(listener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.ASYNCHRONOUS);
        when(listener.isHandling(any(TestEvent.class))).thenReturn(true);
        return listener;
    }
}
