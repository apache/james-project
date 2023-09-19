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

package org.apache.james.examples.custom.listeners;

import static org.apache.james.examples.custom.listeners.SetCustomFlagOnBigMessages.BIG_MESSAGE;
import static org.apache.james.examples.custom.listeners.SetCustomFlagOnBigMessages.ONE_MB;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_APPENDED;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_DELIVERY;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mime4j.dom.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;
import com.google.common.collect.Streams;

class SetCustomFlagOnBigMessagesTest {

    private static final Username USER = Username.of("user");
    private static final Event.EventId RANDOM_EVENT_ID = Event.EventId.random();
    private static final MailboxPath INBOX_PATH = MailboxPath.inbox(USER);

    private SetCustomFlagOnBigMessages testee;
    private MessageManager inboxMessageManager;
    private MailboxId inboxId;
    private MailboxSession mailboxSession;
    private InMemoryMailboxManager mailboxManager;

    @BeforeEach
    void beforeEach() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        mailboxSession = MailboxSessionUtil.create(USER);
        inboxId = mailboxManager.createMailbox(INBOX_PATH, mailboxSession).get();
        inboxMessageManager = mailboxManager.getMailbox(inboxId, mailboxSession);

        testee = new SetCustomFlagOnBigMessages(mailboxManager);

        resources.getEventBus().register(testee);
    }

    @Test
    void shouldNotAddFlagWhenSmallMessages() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(smallMessage()),
            mailboxSession).getId();

        assertThat(getMessageFlags(composedId.getUid()))
            .allSatisfy(flags -> assertThat(flags.contains(BIG_MESSAGE)).isFalse());
    }

    @Test
    void shouldNotRemoveOtherFlagsWhenSmallMessages() throws Exception {
        Flags appendMessageFlag = new Flags();
        appendMessageFlag.add(Flags.Flag.SEEN);
        appendMessageFlag.add(Flags.Flag.DRAFT);

        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(appendMessageFlag)
                .build(smallMessage()),
            mailboxSession).getId();

        assertThat(getMessageFlags(composedId.getUid()))
            .allSatisfy(flags -> {
                assertThat(flags.contains(Flags.Flag.SEEN)).isTrue();
                assertThat(flags.contains(Flags.Flag.DRAFT)).isTrue();
            });
    }

    @Test
    void shouldAddFlagWhenBigMessages() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(bigMessage()),
            mailboxSession).getId();

        assertThat(getMessageFlags(composedId.getUid()))
            .allSatisfy(flags -> assertThat(flags.contains(BIG_MESSAGE)).isTrue());
    }

    @Test
    void shouldAddFlagWhenMessageSizeIsEqualToBigMessageSize() throws Exception {
        ComposedMessageId composedIdOfSmallMessage = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(smallMessage()),
            mailboxSession).getId();

        MessageResult addedMessage = inboxMessageManager
            .getMessages(MessageRange.one(composedIdOfSmallMessage.getUid()), FetchGroup.MINIMAL, mailboxSession)
            .next();
        MessageMetaData oneMBMetaData = new MessageMetaData(addedMessage.getUid(), addedMessage.getModSeq(),
            addedMessage.getFlags(), ONE_MB, addedMessage.getInternalDate(), Optional.empty(), addedMessage.getMessageId(), addedMessage.getThreadId());

        Event eventWithAFakeMessageSize = EventFactory.added()
            .eventId(RANDOM_EVENT_ID)
            .mailboxSession(mailboxSession)
            .mailboxId(inboxId)
            .mailboxPath(INBOX_PATH)
            .addMetaData(oneMBMetaData)
            .isDelivery(!IS_DELIVERY)
            .isAppended(!IS_APPENDED)
            .build();

        testee.event(eventWithAFakeMessageSize);

        assertThat(getMessageFlags(composedIdOfSmallMessage.getUid()))
            .allSatisfy(flags -> assertThat(flags.contains(BIG_MESSAGE)).isTrue());
    }

    @Test
    void shouldNotRemoveOtherFlagsWhenBigMessages() throws Exception {
        Flags appendMessageFlag = new Flags();
        appendMessageFlag.add(Flags.Flag.SEEN);
        appendMessageFlag.add(Flags.Flag.DRAFT);

        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(appendMessageFlag)
                .build(bigMessage()),
            mailboxSession).getId();

        assertThat(getMessageFlags(composedId.getUid()))
            .allSatisfy(flags -> {
                assertThat(flags.contains(Flags.Flag.SEEN)).isTrue();
                assertThat(flags.contains(Flags.Flag.DRAFT)).isTrue();
                assertThat(flags.contains(BIG_MESSAGE)).isTrue();
            });
    }

    @Test
    void shouldKeepBigMessageFlagWhenAlreadySet() throws Exception {
        Flags appendMessageFlag = new Flags();
        appendMessageFlag.add(BIG_MESSAGE);

        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(appendMessageFlag)
                .build(bigMessage()),
            mailboxSession).getId();

        assertThat(getMessageFlags(composedId.getUid()))
            .allSatisfy(flags -> assertThat(flags.contains(BIG_MESSAGE)).isTrue());
    }

    private Stream<Flags> getMessageFlags(MessageUid messageUid) throws Exception {
        return Streams.stream(inboxMessageManager
            .getMessages(MessageRange.one(messageUid), FetchGroup.MINIMAL, mailboxSession))
            .map(MessageResult::getFlags);
    }

    private Message bigMessage() throws Exception {
        return Message.Builder.of()
            .setSubject("big message")
            .setBody(Strings.repeat("big message has size greater than one MB", 1024 * 1024), StandardCharsets.UTF_8)
            .build();
    }

    private Message smallMessage() throws Exception {
        return Message.Builder.of()
            .setSubject("small message")
            .setBody("small message has size less than one MB", StandardCharsets.UTF_8)
            .build();
    }
}
