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

import java.util.List;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.jmap.draft.model.Emailer;
import org.apache.james.jmap.draft.model.Keyword;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.model.Number;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.util.ClassLoaderUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class MessageHeaderViewFactoryTest {
    private static final Username BOB = Username.of("bob@local");

    private MessageIdManager messageIdManager;
    private MessageHeaderViewFactory testee;
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
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("fullMessage.eml")),
            session);

        testee = new MessageHeaderViewFactory(resources.getBlobManager());
    }

    @Test
    void fromMessageResultsShouldReturnCorrectView() throws Exception {
        List<MessageResult> messages = messageIdManager
            .getMessages(ImmutableList.of(message1.getMessageId()), FetchGroupImpl.MINIMAL, session);

        Emailer bobEmail = Emailer.builder().name(BOB.getLocalPart()).email(BOB.asString()).build();
        Emailer aliceEmail = Emailer.builder().name("alice").email("alice@local").build();
        Emailer jackEmail = Emailer.builder().name("jack").email("jack@local").build();
        Emailer jacobEmail = Emailer.builder().name("jacob").email("jacob@local").build();

        ImmutableMap<String, String> headersMap = ImmutableMap.<String, String>builder()
            .put("Content-Type", "multipart/mixed; boundary=\"------------7AF1D14DE1DFA16229726B54\"")
            .put("Date", "Tue, 7 Jun 2016 16:23:37 +0200")
            .put("From", "alice <alice@local>")
            .put("To", "bob <bob@local>")
            .put("Subject", "Full message")
            .put("Mime-Version", "1.0")
            .put("Message-ID", "<1cc7f114-dbc4-42c2-99bd-f1100db6d0c1@open-paas.org>")
            .put("Cc", "jack <jack@local>, jacob <jacob@local>")
            .put("Bcc", "alice <alice@local>")
            .put("Reply-to", "alice <alice@local>")
            .put("In-reply-to", "bob@local")
            .build();

        MessageHeaderView actual = testee.fromMessageResults(messages);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(actual.getId()).isEqualTo(message1.getMessageId());
            softly.assertThat(actual.getMailboxIds()).containsExactly(bobInbox.getId());
            softly.assertThat(actual.getThreadId()).isEqualTo(message1.getMessageId().serialize());
            softly.assertThat(actual.getSize()).isEqualTo(Number.fromLong(2255));
            softly.assertThat(actual.getKeywords()).isEqualTo(Keywords.strictFactory().from(Keyword.SEEN).asMap());
            softly.assertThat(actual.getBlobId()).isEqualTo(BlobId.of(message1.getMessageId().serialize()));
            softly.assertThat(actual.getInReplyToMessageId()).isEqualTo(Optional.of(BOB.asString()));
            softly.assertThat(actual.getHeaders()).isEqualTo(headersMap);
            softly.assertThat(actual.getFrom()).isEqualTo(Optional.of(aliceEmail));
            softly.assertThat(actual.getTo()).isEqualTo(ImmutableList.of(bobEmail));
            softly.assertThat(actual.getCc()).isEqualTo(ImmutableList.of(jackEmail, jacobEmail));
            softly.assertThat(actual.getBcc()).isEqualTo(ImmutableList.of(aliceEmail));
            softly.assertThat(actual.getReplyTo()).isEqualTo(ImmutableList.of(aliceEmail));
            softly.assertThat(actual.getSubject()).isEqualTo("Full message");
            softly.assertThat(actual.getDate()).isEqualTo("2016-06-07T14:23:37Z");
        });
    }

    @Test
    void fromMessageResultsShouldCombineKeywords() throws Exception {
        messageIdManager.setInMailboxes(message1.getMessageId(), ImmutableList.of(bobInbox.getId(), bobMailbox.getId()), session);
        bobMailbox.setFlags(new Flags(Flags.Flag.FLAGGED), MessageManager.FlagsUpdateMode.REPLACE, MessageRange.all(), session);

        List<MessageResult> messages = messageIdManager
            .getMessages(ImmutableList.of(message1.getMessageId()), FetchGroupImpl.MINIMAL, session);

        MessageHeaderView actual = testee.fromMessageResults(messages);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(actual.getId()).isEqualTo(message1.getMessageId());
            softly.assertThat(actual.getKeywords()).isEqualTo(Keywords.strictFactory().from(Keyword.SEEN, Keyword.FLAGGED).asMap());
        });
    }
}
