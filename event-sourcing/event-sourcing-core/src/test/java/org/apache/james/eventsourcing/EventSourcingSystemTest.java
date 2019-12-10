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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.History;
import org.junit.jupiter.api.Test;
import org.mockito.internal.matchers.InstanceOf;
import org.mockito.internal.progress.ThreadSafeMockingProgress;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import scala.collection.immutable.List;
import scala.jdk.javaapi.CollectionConverters;

public interface EventSourcingSystemTest {

    String PAYLOAD_1 = "payload1";
    String PAYLOAD_2 = "payload2";
    TestAggregateId AGGREGATE_ID = TestAggregateId.apply(42);

    class MyCommand implements Command {
        private final String payload;

        public MyCommand(String payload) {
            this.payload = payload;
        }

        public String getPayload() {
            return payload;
        }
    }

    @Test
    default void dispatchShouldApplyCommandHandlerThenCallSubscribers(EventStore eventStore) {
        DataCollectorSubscriber subscriber = new DataCollectorSubscriber();
        EventSourcingSystem eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(simpleDispatcher(eventStore)),
            ImmutableSet.of(subscriber),
            eventStore);

        eventSourcingSystem.dispatch(new MyCommand(PAYLOAD_1));

        assertThat(subscriber.getData()).containsExactly(PAYLOAD_1);
    }

    @Test
    default void throwingSubscribersShouldNotAbortSubscriberChain(EventStore eventStore) {
        DataCollectorSubscriber subscriber = new DataCollectorSubscriber();
        EventSourcingSystem eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(simpleDispatcher(eventStore)),
            ImmutableSet.of(
                events -> {
                    throw new RuntimeException();
                },
                subscriber),
            eventStore);

        eventSourcingSystem.dispatch(new MyCommand(PAYLOAD_1));

        assertThat(subscriber.getData()).containsExactly(PAYLOAD_1);
    }

    @Test
    default void throwingStoreShouldNotLeadToPublishing() {
        EventStore eventStore = mock(EventStore.class);
        doThrow(new RuntimeException()).when(eventStore).appendAll(anyScalaList());
        when(eventStore.getEventsOfAggregate(any())).thenReturn(History.empty());

        DataCollectorSubscriber subscriber = new DataCollectorSubscriber();
        EventSourcingSystem eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(simpleDispatcher(eventStore)),
            ImmutableSet.of(
                events -> {
                    throw new RuntimeException();
                },
                subscriber),
            eventStore);

        assertThatThrownBy(() -> eventSourcingSystem.dispatch(new MyCommand(PAYLOAD_1)))
            .isInstanceOf(RuntimeException.class);

        assertThat(subscriber.getData()).isEmpty();
    }

    @Test
    default void dispatchShouldApplyCommandHandlerThenStoreGeneratedEvents(EventStore eventStore) {
        DataCollectorSubscriber subscriber = new DataCollectorSubscriber();
        EventSourcingSystem eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(simpleDispatcher(eventStore)),
            ImmutableSet.of(subscriber),
            eventStore);

        eventSourcingSystem.dispatch(new MyCommand(PAYLOAD_1));

        TestEvent expectedEvent = new TestEvent(EventId.first(), AGGREGATE_ID, PAYLOAD_1);
        assertThat(CollectionConverters.asJava(eventStore.getEventsOfAggregate(AGGREGATE_ID).getEvents()))
            .containsOnly(expectedEvent);
    }

    @Test
    default void dispatchShouldCallSubscriberForSubsequentCommands(EventStore eventStore) {
        DataCollectorSubscriber subscriber = new DataCollectorSubscriber();
        EventSourcingSystem eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(simpleDispatcher(eventStore)),
            ImmutableSet.of(subscriber),
            eventStore);

        eventSourcingSystem.dispatch(new MyCommand(PAYLOAD_1));
        eventSourcingSystem.dispatch(new MyCommand(PAYLOAD_2));

        assertThat(subscriber.getData()).containsExactly(PAYLOAD_1, PAYLOAD_2);
    }

    @Test
    default void dispatchShouldStoreEventsForSubsequentCommands(EventStore eventStore) {
        DataCollectorSubscriber subscriber = new DataCollectorSubscriber();
        EventSourcingSystem eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(simpleDispatcher(eventStore)),
            ImmutableSet.of(subscriber),
            eventStore);

        eventSourcingSystem.dispatch(new MyCommand(PAYLOAD_1));
        eventSourcingSystem.dispatch(new MyCommand(PAYLOAD_2));

        TestEvent expectedEvent1 = new TestEvent(EventId.first(), AGGREGATE_ID, PAYLOAD_1);
        TestEvent expectedEvent2 = new TestEvent(expectedEvent1.eventId().next(), AGGREGATE_ID, PAYLOAD_2);
        assertThat(CollectionConverters.asJava(eventStore.getEventsOfAggregate(AGGREGATE_ID).getEvents()))
            .containsOnly(expectedEvent1, expectedEvent2);
    }

    @Test
    default void dispatcherShouldBeAbleToReturnSeveralEvents(EventStore eventStore) {
        DataCollectorSubscriber subscriber = new DataCollectorSubscriber();
        EventSourcingSystem eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(wordCuttingDispatcher(eventStore)),
            ImmutableSet.of(subscriber),
            eventStore);

        eventSourcingSystem.dispatch(new MyCommand("This is a test"));

        assertThat(subscriber.getData()).containsExactly("This", "is", "a", "test");
    }

    @Test
    default void unknownCommandsShouldBeIgnored(EventStore eventStore) {
        DataCollectorSubscriber subscriber = new DataCollectorSubscriber();
        EventSourcingSystem eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(wordCuttingDispatcher(eventStore)),
            ImmutableSet.of(subscriber),
            eventStore);

        assertThatThrownBy(() -> eventSourcingSystem.dispatch(new Command() {
        }))
            .isInstanceOf(CommandDispatcher.UnknownCommandException.class);
    }

    @Test
    default void constructorShouldThrowWhenSeveralHandlersForTheSameCommand(EventStore eventStore) {
        DataCollectorSubscriber subscriber = new DataCollectorSubscriber();

        assertThatThrownBy(() ->
            EventSourcingSystem.fromJava(
                ImmutableSet.of(wordCuttingDispatcher(eventStore),
                    simpleDispatcher(eventStore)),
                ImmutableSet.of(subscriber),
                eventStore))
            .isInstanceOf(IllegalArgumentException.class);
    }

    default CommandHandler<MyCommand> simpleDispatcher(EventStore eventStore) {
        return new CommandHandler<MyCommand>() {
            @Override
            public Class<MyCommand> handledClass() {
                return MyCommand.class;
            }

            @Override
            public scala.collection.immutable.List<? extends Event> handle(MyCommand myCommand) {
                History history = eventStore.getEventsOfAggregate(AGGREGATE_ID);

                return CollectionConverters.asScala(ImmutableList.of(new TestEvent(
                    history.getNextEventId(),
                    AGGREGATE_ID,
                    myCommand.getPayload()))).toList();
            }
        };
    }

    default CommandHandler<MyCommand> wordCuttingDispatcher(EventStore eventStore) {
        return new CommandHandler<MyCommand>() {
            @Override
            public Class<MyCommand> handledClass() {
                return MyCommand.class;
            }

            @Override
            public scala.collection.immutable.List<? extends Event> handle(MyCommand myCommand) {
                History history = eventStore.getEventsOfAggregate(AGGREGATE_ID);

                EventIdIncrementer eventIdIncrementer = new EventIdIncrementer(history.getNextEventId());

                return CollectionConverters.asScala(Splitter.on(" ")
                    .splitToList(myCommand.getPayload())
                    .stream()
                    .map(word -> new TestEvent(
                        eventIdIncrementer.next(),
                        AGGREGATE_ID,
                        word))
                    .collect(Guavate.toImmutableList())).toList();
            }
        };
    }

    class EventIdIncrementer {
        private EventId currentEventId;

        public EventIdIncrementer(EventId base) {
            this.currentEventId = base;
        }

        public EventId next() {
            currentEventId = currentEventId.next();
            return currentEventId;
        }
    }

    static <T> List<T> anyScalaList() {
        ThreadSafeMockingProgress.mockingProgress().getArgumentMatcherStorage().reportMatcher(new InstanceOf(scala.collection.immutable.List.class, "<any scala List>"));
        return scala.collection.immutable.List.<T>newBuilder().result();
    }

}