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

import java.util.TreeMap;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.TestIdDeserializer;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.json.MessagePackEventSerializer;
import org.apache.james.mailbox.store.json.event.EventConverter;
import org.apache.james.mailbox.store.json.event.MailboxConverter;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.util.EventCollector;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

/**
 Integration tests for BroadcastDelegatingMailboxListener.

 We simulate communications using message queues in memory and check the Listener works as intended.
 */
public class BroadcastDelegatingMailboxListenerIntegrationTest {

    public static final MailboxPath MAILBOX_PATH_1 = new MailboxPath("#private", "user", "mbx");
    public static final MailboxPath MAILBOX_PATH_2 = new MailboxPath("#private", "user", "mbx.other");
    public static final String TOPIC = "TOPIC";
    public static final ImmutableMap<MessageUid, MailboxMessage> EMPTY_MESSAGE_CACHE = ImmutableMap.<MessageUid, MailboxMessage>of();
    private BroadcastDelegatingMailboxListener broadcastDelegatingMailboxListener1;
    private BroadcastDelegatingMailboxListener broadcastDelegatingMailboxListener2;
    private BroadcastDelegatingMailboxListener broadcastDelegatingMailboxListener3;
    private EventCollector eventCollectorMailbox1;
    private EventCollector eventCollectorMailbox2;
    private EventCollector eventCollectorMailbox3;
    private EventCollector eventCollectorOnce1;
    private EventCollector eventCollectorOnce2;
    private EventCollector eventCollectorOnce3;
    private EventCollector eventCollectorEach1;
    private EventCollector eventCollectorEach2;
    private EventCollector eventCollectorEach3;
    private MailboxSession mailboxSession;

    @Before
    public void setUp() throws Exception {
        PublisherReceiver publisherReceiver = new PublisherReceiver();
        broadcastDelegatingMailboxListener1 = new BroadcastDelegatingMailboxListener(publisherReceiver,
            publisherReceiver,
            new MessagePackEventSerializer(
                new EventConverter(new MailboxConverter(new TestIdDeserializer())),
                new TestMessageId.Factory()
            ),
            TOPIC);
        broadcastDelegatingMailboxListener2 = new BroadcastDelegatingMailboxListener(publisherReceiver,
            publisherReceiver,
            new MessagePackEventSerializer(
                new EventConverter(new MailboxConverter(new TestIdDeserializer())),
                new TestMessageId.Factory()
            ),
            TOPIC);
        broadcastDelegatingMailboxListener3 = new BroadcastDelegatingMailboxListener(publisherReceiver,
            publisherReceiver,
            new MessagePackEventSerializer(
                new EventConverter(new MailboxConverter(new TestIdDeserializer())),
                new TestMessageId.Factory()
            ),
            TOPIC);
        eventCollectorMailbox1 = new EventCollector(MailboxListener.ListenerType.MAILBOX);
        eventCollectorMailbox2 = new EventCollector(MailboxListener.ListenerType.MAILBOX);
        eventCollectorMailbox3 = new EventCollector(MailboxListener.ListenerType.MAILBOX);
        eventCollectorOnce1 = new EventCollector(MailboxListener.ListenerType.ONCE);
        eventCollectorOnce2 = new EventCollector(MailboxListener.ListenerType.ONCE);
        eventCollectorOnce3 = new EventCollector(MailboxListener.ListenerType.ONCE);
        eventCollectorEach1 = new EventCollector(MailboxListener.ListenerType.EACH_NODE);
        eventCollectorEach2 = new EventCollector(MailboxListener.ListenerType.EACH_NODE);
        eventCollectorEach3 = new EventCollector(MailboxListener.ListenerType.EACH_NODE);
        mailboxSession = new MockMailboxSession("Test");
        broadcastDelegatingMailboxListener1.addGlobalListener(eventCollectorOnce1, mailboxSession);
        broadcastDelegatingMailboxListener2.addGlobalListener(eventCollectorOnce2, mailboxSession);
        broadcastDelegatingMailboxListener3.addGlobalListener(eventCollectorOnce3, mailboxSession);
        broadcastDelegatingMailboxListener1.addGlobalListener(eventCollectorEach1, mailboxSession);
        broadcastDelegatingMailboxListener2.addGlobalListener(eventCollectorEach2, mailboxSession);
        broadcastDelegatingMailboxListener3.addGlobalListener(eventCollectorEach3, mailboxSession);
        broadcastDelegatingMailboxListener1.addListener(MAILBOX_PATH_1, eventCollectorMailbox1, mailboxSession);
        broadcastDelegatingMailboxListener2.addListener(MAILBOX_PATH_1, eventCollectorMailbox2, mailboxSession);
        broadcastDelegatingMailboxListener3.addListener(MAILBOX_PATH_2, eventCollectorMailbox3, mailboxSession);
    }

    @Test
    public void mailboxEventListenersShouldBeTriggeredIfRegistered() throws Exception {
        SimpleMailbox simpleMailbox = new SimpleMailbox(MAILBOX_PATH_1, 42);
        simpleMailbox.setMailboxId(TestId.of(52));
        final MailboxListener.Event event = new EventFactory().added(mailboxSession, new TreeMap<>(), simpleMailbox, EMPTY_MESSAGE_CACHE);

        broadcastDelegatingMailboxListener1.event(event);

        assertThat(eventCollectorMailbox1.getEvents()).hasSize(1);
        assertThat(eventCollectorMailbox2.getEvents()).hasSize(1);
        assertThat(eventCollectorMailbox3.getEvents()).isEmpty();
    }

    @Test
    public void onceEventListenersShouldBeTriggeredOnceAcrossTheCluster() {
        SimpleMailbox simpleMailbox = new SimpleMailbox(MAILBOX_PATH_1, 42);
        simpleMailbox.setMailboxId(TestId.of(52));
        final MailboxListener.Event event = new EventFactory().added(mailboxSession, new TreeMap<>(), simpleMailbox, EMPTY_MESSAGE_CACHE);

        broadcastDelegatingMailboxListener1.event(event);

        assertThat(eventCollectorOnce1.getEvents()).hasSize(1);
        assertThat(eventCollectorOnce2.getEvents()).isEmpty();
        assertThat(eventCollectorOnce3.getEvents()).isEmpty();
    }

    @Test
    public void eachEventListenersShouldBeTriggeredOnEachNode() {
        SimpleMailbox simpleMailbox = new SimpleMailbox(MAILBOX_PATH_1, 42);
        simpleMailbox.setMailboxId(TestId.of(52));
        final MailboxListener.Event event = new EventFactory().added(mailboxSession, new TreeMap<>(), simpleMailbox, EMPTY_MESSAGE_CACHE);

        broadcastDelegatingMailboxListener1.event(event);

        assertThat(eventCollectorEach1.getEvents()).hasSize(1);
        assertThat(eventCollectorEach2.getEvents()).hasSize(1);
        assertThat(eventCollectorEach3.getEvents()).hasSize(1);
    }

}
