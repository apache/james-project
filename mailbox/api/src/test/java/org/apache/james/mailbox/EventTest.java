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

package org.apache.james.mailbox;

import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_APPENDED;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_DELIVERY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSortedMap;

import nl.jqno.equalsverifier.EqualsVerifier;

class EventTest {
    private static final UUID UUID_1 = UUID.fromString("6e0dd59d-660e-4d9b-b22f-0354479f47b4");
    public static final Username BOB = Username.of("BOB");

    @Test
    void eventIdShouldMatchBeanContract() {
        EqualsVerifier.forClass(Event.EventId.class).verify();
    }

    @Test
    void ofShouldDeserializeUUIDs() {
        assertThat(Event.EventId.of(UUID_1.toString()))
            .isEqualTo(Event.EventId.of(UUID_1));
    }

    @Test
    void getMessageIdsShouldReturnEmptyWhenAddedEmpty() {
        Added added = new Added(MailboxSession.SessionId.of(36), BOB, MailboxPath.inbox(BOB),
            TestId.of(48), ImmutableSortedMap.of(), Event.EventId.of(UUID_1),
            !IS_DELIVERY, IS_APPENDED, Optional.empty());

        assertThat(added.getMessageIds()).isEmpty();
    }

    @Test
    void getMessageIdsShouldReturnDistinctValues() {
        MessageUid uid1 = MessageUid.of(36);
        MessageUid uid2 = MessageUid.of(37);
        TestMessageId messageId1 = TestMessageId.of(45);
        TestMessageId messageId2 = TestMessageId.of(46);
        MessageMetaData metaData1 = new MessageMetaData(uid1, ModSeq.of(85), new Flags(), 36, new Date(), Optional.empty(), messageId1, ThreadId.fromBaseMessageId(messageId1));
        MessageMetaData metaData2 = new MessageMetaData(uid2, ModSeq.of(85), new Flags(), 36, new Date(), Optional.empty(), messageId2, ThreadId.fromBaseMessageId(messageId2));

        Added added = new Added(MailboxSession.SessionId.of(36), BOB, MailboxPath.inbox(BOB), TestId.of(48),
            ImmutableSortedMap.of(
                uid1, metaData1,
                uid2, metaData2),
            Event.EventId.of(UUID_1), !IS_DELIVERY, IS_APPENDED, Optional.empty());

        assertThat(added.getMessageIds()).containsOnly(messageId1, messageId2);
    }

    @Test
    void getMessageIdsShouldNotReturnDuplicates() {
        MessageUid uid1 = MessageUid.of(36);
        MessageUid uid2 = MessageUid.of(37);
        TestMessageId messageId = TestMessageId.of(45);
        MessageMetaData metaData1 = new MessageMetaData(uid1, ModSeq.of(85), new Flags(), 36, new Date(), Optional.empty(), messageId, ThreadId.fromBaseMessageId(messageId));
        MessageMetaData metaData2 = new MessageMetaData(uid2, ModSeq.of(85), new Flags(), 36, new Date(), Optional.empty(), messageId, ThreadId.fromBaseMessageId(messageId));

        Added added = new Added(MailboxSession.SessionId.of(36), BOB, MailboxPath.inbox(BOB), TestId.of(48),
            ImmutableSortedMap.of(
                uid1, metaData1,
                uid2, metaData2),
            Event.EventId.of(UUID_1), !IS_DELIVERY, IS_APPENDED, Optional.empty());

        assertThat(added.getMessageIds()).containsExactly(messageId);
    }
}