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

package org.apache.james.vault;

import static org.apache.james.vault.DeletedMessageFixture.DELETION_DATE;
import static org.apache.james.vault.DeletedMessageFixture.DELIVERY_DATE;
import static org.apache.james.vault.DeletedMessageFixture.INTERNAL_DATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.CombinationManagerTestSystem;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.vault.memory.MemoryDeletedMessagesVault;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

class DeletedMessageVaultHookTest {

    private static final String ALICE_ADDRESS = "alice@james.com";
    private static final String BOB_ADDRESS = "bob@james.com";
    private static final String TEST_ADDRESS = "test@james.com";
    private static final User ALICE = User.fromUsername(ALICE_ADDRESS);
    private static final User BOB = User.fromUsername(BOB_ADDRESS);

    private static final MailboxPath MAILBOX_ALICE_ONE = MailboxPath.forUser(ALICE_ADDRESS, "ALICE_ONE");
    private static final MailboxPath MAILBOX_BOB_ONE = MailboxPath.forUser(BOB_ADDRESS, "BOB_ONE");

    private MailboxManager mailboxManager;
    private MessageIdManager messageIdManager;
    private Message mailContent;
    private MemoryDeletedMessagesVault messageVault;
    private Clock clock;
    private SessionProvider sessionProvider;
    private MailboxSession aliceSession;
    private MailboxSession bobSession;
    private InMemoryMailboxSessionMapperFactory mapperFactory;
    private CombinationManagerTestSystem testingData;
    private SearchQuery searchQuery;

    private DeletedMessage buildDeletedMessage(List<MailboxId> mailboxIds, MessageId messageId, User user) throws Exception {
        return DeletedMessage.builder()
            .messageId(messageId)
            .originMailboxes(mailboxIds)
            .user(user)
            .deliveryDate(DELIVERY_DATE)
            .deletionDate(DELETION_DATE)
            .sender(MaybeSender.getMailSender(ALICE_ADDRESS))
            .recipients(new MailAddress(TEST_ADDRESS))
            .hasAttachment(false)
            .subject("test")
            .build();
    }

    private ComposedMessageId appendMessage(MessageManager messageManager) throws Exception {
        return messageManager.appendMessage(MessageManager.AppendCommand.builder()
                .withInternalDate(INTERNAL_DATE)
                .build(mailContent), aliceSession);
    }

    @BeforeEach
    void setUp() throws Exception {
        clock = Clock.fixed(DELETION_DATE.toInstant(), ZoneOffset.UTC);
        messageVault = new MemoryDeletedMessagesVault();

        Authenticator noAuthenticator = null;
        Authorizator noAuthorizator = null;
        sessionProvider = new SessionProvider(noAuthenticator, noAuthorizator);
        aliceSession = sessionProvider.createSystemSession(ALICE_ADDRESS);
        bobSession = sessionProvider.createSystemSession(BOB_ADDRESS);

        mapperFactory = new InMemoryMailboxSessionMapperFactory();
        DeletedMessageConverter deletedMessageConverter = new DeletedMessageConverter();
        DeletedMessageVaultHook messageVaultHook = new DeletedMessageVaultHook(sessionProvider, messageVault, deletedMessageConverter, mapperFactory, clock);

        testingData = MessageIdManagerWithPreDeletionHooksTestSystemProvider
            .createTestingData(sessionProvider, mapperFactory, ImmutableSet.of(messageVaultHook));

        mailboxManager = testingData.getMailboxManager();
        messageIdManager = testingData.getMessageIdManager();

        mailContent = Message.Builder.of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .setSender(ALICE_ADDRESS)
            .setTo(TEST_ADDRESS)
            .setDate(INTERNAL_DATE)
            .build();

        searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.internalDateOn(INTERNAL_DATE, SearchQuery.DateResolution.Second));
    }

    @Test
    void notifyDeleteShouldAppendMessageVault() throws Exception {
        Mailbox aliceMailbox = testingData.createMailbox(MAILBOX_ALICE_ONE, aliceSession);
        MessageManager messageManager = testingData.createMessageManager(aliceMailbox, aliceSession);
        MessageId messageId = appendMessage(messageManager).getMessageId();

        messageIdManager.delete(ImmutableList.of(messageId), aliceSession);

        DeletedMessage deletedMessage = buildDeletedMessage(ImmutableList.of(aliceMailbox.getMailboxId()), messageId, ALICE);
        assertThat(messageVault.search(ALICE, Query.ALL).blockFirst())
            .isEqualTo(deletedMessage);
    }

    @Test
    void notifyDeleteShouldAppendMessageToVaultOfMailboxOwnerWhenOtherUserDeleteMessageInSharingMailbox() throws Exception {
        Mailbox aliceMailbox = testingData.createMailbox(MAILBOX_ALICE_ONE, aliceSession);
        MessageManager aliceMessageManager = testingData.createMessageManager(aliceMailbox, aliceSession);
        MessageManager bobMessageManager = testingData.createMessageManager(aliceMailbox, bobSession);
        ComposedMessageId composedMessageId = appendMessage(aliceMessageManager);
        MessageId messageId = composedMessageId.getMessageId();

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB_ADDRESS)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        DeletedMessage deletedMessage = buildDeletedMessage(ImmutableList.of(aliceMailbox.getMailboxId()), messageId, ALICE);
        bobMessageManager.delete(ImmutableList.copyOf(bobMessageManager.search(searchQuery, bobSession)), bobSession);

        assertThat(messageVault.search(ALICE, Query.ALL).blockFirst())
            .isEqualTo(deletedMessage);
    }

    @Test
    void notifyDeleteShouldNotAppendMessageToVaultOfOtherUserOfMailboxWhenOtherUserDeleteMessageInSharingMailbox() throws Exception {
        Mailbox aliceMailbox = testingData.createMailbox(MAILBOX_ALICE_ONE, aliceSession);
        MessageManager aliceMessageManager = testingData.createMessageManager(aliceMailbox, aliceSession);
        MessageManager bobMessageManager = testingData.createMessageManager(aliceMailbox, bobSession);
        appendMessage(aliceMessageManager);

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB_ADDRESS)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        bobMessageManager.delete(ImmutableList.copyOf(bobMessageManager.search(searchQuery, bobSession)), bobSession);

        assertThat(messageVault.search(BOB, Query.ALL).collectList().block())
            .isEmpty();
    }

    @Test
    void notifyDeleteShouldAppendMessageToVaultOfOtherUserOfMailboxWhenOtherUserDeleteMessageAfterMoveToAnotherMailbox() throws Exception {
        Mailbox aliceMailbox = testingData.createMailbox(MAILBOX_ALICE_ONE, aliceSession);
        Mailbox bobMailbox = testingData.createMailbox(MAILBOX_BOB_ONE, bobSession);
        MessageManager aliceMessageManager = testingData.createMessageManager(aliceMailbox, aliceSession);
        MessageManager bobMessageManager = testingData.createMessageManager(bobMailbox, bobSession);
        ComposedMessageId composedMessageId = appendMessage(aliceMessageManager);
        MessageId messageId = composedMessageId.getMessageId();

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB_ADDRESS)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(bobMailbox.getMailboxId()), bobSession);

        DeletedMessage deletedMessage = buildDeletedMessage(ImmutableList.of(bobMailbox.getMailboxId()), messageId, BOB);
        bobMessageManager.delete(ImmutableList.copyOf(bobMessageManager.search(searchQuery, bobSession)), bobSession);

        assertThat(messageVault.search(BOB, Query.ALL).blockFirst())
            .isEqualTo(deletedMessage);
    }

    @Test
    void notifyDeleteShouldNotAppendMessageToVaultOfMailboxOwnerWhenOtherUserDeleteMessageAfterMoveToAnotherMailbox() throws Exception {
        Mailbox aliceMailbox = testingData.createMailbox(MAILBOX_ALICE_ONE, aliceSession);
        Mailbox bobMailbox = testingData.createMailbox(MAILBOX_BOB_ONE, bobSession);
        MessageManager aliceMessageManager = testingData.createMessageManager(aliceMailbox, aliceSession);
        MessageManager bobMessageManager = testingData.createMessageManager(bobMailbox, bobSession);
        ComposedMessageId composedMessageId = appendMessage(aliceMessageManager);
        MessageId messageId = composedMessageId.getMessageId();

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB_ADDRESS)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(bobMailbox.getMailboxId()), bobSession);

        bobMessageManager.delete(ImmutableList.copyOf(bobMessageManager.search(searchQuery, bobSession)), bobSession);

        assertThat(messageVault.search(ALICE, Query.ALL).collectList().block())
            .isEmpty();
    }

    @Test
    void notifyDeleteShouldAppendMessageToVaultOfOtherUserOfMailboxWhenOtherUserDeleteMessageAfterCopyToAnotherMailbox() throws Exception {
        Mailbox aliceMailbox = testingData.createMailbox(MAILBOX_ALICE_ONE, aliceSession);
        Mailbox bobMailbox = testingData.createMailbox(MAILBOX_BOB_ONE, bobSession);
        MessageManager aliceMessageManager = testingData.createMessageManager(aliceMailbox, aliceSession);
        MessageManager bobMessageManager = testingData.createMessageManager(bobMailbox, bobSession);
        ComposedMessageId composedMessageId = appendMessage(aliceMessageManager);
        MessageId messageId = composedMessageId.getMessageId();

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB_ADDRESS)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox.getMailboxId(), bobMailbox.getMailboxId()), bobSession);

        DeletedMessage deletedMessage = buildDeletedMessage(ImmutableList.of(bobMailbox.getMailboxId()), messageId, BOB);
        bobMessageManager.delete(ImmutableList.copyOf(bobMessageManager.search(searchQuery, bobSession)), bobSession);

        assertThat(messageVault.search(BOB, Query.ALL).blockFirst())
            .isEqualTo(deletedMessage);
    }

    @Test
    void notifyDeleteShouldNotAppendMessageToVaultOfMailboxOwnerWhenOtherUserDeleteMessageAfterCopyToAnotherMailbox() throws Exception {
        Mailbox aliceMailbox = testingData.createMailbox(MAILBOX_ALICE_ONE, aliceSession);
        Mailbox bobMailbox = testingData.createMailbox(MAILBOX_BOB_ONE, bobSession);
        MessageManager aliceMessageManager = testingData.createMessageManager(aliceMailbox, aliceSession);
        MessageManager bobMessageManager = testingData.createMessageManager(bobMailbox, bobSession);
        ComposedMessageId composedMessageId = appendMessage(aliceMessageManager);
        MessageId messageId = composedMessageId.getMessageId();

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB_ADDRESS)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox.getMailboxId(), bobMailbox.getMailboxId()), bobSession);

        bobMessageManager.delete(ImmutableList.copyOf(bobMessageManager.search(searchQuery, bobSession)), bobSession);

        assertThat(messageVault.search(ALICE, Query.ALL).collectList().block())
            .isEmpty();
    }

}
