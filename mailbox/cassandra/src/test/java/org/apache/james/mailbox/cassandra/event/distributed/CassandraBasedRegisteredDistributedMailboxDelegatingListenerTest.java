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

package org.apache.james.mailbox.cassandra.event.distributed;

import org.apache.james.backends.cassandra.CassandraClusterSingleton;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.CassandraMailboxModule;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.TestIdDeserializer;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.event.distributed.DistantMailboxPathRegister;
import org.apache.james.mailbox.store.event.distributed.PublisherReceiver;
import org.apache.james.mailbox.store.event.distributed.RegisteredDelegatingMailboxListener;
import org.apache.james.mailbox.store.json.MessagePackEventSerializer;
import org.apache.james.mailbox.store.json.event.MailboxConverter;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.util.EventCollector;
import org.junit.Before;
import org.junit.Test;

import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 Integration tests for RegisteredDelegatingMailboxListener using a cassandra back-end.

 We simulate communications using message queues in memory and check the Listener works as intended.
 */
public class CassandraBasedRegisteredDistributedMailboxDelegatingListenerTest {

    public static final MailboxPath MAILBOX_PATH_1 = new MailboxPath("#private", "user", "mbx");
    public static final MailboxPath MAILBOX_PATH_2 = new MailboxPath("#private", "user", "mbx.other");

    private CassandraClusterSingleton cassandraClusterSingleton = CassandraClusterSingleton.create(new CassandraMailboxModule());
    private RegisteredDelegatingMailboxListener registeredDelegatingMailboxListener1;
    private RegisteredDelegatingMailboxListener registeredDelegatingMailboxListener2;
    private RegisteredDelegatingMailboxListener registeredDelegatingMailboxListener3;
    private EventCollector eventCollectorMailbox1;
    private EventCollector eventCollectorMailbox2;
    private EventCollector eventCollectorMailbox3;
    private EventCollector eventCollectorOnce1;
    private EventCollector eventCollectorOnce2;
    private EventCollector eventCollectorOnce3;
    private MailboxSession mailboxSession;

    @Before
    public void setUp() throws Exception {
        PublisherReceiver publisherReceiver = new PublisherReceiver();
        DistantMailboxPathRegister mailboxPathRegister1 = new DistantMailboxPathRegister(
            new CassandraMailboxPathRegisterMapper(
                cassandraClusterSingleton.getConf(),
                cassandraClusterSingleton.getTypesProvider()));
        registeredDelegatingMailboxListener1 = new RegisteredDelegatingMailboxListener(
            new MessagePackEventSerializer<>(
                new MailboxConverter<>(new TestIdDeserializer())
            ),
            publisherReceiver,
            publisherReceiver,
            mailboxPathRegister1);
        DistantMailboxPathRegister mailboxPathRegister2 = new DistantMailboxPathRegister(
            new CassandraMailboxPathRegisterMapper(
                cassandraClusterSingleton.getConf(),
                cassandraClusterSingleton.getTypesProvider()));
        registeredDelegatingMailboxListener2 = new RegisteredDelegatingMailboxListener(
            new MessagePackEventSerializer<>(
                new MailboxConverter<>(new TestIdDeserializer())
            ),
            publisherReceiver,
            publisherReceiver,
            mailboxPathRegister2);
        DistantMailboxPathRegister mailboxPathRegister3 = new DistantMailboxPathRegister(
            new CassandraMailboxPathRegisterMapper(
                cassandraClusterSingleton.getConf(),
                cassandraClusterSingleton.getTypesProvider()));
        registeredDelegatingMailboxListener3 = new RegisteredDelegatingMailboxListener(
            new MessagePackEventSerializer<>(
                new MailboxConverter<>(new TestIdDeserializer())
            ),
            publisherReceiver,
            publisherReceiver,
            mailboxPathRegister3);
        eventCollectorMailbox1 = new EventCollector(MailboxListener.ListenerType.MAILBOX);
        eventCollectorMailbox2 = new EventCollector(MailboxListener.ListenerType.MAILBOX);
        eventCollectorMailbox3 = new EventCollector(MailboxListener.ListenerType.MAILBOX);
        eventCollectorOnce1 = new EventCollector(MailboxListener.ListenerType.ONCE);
        eventCollectorOnce2 = new EventCollector(MailboxListener.ListenerType.ONCE);
        eventCollectorOnce3 = new EventCollector(MailboxListener.ListenerType.ONCE);
        mailboxSession = new MockMailboxSession("Test");
        registeredDelegatingMailboxListener1.addGlobalListener(eventCollectorOnce1, mailboxSession);
        registeredDelegatingMailboxListener2.addGlobalListener(eventCollectorOnce2, mailboxSession);
        registeredDelegatingMailboxListener3.addGlobalListener(eventCollectorOnce3, mailboxSession);
        registeredDelegatingMailboxListener1.addListener(MAILBOX_PATH_1, eventCollectorMailbox1, mailboxSession);
        registeredDelegatingMailboxListener2.addListener(MAILBOX_PATH_1, eventCollectorMailbox2, mailboxSession);
        registeredDelegatingMailboxListener3.addListener(MAILBOX_PATH_2, eventCollectorMailbox3, mailboxSession);
    }

    @Test
    public void mailboxEventListenersShouldBeTriggeredIfRegistered() throws Exception {
        SimpleMailbox<TestId> simpleMailbox = new SimpleMailbox<>(MAILBOX_PATH_1, 42);
        simpleMailbox.setMailboxId(TestId.of(52));
        final MailboxListener.Event event = new EventFactory<TestId>().added(mailboxSession, new TreeMap<>(), simpleMailbox);

        registeredDelegatingMailboxListener1.event(event);

        assertThat(eventCollectorMailbox1.getEvents()).hasSize(1);
        assertThat(eventCollectorMailbox2.getEvents()).hasSize(1);
        assertThat(eventCollectorMailbox3.getEvents()).isEmpty();
    }

    @Test
    public void onceEventListenersShouldBeTriggeredOnceAcrossTheCluster() {
        SimpleMailbox<TestId> simpleMailbox = new SimpleMailbox<>(MAILBOX_PATH_1, 42);
        simpleMailbox.setMailboxId(TestId.of(52));
        final MailboxListener.Event event = new EventFactory<TestId>().added(mailboxSession, new TreeMap<>(), simpleMailbox);

        registeredDelegatingMailboxListener1.event(event);

        assertThat(eventCollectorOnce1.getEvents()).hasSize(1);
        assertThat(eventCollectorOnce2.getEvents()).isEmpty();
        assertThat(eventCollectorOnce3.getEvents()).isEmpty();
    }

}
