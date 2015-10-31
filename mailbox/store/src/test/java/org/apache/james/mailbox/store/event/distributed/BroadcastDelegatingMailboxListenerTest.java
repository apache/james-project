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

package org.apache.james.mailbox.store.event.distributed;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.event.EventSerializer;
import org.apache.james.mailbox.store.publisher.MessageConsumer;
import org.apache.james.mailbox.store.publisher.Publisher;
import org.apache.james.mailbox.util.EventCollector;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class BroadcastDelegatingMailboxListenerTest {

    private static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", "user", "name");
    private static final MailboxPath MAILBOX_PATH_NEW = new MailboxPath("namespace_new", "user_new", "name_new");
    private static final String TOPIC = "topic";
    private static final byte[] BYTES = new byte[0];
    private static final MailboxSession mailboxSession = new MockMailboxSession("benwa");

    public static final MailboxListener.Event EVENT = new MailboxListener.Event(mailboxSession, MAILBOX_PATH) {};

    private BroadcastDelegatingMailboxListener broadcastDelegatingMailboxListener;
    private Publisher mockedPublisher;
    private EventSerializer mockedEventSerializer;
    private EventCollector mailboxEventCollector;
    private EventCollector eachEventCollector;
    private EventCollector onceEventCollector;

    @Before
    public void setUp() throws Exception {
        mockedEventSerializer = mock(EventSerializer.class);
        mockedPublisher = mock(Publisher.class);
        MessageConsumer messageConsumer = mock(MessageConsumer.class);
        broadcastDelegatingMailboxListener = new BroadcastDelegatingMailboxListener(mockedPublisher, messageConsumer, mockedEventSerializer, TOPIC);
        mailboxEventCollector = new EventCollector(MailboxListener.ListenerType.MAILBOX);
        eachEventCollector = new EventCollector(MailboxListener.ListenerType.EACH_NODE);
        onceEventCollector = new EventCollector(MailboxListener.ListenerType.ONCE);
    }

    @Test
    public void eventWithNoRegisteredListenersShouldWork() throws Exception {
        when(mockedEventSerializer.serializeEvent(EVENT)).thenAnswer(new Answer<byte[]>() {
            @Override
            public byte[] answer(InvocationOnMock invocation) throws Throwable {
                return BYTES;
            }
        });
        broadcastDelegatingMailboxListener.event(EVENT);
        verify(mockedEventSerializer).serializeEvent(EVENT);
        verify(mockedPublisher).publish(TOPIC, BYTES);
        verifyNoMoreInteractions(mockedEventSerializer, mockedPublisher);
    }

    @Test
    public void eventWithMailboxRegisteredListenerShouldWork() throws Exception {
        broadcastDelegatingMailboxListener.addListener(MAILBOX_PATH, mailboxEventCollector, mailboxSession);
        when(mockedEventSerializer.serializeEvent(EVENT)).thenAnswer(new Answer<byte[]>() {
            @Override
            public byte[] answer(InvocationOnMock invocation) throws Throwable {
                return BYTES;
            }
        });
        broadcastDelegatingMailboxListener.event(EVENT);
        assertThat(mailboxEventCollector.getEvents()).isEmpty();
        verify(mockedEventSerializer).serializeEvent(EVENT);
        verify(mockedPublisher).publish(TOPIC, BYTES);
        verifyNoMoreInteractions(mockedEventSerializer, mockedPublisher);
    }

    @Test
    public void eventWithEachRegisteredListenerShouldWork() throws Exception {
        broadcastDelegatingMailboxListener.addGlobalListener(eachEventCollector, mailboxSession);
        when(mockedEventSerializer.serializeEvent(EVENT)).thenAnswer(new Answer<byte[]>() {
            @Override
            public byte[] answer(InvocationOnMock invocation) throws Throwable {
                return BYTES;
            }
        });
        broadcastDelegatingMailboxListener.event(EVENT);
        assertThat(eachEventCollector.getEvents()).isEmpty();
        verify(mockedEventSerializer).serializeEvent(EVENT);
        verify(mockedPublisher).publish(TOPIC, BYTES);
        verifyNoMoreInteractions(mockedEventSerializer, mockedPublisher);
    }

    @Test
    public void eventWithOnceRegisteredListenerShouldWork() throws Exception {
        broadcastDelegatingMailboxListener.addGlobalListener(onceEventCollector, mailboxSession);
        when(mockedEventSerializer.serializeEvent(EVENT)).thenAnswer(new Answer<byte[]>() {
            @Override
            public byte[] answer(InvocationOnMock invocation) throws Throwable {
                return BYTES;
            }
        });
        broadcastDelegatingMailboxListener.event(EVENT);
        assertThat(onceEventCollector.getEvents()).containsOnly(EVENT);
        verify(mockedEventSerializer).serializeEvent(EVENT);
        verify(mockedPublisher).publish(TOPIC, BYTES);
        verifyNoMoreInteractions(mockedEventSerializer, mockedPublisher);
    }

    @Test
    public void receiveSerializedEventShouldWorkWithNoRegisteredListeners() throws Exception {
        when(mockedEventSerializer.deSerializeEvent(BYTES)).thenAnswer(new Answer<MailboxListener.Event>() {
            @Override
            public MailboxListener.Event answer(InvocationOnMock invocation) throws Throwable {
                return EVENT;
            }
        });
        broadcastDelegatingMailboxListener.receiveSerializedEvent(BYTES);
        verify(mockedEventSerializer).deSerializeEvent(BYTES);
        verifyNoMoreInteractions(mockedEventSerializer, mockedPublisher);
    }

    @Test
    public void receiveSerializedEventShouldWorkWithMailboxRegisteredListeners() throws Exception {
        broadcastDelegatingMailboxListener.addListener(MAILBOX_PATH, mailboxEventCollector, mailboxSession);
        when(mockedEventSerializer.deSerializeEvent(BYTES)).thenAnswer(new Answer<MailboxListener.Event>() {
            @Override
            public MailboxListener.Event answer(InvocationOnMock invocation) throws Throwable {
                return EVENT;
            }
        });
        broadcastDelegatingMailboxListener.receiveSerializedEvent(BYTES);
        verify(mockedEventSerializer).deSerializeEvent(BYTES);
        verifyNoMoreInteractions(mockedEventSerializer, mockedPublisher);
        assertThat(mailboxEventCollector.getEvents()).containsOnly(EVENT);
    }

    @Test
    public void receiveSerializedEventShouldWorkWithEachRegisteredListeners() throws Exception {
        broadcastDelegatingMailboxListener.addGlobalListener(eachEventCollector, mailboxSession);
        when(mockedEventSerializer.deSerializeEvent(BYTES)).thenAnswer(new Answer<MailboxListener.Event>() {
            @Override
            public MailboxListener.Event answer(InvocationOnMock invocation) throws Throwable {
                return EVENT;
            }
        });
        broadcastDelegatingMailboxListener.receiveSerializedEvent(BYTES);
        verify(mockedEventSerializer).deSerializeEvent(BYTES);
        verifyNoMoreInteractions(mockedEventSerializer, mockedPublisher);
        assertThat(eachEventCollector.getEvents()).containsOnly(EVENT);
    }

    @Test
    public void receiveSerializedEventShouldWorkWithOnceRegisteredListeners() throws Exception {
        broadcastDelegatingMailboxListener.addGlobalListener(onceEventCollector, mailboxSession);
        when(mockedEventSerializer.deSerializeEvent(BYTES)).thenAnswer(new Answer<MailboxListener.Event>() {
            @Override
            public MailboxListener.Event answer(InvocationOnMock invocation) throws Throwable {
                return EVENT;
            }
        });
        broadcastDelegatingMailboxListener.receiveSerializedEvent(BYTES);
        verify(mockedEventSerializer).deSerializeEvent(BYTES);
        verifyNoMoreInteractions(mockedEventSerializer, mockedPublisher);
        assertThat(onceEventCollector.getEvents()).isEmpty();
    }

    @Test
    public void deletionDistantEventsShouldBeWellHandled() throws Exception {
        final MailboxListener.Event event = new MailboxListener.MailboxDeletion(mailboxSession, MAILBOX_PATH);
        broadcastDelegatingMailboxListener.addListener(MAILBOX_PATH, mailboxEventCollector, mailboxSession);
        when(mockedEventSerializer.deSerializeEvent(BYTES)).thenAnswer(new Answer<MailboxListener.Event>() {
            @Override
            public MailboxListener.Event answer(InvocationOnMock invocation) throws Throwable {
                return event;
            }
        });
        broadcastDelegatingMailboxListener.receiveSerializedEvent(BYTES);
        verify(mockedEventSerializer).deSerializeEvent(BYTES);
        verifyNoMoreInteractions(mockedEventSerializer, mockedPublisher);
        assertThat(mailboxEventCollector.getEvents()).containsOnly(event);
    }

    @Test
    public void renameDistantEventsShouldBeWellHandled() throws Exception {
        final MailboxListener.Event event = new MailboxListener.MailboxRenamed(mailboxSession, MAILBOX_PATH) {
            @Override
            public MailboxPath getNewPath() {
                return MAILBOX_PATH_NEW;
            }
        };
        when(mockedEventSerializer.deSerializeEvent(BYTES)).thenAnswer(new Answer<MailboxListener.Event>() {
            @Override
            public MailboxListener.Event answer(InvocationOnMock invocation) throws Throwable {
                return event;
            }
        });
        broadcastDelegatingMailboxListener.addListener(MAILBOX_PATH, mailboxEventCollector, mailboxSession);
        broadcastDelegatingMailboxListener.receiveSerializedEvent(BYTES);
        verify(mockedEventSerializer).deSerializeEvent(BYTES);
        verifyNoMoreInteractions(mockedEventSerializer, mockedPublisher);
        assertThat(mailboxEventCollector.getEvents()).containsOnly(event);
    }

}
