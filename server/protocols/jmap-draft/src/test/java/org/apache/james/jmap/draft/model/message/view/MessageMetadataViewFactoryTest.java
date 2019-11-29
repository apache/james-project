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

package org.apache.james.jmap.draft.model.message.view;

import static org.apache.james.jmap.draft.model.message.view.MessageViewFixture.BOB;

import javax.mail.Flags;

import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.jmap.draft.model.Keyword;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.model.Number;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class MessageMetadataViewFactoryTest {
    private MessageIdManager messageIdManager;
    private MessageMetadataViewFactory testee;
    private MailboxSession session;
    private MessageManager bobInbox;
    private MessageManager bobMailbox;
    private ComposedMessageId message1;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        messageIdManager = resources.getMessageIdManager();
        InMemoryMailboxManager mailboxManager = resources.getMailboxManager();

        session = mailboxManager.createSystemSession(BOB);
        MailboxId bobInboxId = mailboxManager.createMailbox(MailboxPath.inbox(session), session).get();
        MailboxId bobMailboxId = mailboxManager.createMailbox(MailboxPath.forUser(BOB, "anotherMailbox"), session).get();

        bobInbox = mailboxManager.getMailbox(bobInboxId, session);
        bobMailbox = mailboxManager.getMailbox(bobMailboxId, session);

        message1 = bobInbox.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags(Flags.Flag.SEEN))
                .build("header: value\r\n\r\nbody"),
            session);

        testee = new MessageMetadataViewFactory(resources.getBlobManager(), messageIdManager);
    }

    @Test
    void fromMessageResultsShouldReturnCorrectView() throws Exception {
        MessageMetadataView actual = testee.fromMessageIds(ImmutableList.of(message1.getMessageId()), session).get(0);
        SoftAssertions.assertSoftly(softly -> {
           softly.assertThat(actual.getId()).isEqualTo(message1.getMessageId());
           softly.assertThat(actual.getMailboxIds()).containsExactly(bobInbox.getId());
           softly.assertThat(actual.getThreadId()).isEqualTo(message1.getMessageId().serialize());
           softly.assertThat(actual.getSize()).isEqualTo(Number.fromLong(21));
           softly.assertThat(actual.getKeywords()).isEqualTo(Keywords.strictFactory().from(Keyword.SEEN).asMap());
           softly.assertThat(actual.getBlobId()).isEqualTo(BlobId.of(message1.getMessageId().serialize()));
        });
    }

    @Test
    void fromMessageResultsShouldCombineKeywords() throws Exception {
        messageIdManager.setInMailboxes(message1.getMessageId(), ImmutableList.of(bobInbox.getId(), bobMailbox.getId()), session);
        bobMailbox.setFlags(new Flags(Flags.Flag.FLAGGED), MessageManager.FlagsUpdateMode.REPLACE, MessageRange.all(), session);

        MessageMetadataView actual = testee.fromMessageIds(ImmutableList.of(message1.getMessageId()), session).get(0);
        SoftAssertions.assertSoftly(softly -> {
           softly.assertThat(actual.getId()).isEqualTo(message1.getMessageId());
           softly.assertThat(actual.getKeywords()).isEqualTo(Keywords.strictFactory().from(Keyword.SEEN, Keyword.FLAGGED).asMap());
        });
    }

}