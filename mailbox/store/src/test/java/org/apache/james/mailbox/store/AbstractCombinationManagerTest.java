/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.store;

import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.RESET;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableSet;
import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;

import org.apache.james.mailbox.ApplicableFlagBuilder;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.fixture.MailboxFixture;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mime4j.dom.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public abstract class AbstractCombinationManagerTest {

    private static final int DEFAULT_MAXIMUM_LIMIT = 256;

    private static final String USER_FLAGS_VALUE = "User Flags";
    private static final String ANOTHER_USER_FLAGS_VALUE = "Another User Flags";

    private MailboxManager mailboxManager;
    private MessageIdManager messageIdManager;
    private MessageManager messageManager1;
    private MessageManager messageManager2;

    private MailboxSession session;
    private Mailbox mailbox1;
    private Mailbox mailbox2;

    private CombinationManagerTestSystem testingData;
    private Message mailContent;

    protected abstract CombinationManagerTestSystem createTestingData() throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        session = MailboxSessionUtil.create(MailboxFixture.ALICE);
        testingData = createTestingData();

        mailbox1 = testingData.createMailbox(MailboxFixture.INBOX_ALICE, session);
        mailbox2 = testingData.createMailbox(MailboxFixture.OUTBOX_ALICE, session);

        mailboxManager = testingData.getMailboxManager();
        messageIdManager = testingData.getMessageIdManager();
        messageManager1 = testingData.createMessageManager(mailbox1, session);
        messageManager2 = testingData.createMessageManager(mailbox2, session);

        mailContent = Message.Builder.of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build();
    }


    @Test
    void getMessageCountFromMessageManagerShouldReturnDataSetInMailboxesFromMessageIdManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMessageCount(session)).isEqualTo(1);
    }

    @Test
    void searchFromMessageManagerShouldReturnMessagesUsingSetInMailboxesFromMessageIdManager() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.all());

        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(Flux.from(messageManager2.search(query, session)).toStream()).hasSize(1);
    }

    @Test
    void searchFromMessageManagerShouldReturnMessagesUsingSetInMailboxesFromMessageIdManagerWhenSearchByMailboxQueryWithMailboxPath() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.all());

        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId,
            ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        MessageUid uidInMailbox2 = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session)
            .get(0)
            .getUid();

        assertThat(Flux.from(messageManager2.search(query, session)).toStream()).hasSize(1)
            .containsExactly(uidInMailbox2);
    }

    @Test
    void searchFromMessageManagerShouldReturnMessagesUsingSetInMailboxesFromMessageIdManagerWhenSearchByMailboxQueryWithUsername() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.all());

        ComposedMessageId composedMessageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session).getId();

        messageIdManager.setInMailboxes(composedMessageId.getMessageId(),
            ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(Flux.from(messageManager1.search(query, session)).toStream()).hasSize(1)
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void searchFromMailboxManagerShouldReturnMessagesUsingSetInMailboxesFromMessageIdManagerWhenSearchByMultiMailboxes() throws Exception {
        SearchQuery query = SearchQuery.of(SearchQuery.all());

        MultimailboxesSearchQuery.Builder builder = MultimailboxesSearchQuery.from(query);
        builder.inMailboxes(mailbox1.getMailboxId(), mailbox2.getMailboxId());
        MultimailboxesSearchQuery multiMailboxesQuery = builder.build();

        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(Flux.from(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
            .collectList().block()).containsOnly(messageId);
    }

    @Test
    void setFlagsToDeleteThenExpungeFromMessageManagerThenGetMessageFromMessageIdManagerShouldNotReturnAnything() throws Exception {
        Flags deleted = new Flags(Flag.DELETED);
        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageManager1.setFlags(deleted, FlagsUpdateMode.ADD, MessageRange.all(), session);
        messageManager1.expunge(MessageRange.all(), session);

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session)).isEmpty();
    }

    @Test
    void expungeFromMessageManagerShouldWorkWhenSetFlagsToDeletedWithMessageIdManager() throws Exception {
        Flags deleted = new Flags(Flag.DELETED);
        ComposedMessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session).getId();

        messageIdManager.setFlags(deleted, FlagsUpdateMode.ADD, messageId.getMessageId(), ImmutableList.of(mailbox1.getMailboxId()), session);

        assertThat(messageManager1.expunge(MessageRange.all(), session))
            .toIterable()
            .containsOnly(messageId.getUid());
    }

    @Test
    void expungeFromMessageManagerShouldWorkWhenSetInMailboxesAMessageWithDeletedFlag() throws Exception { //I can mark as DELETED + expunge an mail with setInMbxs
        Flags deleted = new Flags(Flag.DELETED);
        ComposedMessageId messageId = messageManager1.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(deleted)
                .build(mailContent), session).getId();

        messageIdManager.setInMailboxes(messageId.getMessageId(), ImmutableList.of(mailbox1.getMailboxId()), session);

        assertThat(messageManager1.expunge(MessageRange.all(), session))
            .toIterable()
            .containsOnly(messageId.getUid());
    }

    @Test
    void getMessageFromMessageIdManagerShouldReturnMessageWhenAppendMessageFromMessageManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session)).hasSize(1);
    }

    @Test
    void getMessageFromMessageIdManagerShouldReturnMessageWhenCopyMessageWithMailboxIdFromMailboxManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        mailboxManager.copyMessages(MessageRange.all(), mailbox1.getMailboxId(), mailbox2.getMailboxId(), session);

        List<MessageResult> listMessages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session);

        assertThat(listMessages).hasSize(2)
            .extractingResultOf("getMailboxId")
            .containsOnly(mailbox1.getMailboxId(), mailbox2.getMailboxId());
    }

    @Test
    void getMessageFromMessageIdManagerShouldReturnMessageWhenCopyMessageWithMailboxPathFromMailboxManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        mailboxManager.copyMessages(MessageRange.all(), MailboxFixture.INBOX_ALICE, MailboxFixture.OUTBOX_ALICE, session);

        List<MessageResult> listMessages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session);

        assertThat(listMessages).hasSize(2)
            .extractingResultOf("getMailboxId")
            .containsOnly(mailbox1.getMailboxId(), mailbox2.getMailboxId());
    }

    @Test
    void getMessageFromMessageIdManagerShouldReturnMessageWhenMoveMessageWithMailboxIdFromMailboxManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        mailboxManager.moveMessages(MessageRange.all(), MailboxFixture.INBOX_ALICE, MailboxFixture.OUTBOX_ALICE, session);

        List<MessageResult> listMessages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session);

        assertThat(listMessages).hasSize(1)
            .extractingResultOf("getMailboxId")
            .containsOnly(mailbox2.getMailboxId());
    }

    @Test
    void getMessagesFromMessageManagerShouldReturnMessagesCreatedBySetInMailboxesFromMessageIdManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMessages(MessageRange.all(), FetchGroup.MINIMAL, session))
            .toIterable()
            .hasSize(1);
    }

    @Test
    void getMetadataFromMessageManagerShouldReturnRecentMessageWhenSetInMailboxesFromMessageIdManager() throws Exception {
        Flags recent = new Flags(Flag.RECENT);
        ComposedMessageId messageId = messageManager1.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(recent)
                .build(mailContent), session).getId();

        long mailbox2NextUid = messageManager2.getMetaData(RESET, session, MessageManager.MailboxMetaData.FetchGroup.UNSEEN_COUNT).getUidNext().asLong();
        messageIdManager.setInMailboxes(messageId.getMessageId(), ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        List<MessageUid> messageUids = messageManager2.getMetaData(RESET, session, MessageManager.MailboxMetaData.FetchGroup.UNSEEN_COUNT).getRecent();

        assertThat(messageUids).hasSize(1);
        assertThat(messageUids.get(0).asLong()).isGreaterThanOrEqualTo(mailbox2NextUid);
    }

    @Test
    void getMetadataFromMessageManagerShouldReturnNumberOfRecentMessageWhenSetInMailboxesFromMessageIdManager() throws Exception {
        Flags recent = new Flags(Flag.RECENT);
        MessageId messageId = messageManager1.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(recent)
                .build(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMetaData(RESET, session, MessageManager.MailboxMetaData.FetchGroup.FIRST_UNSEEN).countRecent()).isEqualTo(1);
    }

    @Test
    void getMetadataFromMessageManagerShouldReturnUidNextWhenSetInMailboxesFromMessageIdManager() throws Exception {
        Flags recent = new Flags(Flag.RECENT);
        ComposedMessageId messageId = messageManager1.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(recent)
                .build(mailContent), session).getId();

        messageIdManager.setInMailboxes(messageId.getMessageId(), ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        List<MessageResult> listMessages = messageIdManager.getMessage(messageId.getMessageId(), FetchGroup.MINIMAL, session);

        long uid2 = listMessages.stream()
            .filter(messageInMailbox2())
            .findFirst()
            .get()
            .getUid()
            .asLong();

        assertThat(messageManager2.getMetaData(RESET, session, MessageManager.MailboxMetaData.FetchGroup.FIRST_UNSEEN).getUidNext().asLong())
            .isGreaterThan(uid2);
    }

    @Test
    void getMetadataFromMessageManagerShouldReturnHighestModSeqWhenSetInMailboxesFromMessageIdManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMetaData(RESET, session, MessageManager.MailboxMetaData.FetchGroup.FIRST_UNSEEN).getHighestModSeq().asLong()).isNotNegative();
    }

    @Test
    void getMetadataFromMessageManagerShouldReturnMessageCountWhenSetInMailboxesFromMessageIdManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMetaData(RESET, session, MessageManager.MailboxMetaData.FetchGroup.FIRST_UNSEEN).getMessageCount()).isEqualTo(1);
    }

    @Test
    void getMetadataFromMessageManagerShouldReturnNumberOfUnseenMessageWhenSetInMailboxesFromMessageIdManager() throws Exception {
        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMetaData(RESET, session, MessageManager.MailboxMetaData.FetchGroup.UNSEEN_COUNT).getUnseenCount()).isEqualTo(1);
    }

    @Test
    void getMetadataFromMessageManagerShouldReturnFirstUnseenMessageWhenSetInMailboxesFromMessageIdManager() throws Exception {
        ComposedMessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session).getId();

        messageIdManager.setInMailboxes(messageId.getMessageId(), ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager2.getMetaData(RESET, session, MessageManager.MailboxMetaData.FetchGroup.FIRST_UNSEEN).getFirstUnseen()).isEqualTo(messageId.getUid());
    }

    @Test
    void getMetadataFromMessageManagerShouldReturnNumberOfUnseenMessageWhenSetFlagsFromMessageIdManager() throws Exception {
        Flags newFlag = new Flags(Flag.RECENT);
        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setFlags(newFlag, FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager1.getMetaData(RESET, session, MessageManager.MailboxMetaData.FetchGroup.UNSEEN_COUNT).getUnseenCount()).isEqualTo(1);
    }

    @Test
    void getMetadataFromMessageManagerShouldReturnFirstUnseenMessageWhenSetFlagsFromMessageIdManager() throws Exception {
        Flags newFlag = new Flags(Flag.USER);
        ComposedMessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session).getId();

        messageIdManager.setFlags(newFlag, FlagsUpdateMode.ADD, messageId.getMessageId(), ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        assertThat(messageManager1.getMetaData(RESET, session, MessageManager.MailboxMetaData.FetchGroup.FIRST_UNSEEN).getFirstUnseen()).isEqualTo(messageId.getUid());
    }

    @Test
    void setInMailboxesFromMessageIdManagerShouldMoveMessage() throws Exception {
        MessageId messageId = messageManager1.appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        assertThat(messageManager1.getMessages(MessageRange.all(), FetchGroup.MINIMAL, session))
            .toIterable()
            .isEmpty();
        assertThat(messageManager2.getMessages(MessageRange.all(), FetchGroup.MINIMAL, session))
            .toIterable()
            .hasSize(1)
            .extracting(MessageResult::getMessageId)
            .containsOnly(messageId);
    }

    @Test
    void appendMessageFromMessageManagerAndSetInMailboxFromMessageIdManagerShouldUpdateApplicableFlag() throws Exception {
        Flags messageFlag = new FlagsBuilder()
            .add(Flag.ANSWERED)
            .add(USER_FLAGS_VALUE)
            .build();

        MessageId messageId = messageManager1.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(messageFlag)
                .build(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        Flags expected = ApplicableFlagBuilder
            .from(messageFlag)
            .build();

        assertThat(messageManager1.getApplicableFlags(session))
            .isEqualTo(expected);

        assertThat(messageManager2.getApplicableFlags(session))
            .isEqualTo(expected);
    }

    @Test
    void appendMessageFromMessageManagerAndSetFlagsFromMessageIdManagerShouldUnionApplicableFlag() throws Exception {
        Flags messageFlag = new FlagsBuilder()
            .add(Flag.ANSWERED)
            .add(USER_FLAGS_VALUE)
            .build();

        Flags deleted = new FlagsBuilder()
            .add(Flag.DELETED)
            .add(USER_FLAGS_VALUE, ANOTHER_USER_FLAGS_VALUE)
            .build();

        MessageId messageId = messageManager1.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(messageFlag)
                .build(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setFlags(deleted, FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        assertThat(messageManager1.getApplicableFlags(session))
            .isEqualTo(ApplicableFlagBuilder
                .from(USER_FLAGS_VALUE, ANOTHER_USER_FLAGS_VALUE)
                .build());
    }

    @Test
    void setFlagsFromMessageManagerAndSetFlagsFromMessageIdManagerShouldUpdateSameApplicableFlag() throws Exception {
        Flags customFlag1 = new Flags(Flag.ANSWERED);
        Flags customFlag2 = new Flags("customFlag2");
        MessageId messageId = messageManager1.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(customFlag1)
                .build(mailContent), session)
            .getId().getMessageId();


        messageIdManager.setFlags(customFlag2, FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId()), session);
        messageManager1.setFlags(customFlag2, FlagsUpdateMode.ADD, MessageRange.all(), session);

        Flags applicableFlags = messageManager1.getApplicableFlags(session);

        assertThat(applicableFlags)
            .isEqualTo(ApplicableFlagBuilder
                .from(customFlag1, customFlag2)
                .build());
    }

    @Test
    void setInMailboxFromMessageIdManagerAndSetFlagsFromMessageManagerShouldUnionApplicableFlag() throws Exception {
        Flags custom1 = new Flags("custom1");
        Flags custom2 = new Flags("custom2");
        MessageId messageId = messageManager1.appendMessage(
            MessageManager.AppendCommand.builder()
                .withFlags(custom1)
                .build(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);
        messageManager2.setFlags(custom2, FlagsUpdateMode.ADD, MessageRange.all(), session);

        Flags expected = ApplicableFlagBuilder
            .from(custom1, custom2)
            .build();

        assertThat(messageManager2.getApplicableFlags(session))
            .isEqualTo(expected);
    }

    @Test
    void getUidsShouldInteractWellWithSetInMailboxes() throws Exception {
        MessageId messageId = messageManager1
            .appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        MessageUid uid2 = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, session)
            .stream()
            .filter(messageInMailbox2())
            .findFirst()
            .get()
            .getUid();

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.all());
        assertThat(Flux.from(messageManager2.search(searchQuery, session)).toStream())
            .hasSize(1)
            .containsOnly(uid2);
    }

    @Test
    void getUidsShouldInteractWellWithDelete() throws Exception {
        MessageId messageId = messageManager1
            .appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.all());
        assertThat(Flux.from(messageManager1.search(searchQuery, session)).toStream()).isEmpty();
    }

    @Test
    void getUidsShouldInteractWellWithDeletes() throws Exception {
        MessageId messageId1 = messageManager1
            .appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();
        MessageId messageId2 = messageManager1
            .appendMessage(MessageManager.AppendCommand.from(mailContent), session)
            .getId().getMessageId();

        Mono.from(messageIdManager.delete(ImmutableSet.of(messageId1, messageId2), session))
            .subscribeOn(Schedulers.newSingle("test"))
            .block();

        SearchQuery searchQuery = SearchQuery.of(SearchQuery.all());
        assertThat(Flux.from(messageManager1.search(searchQuery, session)).toStream()).isEmpty();
    }

    private Predicate<MessageResult> messageInMailbox2() {
        return messageResult -> messageResult.getMailboxId().equals(mailbox2.getMailboxId());
    }

}
