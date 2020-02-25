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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

interface EventDeadLettersContract {

    class Group0 extends Group {

    }

    class Group1 extends Group {

    }

    class Group2 extends Group {

    }

    class Group3 extends Group {

    }

    class Group4 extends Group {

    }

    class Group5 extends Group {

    }

    class Group6 extends Group {

    }

    class Group7 extends Group {

    }

    class Group8 extends Group {

    }

    class Group9 extends Group {

    }

    static ImmutableMap<Integer, Group> concurrentGroups() {
        return IntStream.range(0, CONCURRENT_GROUPS.size()).boxed()
            .collect(Guavate.toImmutableMap(Function.identity(), CONCURRENT_GROUPS::get));
    }

    static Event event(Event.EventId eventId) {
        return new MailboxListener.MailboxAdded(SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, eventId);
    }

    List<Group> CONCURRENT_GROUPS = ImmutableList.of(new Group0(), new Group1(), new Group2(), new Group3(), new Group4(), new Group5(),
        new Group6(), new Group7(), new Group8(), new Group9());
    Duration RUN_SUCCESSFULLY_IN = Duration.ofSeconds(5);
    int THREAD_COUNT = 10;
    int OPERATION_COUNT = 50;

    Username USERNAME = Username.of("user");
    MailboxPath MAILBOX_PATH = new MailboxPath(MailboxConstants.USER_NAMESPACE, USERNAME, "mailboxName");
    MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(235);
    TestId MAILBOX_ID = TestId.of(563);
    Event.EventId EVENT_ID_1 = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");
    Event.EventId EVENT_ID_2 = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b5");
    Event.EventId EVENT_ID_3 = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b6");
    MailboxListener.MailboxAdded EVENT_1 = new MailboxListener.MailboxAdded(SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, EVENT_ID_1);
    MailboxListener.MailboxAdded EVENT_2 = new MailboxListener.MailboxAdded(SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, EVENT_ID_2);
    MailboxListener.MailboxAdded EVENT_3 = new MailboxListener.MailboxAdded(SESSION_ID, USERNAME, MAILBOX_PATH, MAILBOX_ID, EVENT_ID_3);
    EventDeadLetters.InsertionId INSERTION_ID_1 = EventDeadLetters.InsertionId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b7");
    EventDeadLetters.InsertionId INSERTION_ID_2 = EventDeadLetters.InsertionId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b8");
    EventDeadLetters.InsertionId INSERTION_ID_3 = EventDeadLetters.InsertionId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b9");

    Group GROUP_A = new EventBusTestFixture.GroupA();
    Group GROUP_B = new EventBusTestFixture.GroupB();
    Group NULL_GROUP = null;
    Event NULL_EVENT = null;
    EventDeadLetters.InsertionId NULL_INSERTION_ID = null;

    EventDeadLetters eventDeadLetters();

    default Stream<EventDeadLetters.InsertionId> allInsertionIds() {
        EventDeadLetters eventDeadLetters = eventDeadLetters();

        return eventDeadLetters.groupsWithFailedEvents()
            .flatMap(eventDeadLetters::failedIds)
            .toStream();
    }

    interface StoreContract extends EventDeadLettersContract {

        @Test
        default void storeShouldThrowWhenNullGroup() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.store(NULL_GROUP, EVENT_1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void storeShouldThrowWhenNullEvent() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.store(GROUP_A, NULL_EVENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void storeShouldThrowWhenBothGroupAndEventAndInsertionIdAreNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.store(NULL_GROUP, NULL_EVENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void storeShouldStoreGroupWithCorrespondingEvent() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            EventDeadLetters.InsertionId insertionId = eventDeadLetters.store(GROUP_A, EVENT_1).block();
            assertThat(eventDeadLetters.failedEvent(GROUP_A, insertionId).block())
                .isEqualTo(EVENT_1);
        }

        @Test
        default void storeShouldKeepConsistencyWhenConcurrentStore() throws Exception {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            ImmutableMap<Integer, Group> groups = concurrentGroups();
            Multimap<Integer, EventDeadLetters.InsertionId> storedInsertionIds = Multimaps.synchronizedSetMultimap(HashMultimap.create());

            ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> {
                    Event.EventId eventId = Event.EventId.random();
                    EventDeadLetters.InsertionId insertionId = eventDeadLetters.store(groups.get(threadNumber), event(eventId)).block();
                    storedInsertionIds.put(threadNumber, insertionId);
                })
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(RUN_SUCCESSFULLY_IN);

            groups.forEach((groupId, group) -> {
                Group storedGroup = groups.get(groupId);
                assertThat(eventDeadLetters.failedIds(storedGroup).collectList().block())
                    .hasSameElementsAs(storedInsertionIds.get(groupId));
            });
        }
    }

    interface RemoveContract extends EventDeadLettersContract {

        @Test
        default void removeShouldThrowWhenGroupIsNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.remove(NULL_GROUP, INSERTION_ID_1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void removeShouldThrowWhenInsertionIdIsNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.remove(GROUP_A, NULL_INSERTION_ID))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void removeShouldThrowWhenBothGroupAndInsertionIdAreNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.remove(NULL_GROUP, NULL_INSERTION_ID))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void removeShouldRemoveMatched() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_2).block();

            eventDeadLetters.remove(GROUP_A, INSERTION_ID_1).block();

            assertThat(eventDeadLetters.failedEvent(GROUP_A, INSERTION_ID_1).block())
                .isNull();
        }

        @Test
        default void removeShouldKeepNonMatched() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            EventDeadLetters.InsertionId insertionId1 = eventDeadLetters.store(GROUP_A, EVENT_1).block();
            EventDeadLetters.InsertionId insertionId2 = eventDeadLetters.store(GROUP_A, EVENT_2).block();
            EventDeadLetters.InsertionId insertionId3 = eventDeadLetters.store(GROUP_A, EVENT_3).block();

            eventDeadLetters.remove(GROUP_A, insertionId1).block();

            assertThat(eventDeadLetters.failedIds(GROUP_A).toStream())
                .containsOnly(insertionId2, insertionId3);
        }

        @Test
        default void removeShouldNotThrowWhenNoInsertionIdMatched() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();

            assertThatCode(() -> eventDeadLetters.remove(GROUP_A, INSERTION_ID_2).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void removeShouldNotThrowWhenNoGroupMatched() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();

            assertThatCode(() -> eventDeadLetters.remove(GROUP_B, INSERTION_ID_1).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void removeShouldKeepConsistencyWhenConcurrentRemove() throws Exception {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            ImmutableMap<Integer, Group> groups = concurrentGroups();
            ConcurrentHashMap<Integer, EventDeadLetters.InsertionId> storedInsertionIds = new ConcurrentHashMap<>();

            ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> {
                    int operationIndex = threadNumber * OPERATION_COUNT + step;
                    Event.EventId eventId = Event.EventId.random();
                    EventDeadLetters.InsertionId insertionId = eventDeadLetters.store(groups.get(threadNumber), event(eventId)).block();
                    storedInsertionIds.put(operationIndex, insertionId);
                })
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(RUN_SUCCESSFULLY_IN);

            ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> {
                    int operationIndex = threadNumber * OPERATION_COUNT + step;
                    eventDeadLetters.remove(groups.get(threadNumber), storedInsertionIds.get(operationIndex))
                        .block();
                })
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(RUN_SUCCESSFULLY_IN);

            assertThat(allInsertionIds())
                .isEmpty();
        }
    }

    interface FailedEventContract extends EventDeadLettersContract {

        @Test
        default void failedEventShouldThrowWhenGroupIsNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.failedEvent(NULL_GROUP, INSERTION_ID_1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void failedEventShouldThrowWhenInsertionIdIsNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.failedEvent(GROUP_A, NULL_INSERTION_ID))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void failedEventShouldThrowWhenBothGroupAndInsertionIdAreNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.failedEvent(NULL_GROUP, NULL_INSERTION_ID))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void failedEventShouldReturnEmptyWhenNotFound() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_2).block();

            assertThat(eventDeadLetters.failedEvent(GROUP_A, INSERTION_ID_3).block())
                .isNull();
        }

        @Test
        default void failedEventShouldReturnEventWhenContains() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            EventDeadLetters.InsertionId insertionId = eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_2).block();

            assertThat(eventDeadLetters.failedEvent(GROUP_A, insertionId).block())
                .isEqualTo(EVENT_1);
        }

        @Test
        default void failedEventShouldNotRemoveEvent() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            EventDeadLetters.InsertionId insertionId1 = eventDeadLetters.store(GROUP_A, EVENT_1).block();
            EventDeadLetters.InsertionId insertionId2 = eventDeadLetters.store(GROUP_A, EVENT_2).block();
            EventDeadLetters.InsertionId insertionId3 = eventDeadLetters.store(GROUP_A, EVENT_3).block();

            eventDeadLetters.failedEvent(GROUP_A, insertionId1).block();

            assertThat(allInsertionIds())
                .containsOnly(insertionId1, insertionId2, insertionId3);
        }

        @Test
        default void failedEventShouldNotThrowWhenNoGroupMatched() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            EventDeadLetters.InsertionId insertionId = eventDeadLetters.store(GROUP_A, EVENT_1).block();

            assertThatCode(() -> eventDeadLetters.failedEvent(GROUP_B, insertionId).block())
                .doesNotThrowAnyException();
        }
    }

    interface FailedEventsContract extends EventDeadLettersContract {

        @Test
        default void failedEventsShouldThrowWhenGroupIsNull() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThatThrownBy(() -> eventDeadLetters.failedIds(NULL_GROUP))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        default void failedEventsByGroupShouldReturnEmptyWhenNonMatch() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_A, EVENT_2).block();
            eventDeadLetters.store(GROUP_A, EVENT_3).block();

            assertThat(eventDeadLetters.failedIds(GROUP_B).toStream())
                .isEmpty();
        }

        @Test
        default void failedEventsByGroupShouldReturnAllEventsCorrespondingToGivenGroup() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            EventDeadLetters.InsertionId insertionId = eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_B, EVENT_2).block();
            eventDeadLetters.store(GROUP_B, EVENT_3).block();

            assertThat(eventDeadLetters.failedIds(GROUP_A).toStream())
                .containsOnly(insertionId);
        }

        @Test
        default void failedEventsByGroupShouldNotRemoveEvents() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            EventDeadLetters.InsertionId insertionId1 = eventDeadLetters.store(GROUP_A, EVENT_1).block();
            EventDeadLetters.InsertionId insertionId2 = eventDeadLetters.store(GROUP_A, EVENT_2).block();
            EventDeadLetters.InsertionId insertionId3 = eventDeadLetters.store(GROUP_B, EVENT_3).block();

            eventDeadLetters.failedIds(GROUP_A).toStream();

            assertThat(allInsertionIds())
                .containsOnly(insertionId1, insertionId2, insertionId3);
        }
    }

    interface GroupsWithFailedEventsContract extends EventDeadLettersContract {
        @Test
        default void groupsWithFailedEventsShouldReturnAllStoredGroups() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();
            eventDeadLetters.store(GROUP_A, EVENT_1).block();
            eventDeadLetters.store(GROUP_B, EVENT_1).block();

            assertThat(eventDeadLetters.groupsWithFailedEvents().collectList().block())
                .containsOnly(GROUP_A, GROUP_B);
        }

        @Test
        default void groupsWithFailedEventsShouldReturnEmptyWhenNoStored() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThat(eventDeadLetters.groupsWithFailedEvents().toStream()).isEmpty();
        }
    }

    interface ContainEventsContract extends EventDeadLettersContract {
        @Test
        default void containEventsShouldReturnFalseOnNothingStored() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();

            assertThat(eventDeadLetters.containEvents().block()).isFalse();
        }

        @Test
        default void containEventsShouldReturnTrueOnStoredEvents() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();
            eventDeadLetters.store(GROUP_A, EVENT_1).block();

            assertThat(eventDeadLetters.containEvents().block()).isTrue();
        }

        @Test
        default void containEventsShouldReturnFalseWhenRemoveAllStoredEvents() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();
            EventDeadLetters.InsertionId insertionId1 = eventDeadLetters().store(GROUP_A, EVENT_1).block();
            EventDeadLetters.InsertionId insertionId2 = eventDeadLetters().store(GROUP_A, EVENT_2).block();

            assertThat(eventDeadLetters.containEvents().block()).isTrue();

            eventDeadLetters.remove(GROUP_A, insertionId1).block();
            eventDeadLetters.remove(GROUP_A, insertionId2).block();

            assertThat(eventDeadLetters.containEvents().block()).isFalse();
        }

        @Test
        default void containEventsShouldReturnTrueWhenRemoveSomeStoredEvents() {
            EventDeadLetters eventDeadLetters = eventDeadLetters();
            EventDeadLetters.InsertionId insertionId1 = eventDeadLetters().store(GROUP_A, EVENT_1).block();
            eventDeadLetters().store(GROUP_B, EVENT_2).block();

            assertThat(eventDeadLetters.containEvents().block()).isTrue();

            eventDeadLetters.remove(GROUP_A, insertionId1).block();

            assertThat(eventDeadLetters.containEvents().block()).isTrue();
        }
    }

    interface AllContracts extends StoreContract, RemoveContract, FailedEventContract, FailedEventsContract, GroupsWithFailedEventsContract, ContainEventsContract {

    }
}