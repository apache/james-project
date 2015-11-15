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

package org.apache.james.mailbox.store.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Lists;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.event.EventSerializer;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Flags;
import java.util.TreeMap;

public abstract class EventSerializerTest {

    public static final long UID = 42L;
    public static final long MOD_SEQ = 24L;
    public static final UpdatedFlags UPDATED_FLAGS = new UpdatedFlags(UID, MOD_SEQ, new Flags(), new Flags(Flags.Flag.SEEN));
    public static final Flags FLAGS = new Flags();
    public static final long SIZE = 45L;
    public static final SimpleMessageMetaData MESSAGE_META_DATA = new SimpleMessageMetaData(UID, MOD_SEQ, FLAGS, SIZE, null);
    public static final MailboxPath FROM = new MailboxPath("namespace", "user", "name");
    private EventSerializer serializer;
    private EventFactory<TestId> eventFactory;
    private MailboxSession mailboxSession;
    private SimpleMailbox<TestId> mailbox;

    abstract EventSerializer createSerializer();

    @Before
    public void setUp() {
        eventFactory = new EventFactory<TestId>();
        serializer = createSerializer();
        mailboxSession = new MockMailboxSession("benwa");
        mailbox = new SimpleMailbox<TestId>(new MailboxPath("#private", "benwa", "name"), 42);
        mailbox.setMailboxId(TestId.of(28L));
    }

    @Test
    public void addedEventShouldBeWellConverted() throws Exception {
        TreeMap<Long, MessageMetaData> treeMap = new TreeMap<Long, MessageMetaData>();
        treeMap.put(UID, MESSAGE_META_DATA);
        MailboxListener.Event event = eventFactory.added(mailboxSession, treeMap, mailbox);
        byte[] serializedEvent = serializer.serializeEvent(event);
        MailboxListener.Event deserializedEvent = serializer.deSerializeEvent(serializedEvent);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
        assertThat(deserializedEvent.getSession().getSessionId()).isEqualTo(event.getSession().getSessionId());
        assertThat(deserializedEvent).isInstanceOf(MailboxListener.Added.class);
        assertThat(((MailboxListener.Added)deserializedEvent).getUids()).containsOnly(UID);
        assertThat(((MailboxListener.Added)deserializedEvent).getMetaData(UID)).isEqualTo(MESSAGE_META_DATA);
    }

    @Test
    public void expungedEventShouldBeWellConverted() throws Exception {
        TreeMap<Long, MessageMetaData> treeMap = new TreeMap<Long, MessageMetaData>();
        treeMap.put(UID, MESSAGE_META_DATA);
        MailboxListener.Event event = eventFactory.expunged(mailboxSession, treeMap, mailbox);
        byte[] serializedEvent = serializer.serializeEvent(event);
        MailboxListener.Event deserializedEvent = serializer.deSerializeEvent(serializedEvent);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
        assertThat(deserializedEvent.getSession().getSessionId()).isEqualTo(event.getSession().getSessionId());
        assertThat(deserializedEvent).isInstanceOf(MailboxListener.Expunged.class);
        assertThat(((MailboxListener.Expunged)deserializedEvent).getUids()).containsOnly(UID);
        assertThat(((MailboxListener.Expunged)deserializedEvent).getMetaData(UID)).isEqualTo(MESSAGE_META_DATA);
    }

    @Test
    public void flagsUpdatedEventShouldBeWellConverted() throws Exception {
        MailboxListener.Event event = eventFactory.flagsUpdated(mailboxSession, Lists.newArrayList(UID), mailbox, Lists.newArrayList(UPDATED_FLAGS));
        byte[] serializedEvent = serializer.serializeEvent(event);
        MailboxListener.Event deserializedEvent = serializer.deSerializeEvent(serializedEvent);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
        assertThat(deserializedEvent.getSession().getSessionId()).isEqualTo(event.getSession().getSessionId());
        assertThat(deserializedEvent).isInstanceOf(MailboxListener.FlagsUpdated.class);
        assertThat(((MailboxListener.FlagsUpdated)event).getUpdatedFlags()).containsOnly(UPDATED_FLAGS);
    }

    @Test
    public void mailboxAddedShouldBeWellConverted() throws Exception {
        MailboxListener.Event event = eventFactory.mailboxAdded(mailboxSession, mailbox);
        byte[] serializedEvent = serializer.serializeEvent(event);
        MailboxListener.Event deserializedEvent = serializer.deSerializeEvent(serializedEvent);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
        assertThat(deserializedEvent.getSession().getSessionId()).isEqualTo(event.getSession().getSessionId());
        assertThat(deserializedEvent).isInstanceOf(MailboxListener.MailboxAdded.class);
    }

    @Test
    public void mailboxDeletionShouldBeWellConverted() throws Exception {
        MailboxListener.Event event = eventFactory.mailboxDeleted(mailboxSession, mailbox);
        byte[] serializedEvent = serializer.serializeEvent(event);
        MailboxListener.Event deserializedEvent = serializer.deSerializeEvent(serializedEvent);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
        assertThat(deserializedEvent.getSession().getSessionId()).isEqualTo(event.getSession().getSessionId());
        assertThat(deserializedEvent).isInstanceOf(MailboxListener.MailboxDeletion.class);
    }

    @Test
    public void mailboxRenamedShouldBeWellConverted() throws Exception {
        MailboxListener.Event event = eventFactory.mailboxRenamed(mailboxSession, FROM, mailbox);
        byte[] serializedEvent = serializer.serializeEvent(event);
        MailboxListener.Event deserializedEvent = serializer.deSerializeEvent(serializedEvent);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
        assertThat(deserializedEvent.getSession().getSessionId()).isEqualTo(event.getSession().getSessionId());
        assertThat(deserializedEvent).isInstanceOf(MailboxListener.MailboxRenamed.class);
        assertThat(deserializedEvent.getMailboxPath()).isEqualTo(event.getMailboxPath());
    }

}
