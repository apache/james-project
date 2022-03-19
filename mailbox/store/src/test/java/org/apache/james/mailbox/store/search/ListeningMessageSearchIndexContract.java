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

package org.apache.james.mailbox.store.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import jakarta.mail.Flags;

import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.Test;

public interface ListeningMessageSearchIndexContract {
    ModSeq MOD_SEQ = ModSeq.of(42L);
    int SIZE = 25;
    int BODY_START_OCTET = 100;
    MessageId MESSAGE_ID = TestMessageId.of(21L);
    ThreadId THREAD_ID = ThreadId.fromBaseMessageId(MESSAGE_ID);
    MessageUid MESSAGE_UID = MessageUid.of(28);
    
    SimpleMailboxMessage.Builder MESSAGE_BUILDER = SimpleMailboxMessage.builder()
        .messageId(MESSAGE_ID)
        .threadId(THREAD_ID)
        .uid(MESSAGE_UID)
        .bodyStartOctet(BODY_START_OCTET)
        .internalDate(new Date(1433628000000L))
        .size(SIZE)
        .content(new ByteContent("message".getBytes(StandardCharsets.UTF_8)))
        .properties(new PropertyBuilder())
        .modseq(MOD_SEQ);
    
    ListeningMessageSearchIndex testee();

    MailboxSession session();

    Mailbox mailbox();
    
    @Test
    default void retrieveIndexedFlagsShouldRetrieveSystemFlags() {
        Flags flags = FlagsBuilder.builder()
            .add(Flags.Flag.RECENT)
            .add(Flags.Flag.DRAFT)
            .build();

        SimpleMailboxMessage message = MESSAGE_BUILDER.mailboxId(mailbox().getMailboxId())
            .flags(flags)
            .build();

        testee().add(session(), mailbox(), message).block();

        assertThat(testee().retrieveIndexedFlags(mailbox(), MESSAGE_UID).block())
            .isEqualTo(flags);
    }

    @Test
    default void retrieveIndexedFlagsShouldReturnEmptyFlagsWhenNoFlags() {
        Flags flags = new Flags();

        SimpleMailboxMessage message = MESSAGE_BUILDER.mailboxId(mailbox().getMailboxId())
            .flags(flags)
            .build();

        testee().add(session(), mailbox(), message).block();

        assertThat(testee().retrieveIndexedFlags(mailbox(), MESSAGE_UID).block())
            .isEqualTo(flags);
    }

    @Test
    default void retrieveIndexedFlagsShouldReturnAllSystemFlagsWhenAllFlagsSet() {
        Flags flags = FlagsBuilder.builder()
            .add(Flags.Flag.ANSWERED)
            .add(Flags.Flag.DELETED)
            .add(Flags.Flag.RECENT)
            .add(Flags.Flag.DRAFT)
            .add(Flags.Flag.FLAGGED)
            .add(Flags.Flag.SEEN)
            .build();

        SimpleMailboxMessage message = MESSAGE_BUILDER.mailboxId(mailbox().getMailboxId())
            .flags(flags)
            .build();

        testee().add(session(), mailbox(), message).block();

        assertThat(testee().retrieveIndexedFlags(mailbox(), MESSAGE_UID).block())
            .isEqualTo(flags);
    }

    @Test
    default void retrieveIndexedFlagsShouldReturnUserFlags() {
        Flags flags = FlagsBuilder.builder()
            .add("flag1")
            .add("flag2")
            .build();

        SimpleMailboxMessage message = MESSAGE_BUILDER.mailboxId(mailbox().getMailboxId())
            .flags(flags)
            .build();

        testee().add(session(), mailbox(), message).block();

        assertThat(testee().retrieveIndexedFlags(mailbox(), MESSAGE_UID).block())
            .isEqualTo(flags);
    }

    @Test
    default void retrieveIndexedFlagsShouldReturnUserAndSystemFlags() {
        Flags flags = FlagsBuilder.builder()
            .add(Flags.Flag.ANSWERED)
            .add(Flags.Flag.DELETED)
            .add("flag1")
            .add("flag2")
            .build();

        SimpleMailboxMessage message = MESSAGE_BUILDER.mailboxId(mailbox().getMailboxId())
            .flags(flags)
            .build();

        testee().add(session(), mailbox(), message).block();

        assertThat(testee().retrieveIndexedFlags(mailbox(), MESSAGE_UID).block())
            .isEqualTo(flags);
    }
}
