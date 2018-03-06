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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TreeMap;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.modules.CassandraRegistrationModule;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.TestIdDeserializer;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.event.distributed.DistantMailboxPathRegister;
import org.apache.james.mailbox.store.event.distributed.PublisherReceiver;
import org.apache.james.mailbox.store.event.distributed.RegisteredDelegatingMailboxListener;
import org.apache.james.mailbox.store.json.MessagePackEventSerializer;
import org.apache.james.mailbox.store.json.event.EventConverter;
import org.apache.james.mailbox.store.json.event.MailboxConverter;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.util.EventCollector;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

/**
 Integration tests for RegisteredDelegatingMailboxListener using a cassandra back-end.

 We simulate communications using message queues in memory and check the Listener works as intended.
 */
public class CassandraBasedRegisteredDistributedMailboxDelegatingListenerTest {

    public static final MailboxPath MAILBOX_PATH_1 = MailboxPath.forUser("user", "mbx");
    public static final MailboxPath MAILBOX_PATH_2 = MailboxPath.forUser("user", "mbx.other");
    public static final int CASSANDRA_TIME_OUT_IN_S = 10;
    public static final int SCHEDULER_PERIOD_IN_S = 20;
    public static final ImmutableMap<MessageUid, MailboxMessage> EMPTY_MESSAGE_CACHE = ImmutableMap.of();

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    
    private CassandraCluster cassandra = CassandraCluster.create(new CassandraRegistrationModule(), cassandraServer.getIp(), cassandraServer.getBindingPort());
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
                cassandra.getConf(),
                cassandra.getTypesProvider(),
                CassandraUtils.WITH_DEFAULT_CONFIGURATION,
                CASSANDRA_TIME_OUT_IN_S),
            SCHEDULER_PERIOD_IN_S);
        registeredDelegatingMailboxListener1 = new RegisteredDelegatingMailboxListener(
            new MessagePackEventSerializer(
                new EventConverter(new MailboxConverter(new TestIdDeserializer())),
                new TestMessageId.Factory()
            ),
            publisherReceiver,
            publisherReceiver,
            mailboxPathRegister1);
        DistantMailboxPathRegister mailboxPathRegister2 = new DistantMailboxPathRegister(
            new CassandraMailboxPathRegisterMapper(
                cassandra.getConf(),
                cassandra.getTypesProvider(),
                CassandraUtils.WITH_DEFAULT_CONFIGURATION,
                CASSANDRA_TIME_OUT_IN_S),
            SCHEDULER_PERIOD_IN_S);
        registeredDelegatingMailboxListener2 = new RegisteredDelegatingMailboxListener(
            new MessagePackEventSerializer(
                new EventConverter(new MailboxConverter(new TestIdDeserializer())),
                new TestMessageId.Factory()
            ),
            publisherReceiver,
            publisherReceiver,
            mailboxPathRegister2);
        DistantMailboxPathRegister mailboxPathRegister3 = new DistantMailboxPathRegister(
            new CassandraMailboxPathRegisterMapper(
                cassandra.getConf(),
                cassandra.getTypesProvider(),
                CassandraUtils.WITH_DEFAULT_CONFIGURATION,
                CASSANDRA_TIME_OUT_IN_S),
            SCHEDULER_PERIOD_IN_S);
        registeredDelegatingMailboxListener3 = new RegisteredDelegatingMailboxListener(
            new MessagePackEventSerializer(
                new EventConverter(new MailboxConverter(new TestIdDeserializer())),
                new TestMessageId.Factory()
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

    @After
    public void tearDown() {
        cassandra.close();
    }

    @Test
    public void mailboxEventListenersShouldBeTriggeredIfRegistered() throws Exception {
        SimpleMailbox simpleMailbox = new SimpleMailbox(MAILBOX_PATH_1, 42);
        simpleMailbox.setMailboxId(TestId.of(52));
        TreeMap<MessageUid, MessageMetaData> uids = new TreeMap<>();
        final MailboxListener.MailboxEvent event = new EventFactory().added(mailboxSession, uids, simpleMailbox, EMPTY_MESSAGE_CACHE);

        registeredDelegatingMailboxListener1.event(event);

        assertThat(eventCollectorMailbox1.getEvents()).hasSize(1);
        assertThat(eventCollectorMailbox2.getEvents()).hasSize(1);
        assertThat(eventCollectorMailbox3.getEvents()).isEmpty();
    }

    @Test
    public void onceEventListenersShouldBeTriggeredOnceAcrossTheCluster() {
        SimpleMailbox simpleMailbox = new SimpleMailbox(MAILBOX_PATH_1, 42);
        simpleMailbox.setMailboxId(TestId.of(52));
        TreeMap<MessageUid, MessageMetaData> uids = new TreeMap<>();
        final MailboxListener.MailboxEvent event = new EventFactory().added(mailboxSession, uids, simpleMailbox, EMPTY_MESSAGE_CACHE);

        registeredDelegatingMailboxListener1.event(event);

        assertThat(eventCollectorOnce1.getEvents()).hasSize(1);
        assertThat(eventCollectorOnce2.getEvents()).isEmpty();
        assertThat(eventCollectorOnce3.getEvents()).isEmpty();
    }

}
