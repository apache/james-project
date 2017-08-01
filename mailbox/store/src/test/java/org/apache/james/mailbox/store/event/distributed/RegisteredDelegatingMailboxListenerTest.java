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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.event.EventSerializer;
import org.apache.james.mailbox.store.publisher.MessageConsumer;
import org.apache.james.mailbox.store.publisher.Publisher;
import org.apache.james.mailbox.store.publisher.Topic;
import org.apache.james.mailbox.util.EventCollector;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;

public class RegisteredDelegatingMailboxListenerTest {

    private static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", "user", "name");
    private static final MailboxPath MAILBOX_PATH_NEW = new MailboxPath("namespace_new", "user_new", "name_new");
    private static final Topic TOPIC = new Topic("topic");
    private static final Topic TOPIC_2 = new Topic("topic_2");
    private static final byte[] BYTES = new byte[0];

    private RegisteredDelegatingMailboxListener testee;
    private MailboxPathRegister mockedMailboxPathRegister;
    private EventSerializer mockedEventSerializer;
    private Publisher mockedPublisher;
    private EventCollector mailboxEventCollector;
    private EventCollector eachEventCollector;
    private EventCollector onceEventCollector;
    private MailboxSession mailboxSession;
    private MailboxListener.Event event;

    @Before
    public void setUp() throws Exception {
        mailboxSession = new MockMailboxSession("benwa");
        event = new MailboxListener.Event(mailboxSession, MAILBOX_PATH) {};

        mockedEventSerializer = mock(EventSerializer.class);
        mockedPublisher = mock(Publisher.class);
        mockedMailboxPathRegister = mock(MailboxPathRegister.class);
        MessageConsumer messageConsumer = mock(MessageConsumer.class);
        testee = new RegisteredDelegatingMailboxListener(mockedEventSerializer, mockedPublisher, messageConsumer, mockedMailboxPathRegister);
        mailboxEventCollector = new EventCollector(MailboxListener.ListenerType.MAILBOX);
        eachEventCollector = new EventCollector(MailboxListener.ListenerType.EACH_NODE);
        onceEventCollector = new EventCollector(MailboxListener.ListenerType.ONCE);
    }

    @Test
    public void eventShouldBeLocallyDeliveredIfThereIsNoOtherRegisteredServers() throws Exception {
        testee.addListener(MAILBOX_PATH, mailboxEventCollector, mailboxSession);
        verify(mockedMailboxPathRegister).register(MAILBOX_PATH);
        when(mockedMailboxPathRegister.getTopics(MAILBOX_PATH)).thenReturn(Sets.newHashSet(TOPIC));
        when(mockedMailboxPathRegister.getLocalTopic()).thenReturn(TOPIC);
        testee.event(event);
        assertThat(mailboxEventCollector.getEvents()).containsOnly(event);
        verify(mockedMailboxPathRegister, times(2)).getLocalTopic();
        verify(mockedMailboxPathRegister).getTopics(MAILBOX_PATH);
        verifyNoMoreInteractions(mockedEventSerializer);
        verifyNoMoreInteractions(mockedPublisher);
        verifyNoMoreInteractions(mockedMailboxPathRegister);
    }

    @Test
    public void eventShouldBeRemotelySent() throws Exception {
        testee.addListener(MAILBOX_PATH, mailboxEventCollector, mailboxSession);
        verify(mockedMailboxPathRegister).register(MAILBOX_PATH);
        when(mockedMailboxPathRegister.getTopics(MAILBOX_PATH)).thenReturn(Sets.newHashSet(TOPIC, TOPIC_2));
        when(mockedMailboxPathRegister.getLocalTopic()).thenReturn(TOPIC);
        when(mockedEventSerializer.serializeEvent(event)).thenReturn(BYTES);
        testee.event(event);
        assertThat(mailboxEventCollector.getEvents()).containsOnly(event);
        verify(mockedMailboxPathRegister, times(2)).getLocalTopic();
        verify(mockedMailboxPathRegister).getTopics(MAILBOX_PATH);
        verify(mockedEventSerializer).serializeEvent(event);
        verify(mockedPublisher).publish(TOPIC_2, BYTES);
        verifyNoMoreInteractions(mockedEventSerializer);
        verifyNoMoreInteractions(mockedPublisher);
        verifyNoMoreInteractions(mockedMailboxPathRegister);
    }

    @Test
    public void onceListenersShouldBeTriggered() throws Exception {
        MailboxListener.Event event = new MailboxListener.Event(mailboxSession, MAILBOX_PATH) {};
        testee.addGlobalListener(onceEventCollector, mailboxSession);
        when(mockedMailboxPathRegister.getTopics(MAILBOX_PATH)).thenReturn(Sets.newHashSet(TOPIC));
        when(mockedMailboxPathRegister.getLocalTopic()).thenReturn(TOPIC);
        testee.event(event);
        assertThat(onceEventCollector.getEvents()).containsOnly(event);
        verify(mockedMailboxPathRegister, times(2)).getLocalTopic();
        verify(mockedMailboxPathRegister).getTopics(MAILBOX_PATH);
        verifyNoMoreInteractions(mockedEventSerializer);
        verifyNoMoreInteractions(mockedPublisher);
        verifyNoMoreInteractions(mockedMailboxPathRegister);
    }

    @Test(expected = MailboxException.class)
    public void eachNodeListenersShouldBeRejected() throws Exception {
        testee.addGlobalListener(eachEventCollector, mailboxSession);
    }

    @Test
    public void distantEventShouldBeLocallyDelivered() throws Exception {
        testee.addListener(MAILBOX_PATH, mailboxEventCollector, mailboxSession);
        verify(mockedMailboxPathRegister).register(MAILBOX_PATH);
        when(mockedEventSerializer.deSerializeEvent(BYTES)).thenReturn(event);
        testee.receiveSerializedEvent(BYTES);
        assertThat(mailboxEventCollector.getEvents()).containsOnly(event);
        verify(mockedMailboxPathRegister).getLocalTopic();
        verify(mockedEventSerializer).deSerializeEvent(BYTES);
        verifyNoMoreInteractions(mockedEventSerializer);
        verifyNoMoreInteractions(mockedPublisher);
        verifyNoMoreInteractions(mockedMailboxPathRegister);
    }


    @Test
    public void distantEventShouldNotBeDeliveredToOnceGlobalListeners() throws Exception {
        testee.addGlobalListener(onceEventCollector, mailboxSession);
        when(mockedEventSerializer.deSerializeEvent(BYTES)).thenReturn(event);
        testee.receiveSerializedEvent(BYTES);
        assertThat(onceEventCollector.getEvents()).isEmpty();
        verify(mockedMailboxPathRegister).getLocalTopic();
        verify(mockedEventSerializer).deSerializeEvent(BYTES);
        verifyNoMoreInteractions(mockedEventSerializer);
        verifyNoMoreInteractions(mockedPublisher);
        verifyNoMoreInteractions(mockedMailboxPathRegister);
    }

    @Test
    public void deletionEventsShouldBeWellHandled() throws Exception {
        MailboxListener.Event event = new MailboxListener.MailboxDeletion(mailboxSession, MAILBOX_PATH);
        testee.addListener(MAILBOX_PATH, mailboxEventCollector, mailboxSession);
        verify(mockedMailboxPathRegister).register(MAILBOX_PATH);
        when(mockedMailboxPathRegister.getTopics(MAILBOX_PATH)).thenReturn(Sets.newHashSet(TOPIC, TOPIC_2));
        when(mockedMailboxPathRegister.getLocalTopic()).thenReturn(TOPIC);
        when(mockedEventSerializer.serializeEvent(event)).thenReturn(BYTES);
        testee.event(event);
        assertThat(mailboxEventCollector.getEvents()).containsOnly(event);
        verify(mockedMailboxPathRegister, times(2)).getLocalTopic();
        verify(mockedMailboxPathRegister).getTopics(MAILBOX_PATH);
        verify(mockedMailboxPathRegister).doCompleteUnRegister(MAILBOX_PATH);
        verify(mockedEventSerializer).serializeEvent(event);
        verify(mockedPublisher).publish(TOPIC_2, BYTES);
        verifyNoMoreInteractions(mockedEventSerializer);
        verifyNoMoreInteractions(mockedPublisher);
        verifyNoMoreInteractions(mockedMailboxPathRegister);
    }

    @Test
    public void renameEventsShouldBeWellHandled() throws Exception {
        MailboxListener.Event event = new MailboxListener.MailboxRenamed(mailboxSession, MAILBOX_PATH) {
            @Override
            public MailboxPath getNewPath() {
                return MAILBOX_PATH_NEW;
            }
        };
        testee.addListener(MAILBOX_PATH, mailboxEventCollector, mailboxSession);
        verify(mockedMailboxPathRegister).register(MAILBOX_PATH);
        when(mockedMailboxPathRegister.getTopics(MAILBOX_PATH)).thenReturn(Sets.newHashSet(TOPIC, TOPIC_2));
        when(mockedMailboxPathRegister.getLocalTopic()).thenReturn(TOPIC);
        when(mockedEventSerializer.serializeEvent(event)).thenReturn(BYTES);
        testee.event(event);
        assertThat(mailboxEventCollector.getEvents()).containsOnly(event);
        verify(mockedMailboxPathRegister, times(2)).getLocalTopic();
        verify(mockedMailboxPathRegister).getTopics(MAILBOX_PATH);
        verify(mockedMailboxPathRegister).doRename(MAILBOX_PATH, MAILBOX_PATH_NEW);
        verify(mockedEventSerializer).serializeEvent(event);
        verify(mockedPublisher).publish(TOPIC_2, BYTES);
        verifyNoMoreInteractions(mockedEventSerializer);
        verifyNoMoreInteractions(mockedPublisher);
        verifyNoMoreInteractions(mockedMailboxPathRegister);
    }
}
