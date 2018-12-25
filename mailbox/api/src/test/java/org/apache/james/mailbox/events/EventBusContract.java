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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.james.core.User;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;

public interface EventBusContract {
    MailboxListener.MailboxEvent EVENT = new MailboxListener.MailboxAdded(
        MailboxSession.SessionId.of(42),
        User.fromUsername("user"),
        new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", "mailboxName"),
        TestId.of(18),
        Event.EventId.random());
    MailboxListener.Added NOOP_EVENT = new MailboxListener.Added(MailboxSession.SessionId.of(18), User.fromUsername("bob"), MailboxPath.forUser("bob", "mailbox"), TestId.of(58), ImmutableSortedMap.of(), Event.EventId.random());

    class GroupA extends Group {}

    class GroupB extends Group {}

    int ONE_SECOND = 1000;
    int FIVE_HUNDRED_MS = 500;
    MailboxId ID_1 = TestId.of(18);
    MailboxId ID_2 = TestId.of(24);
    ImmutableSet<RegistrationKey> NO_KEYS = ImmutableSet.of();
    MailboxIdRegistrationKey KEY_1 = new MailboxIdRegistrationKey(ID_1);
    MailboxIdRegistrationKey KEY_2 = new MailboxIdRegistrationKey(ID_2);
    List<Class<? extends Group>> ALL_GROUPS = ImmutableList.of(GroupA.class, GroupB.class);

    EventBus eventBus();

    default MailboxListener newListener() {
        MailboxListener listener = mock(MailboxListener.class);
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
        return listener;
    }

    @Test
    default void listenerGroupShouldReceiveEvents() {
        MailboxListener listener = newListener();

        eventBus().register(listener, new GroupA());

        eventBus().dispatch(EVENT, NO_KEYS).block();

        verify(listener, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    default void groupListenersShouldNotReceiveNoopEvents() {
        MailboxListener listener = newListener();

        eventBus().register(listener, new GroupA());

        eventBus().dispatch(NOOP_EVENT, NO_KEYS).block();

        verifyNoMoreInteractions(listener);
    }

    @Test
    default void registeredListenersShouldNotReceiveNoopEvents() {
        MailboxListener listener = newListener();

        eventBus().register(listener, KEY_1);

        eventBus().dispatch(NOOP_EVENT, KEY_1).block();

        verifyNoMoreInteractions(listener);
    }

    @Test
    default void dispatchShouldNotThrowWhenAGroupListenerFails() {
        MailboxListener listener = newListener();
        doThrow(new RuntimeException()).when(listener).event(any());

        eventBus().register(listener, new GroupA());

        assertThatCode(() -> eventBus().dispatch(EVENT, NO_KEYS).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void dispatchShouldNotThrowWhenARegisteredListenerFails() {
        MailboxListener listener = newListener();
        doThrow(new RuntimeException()).when(listener).event(any());

        eventBus().register(listener, KEY_1);

        assertThatCode(() -> eventBus().dispatch(EVENT, NO_KEYS).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void eachListenerGroupShouldReceiveEvents() {
        MailboxListener listener = newListener();
        MailboxListener listener2 = newListener();
        eventBus().register(listener, new GroupA());
        eventBus().register(listener2, new GroupB());

        eventBus().dispatch(EVENT, NO_KEYS).block();

        verify(listener, timeout(ONE_SECOND).times(1)).event(any());
        verify(listener2, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    default void unregisteredGroupListenerShouldNotReceiveEvents() {
        MailboxListener listener = newListener();
        Registration registration = eventBus().register(listener, new GroupA());

        registration.unregister();

        eventBus().dispatch(EVENT, NO_KEYS).block();
        verify(listener, after(FIVE_HUNDRED_MS).never())
            .event(any());
    }

    @Test
    default void registerShouldThrowWhenAGroupIsAlreadyUsed() {
        MailboxListener listener = newListener();
        MailboxListener listener2 = newListener();

        eventBus().register(listener, new GroupA());

        assertThatThrownBy(() -> eventBus().register(listener2, new GroupA()))
            .isInstanceOf(GroupAlreadyRegistered.class);
    }

    @Test
    default void registerShouldNotThrowOnAnUnregisteredGroup() {
        MailboxListener listener = newListener();
        MailboxListener listener2 = newListener();

        eventBus().register(listener, new GroupA()).unregister();

        assertThatCode(() -> eventBus().register(listener2, new GroupA()))
            .doesNotThrowAnyException();
    }

    @Test
    default void unregisterShouldBeIdempotentForGroups() {
        MailboxListener listener = newListener();

        Registration registration = eventBus().register(listener, new GroupA());
        registration.unregister();

        assertThatCode(registration::unregister)
            .doesNotThrowAnyException();
    }

    @Test
    default void registerShouldAcceptAlreadyUnregisteredGroups() {
        MailboxListener listener = newListener();

        eventBus().register(listener, new GroupA()).unregister();
        eventBus().register(listener, new GroupA());

        eventBus().dispatch(EVENT, NO_KEYS).block();

        verify(listener, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    default void dispatchShouldNotNotifyRegisteredListenerWhenEmptyKeySet() {
        MailboxListener listener = newListener();
        eventBus().register(listener, KEY_1);

        eventBus().dispatch(EVENT, NO_KEYS).block();

        verify(listener, after(FIVE_HUNDRED_MS).never())
            .event(any());
    }

    @Test
    default void dispatchShouldNotNotifyListenerRegisteredOnOtherKeys() {
        MailboxListener listener = newListener();
        eventBus().register(listener, KEY_1);

        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_2)).block();

        verify(listener, after(FIVE_HUNDRED_MS).never())
            .event(any());
    }

    @Test
    default void dispatchShouldNotifyRegisteredListeners() {
        MailboxListener listener = newListener();
        eventBus().register(listener, KEY_1);

        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

        verify(listener, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    default void dispatchShouldNotifyOnlyRegisteredListener() {
        MailboxListener listener = newListener();
        MailboxListener listener2 = newListener();
        eventBus().register(listener, KEY_1);
        eventBus().register(listener2, KEY_2);

        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

        verify(listener, timeout(ONE_SECOND).times(1)).event(any());
        verify(listener2, after(FIVE_HUNDRED_MS).never())
            .event(any());
    }

    @Test
    default void dispatchShouldNotifyAllListenersRegisteredOnAKey() {
        MailboxListener listener = newListener();
        MailboxListener listener2 = newListener();
        eventBus().register(listener, KEY_1);
        eventBus().register(listener2, KEY_1);

        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

        verify(listener, timeout(ONE_SECOND).times(1)).event(any());
        verify(listener2, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    default void registerShouldAllowDuplicatedRegistration() {
        MailboxListener listener = newListener();
        eventBus().register(listener, KEY_1);
        eventBus().register(listener, KEY_1);

        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

        verify(listener, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    default void unregisterShouldRemoveDoubleRegisteredListener() {
        MailboxListener listener = newListener();
        eventBus().register(listener, KEY_1);
        eventBus().register(listener, KEY_1).unregister();

        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

        verify(listener, after(FIVE_HUNDRED_MS).never())
            .event(any());
    }

    @Test
    default void callingAllUnregisterMethodShouldUnregisterTheListener() {
        MailboxListener listener = newListener();
        Registration registration = eventBus().register(listener, KEY_1);
        eventBus().register(listener, KEY_1).unregister();
        registration.unregister();

        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

        verify(listener, after(FIVE_HUNDRED_MS).never())
            .event(any());
    }

    @Test
    default void unregisterShouldHaveNotNotifyWhenCalledOnDifferentKeys() {
        MailboxListener listener = newListener();
        eventBus().register(listener, KEY_1);
        eventBus().register(listener, KEY_2).unregister();

        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

        verify(listener, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    default void unregisterShouldBeIdempotentForKeyRegistrations() {
        MailboxListener listener = newListener();

        Registration registration = eventBus().register(listener, KEY_1);
        registration.unregister();

        assertThatCode(registration::unregister)
            .doesNotThrowAnyException();
    }

    @Test
    default void dispatchShouldAcceptSeveralKeys() {
        MailboxListener listener = newListener();
        eventBus().register(listener, KEY_1);

        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1, KEY_2)).block();

        verify(listener, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    default void dispatchShouldCallListenerOnceWhenSeveralKeysMatching() {
        MailboxListener listener = newListener();
        eventBus().register(listener, KEY_1);
        eventBus().register(listener, KEY_2);

        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1, KEY_2)).block();

        verify(listener, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    default void dispatchShouldNotNotifyUnregisteredListener() {
        MailboxListener listener = newListener();
        eventBus().register(listener, KEY_1).unregister();

        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

        verify(listener, after(FIVE_HUNDRED_MS).never())
            .event(any());
    }

    @Test
    default void dispatchShouldCallSynchronousListener() {
        MailboxListener listener = newListener();

        eventBus().register(listener, new GroupA());

        eventBus().dispatch(EVENT, NO_KEYS).block();

        verify(listener, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    default void dispatchShouldNotBlockAsynchronousListener() {
        MailboxListener listener = newListener();
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.await();
            return null;
        }).when(listener).event(EVENT);

        assertTimeout(Duration.ofSeconds(2),
            () -> {
                eventBus().dispatch(EVENT, NO_KEYS).block();
                latch.countDown();
            });
    }
}