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
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.vault.memory.MemoryDeletedMessagesVault;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

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
    private MailboxSession aliceSession;
    private MailboxSession bobSession;
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

        DeletedMessageConverter deletedMessageConverter = new DeletedMessageConverter();

        InMemoryIntegrationResources.Resources resources = new InMemoryIntegrationResources.Factory()
            .withPreDeletionHook((sessionProvider, mapperFactory) -> new DeletedMessageVaultHook(sessionProvider, messageVault, deletedMessageConverter, mapperFactory, clock))
            .create();

        mailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();

        mailContent = Message.Builder.of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .setSender(ALICE_ADDRESS)
            .setTo(TEST_ADDRESS)
            .setDate(INTERNAL_DATE)
            .build();

        searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.internalDateOn(INTERNAL_DATE, SearchQuery.DateResolution.Second));

        aliceSession = mailboxManager.createSystemSession(ALICE_ADDRESS);
        bobSession = mailboxManager.createSystemSession(BOB_ADDRESS);
    }

    @Test
    void notifyDeleteShouldAppendMessageVault() throws Exception {
        MailboxId aliceMailbox = mailboxManager.createMailbox(MAILBOX_ALICE_ONE, aliceSession).get();
        MessageManager messageManager = mailboxManager.getMailbox(aliceMailbox, aliceSession);
        MessageId messageId = appendMessage(messageManager).getMessageId();

        messageIdManager.delete(ImmutableList.of(messageId), aliceSession);

        DeletedMessage deletedMessage = buildDeletedMessage(ImmutableList.of(aliceMailbox), messageId, ALICE);
        assertThat(messageVault.search(ALICE, Query.ALL).blockFirst())
            .isEqualTo(deletedMessage);
    }

    @Test
    void notifyDeleteShouldAppendMessageToVaultOfMailboxOwnerWhenOtherUserDeleteMessageInSharingMailbox() throws Exception {
        MailboxId aliceMailbox = mailboxManager.createMailbox(MAILBOX_ALICE_ONE, aliceSession).get();
        MessageManager aliceMessageManager = mailboxManager.getMailbox(aliceMailbox, aliceSession);

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB_ADDRESS)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        MessageManager bobMessageManager = mailboxManager.getMailbox(aliceMailbox, bobSession);
        ComposedMessageId composedMessageId = appendMessage(aliceMessageManager);
        MessageId messageId = composedMessageId.getMessageId();


        DeletedMessage deletedMessage = buildDeletedMessage(ImmutableList.of(aliceMailbox), messageId, ALICE);
        bobMessageManager.delete(ImmutableList.copyOf(bobMessageManager.search(searchQuery, bobSession)), bobSession);

        assertThat(messageVault.search(ALICE, Query.ALL).blockFirst())
            .isEqualTo(deletedMessage);
    }

    @Test
    void notifyDeleteShouldNotAppendMessageToVaultOfOtherUserOfMailboxWhenOtherUserDeleteMessageInSharingMailbox() throws Exception {
        MailboxId aliceMailbox = mailboxManager.createMailbox(MAILBOX_ALICE_ONE, aliceSession).get();
        MessageManager aliceMessageManager = mailboxManager.getMailbox(aliceMailbox, aliceSession);

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB_ADDRESS)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        MessageManager bobMessageManager = mailboxManager.getMailbox(aliceMailbox, bobSession);
        appendMessage(aliceMessageManager);

        bobMessageManager.delete(ImmutableList.copyOf(bobMessageManager.search(searchQuery, bobSession)), bobSession);

        assertThat(messageVault.search(BOB, Query.ALL).collectList().block())
            .isEmpty();
    }

    @Test
    void notifyDeleteShouldAppendMessageToVaultOfOtherUserOfMailboxWhenOtherUserDeleteMessageAfterMoveToAnotherMailbox() throws Exception {
        MailboxId aliceMailbox = mailboxManager.createMailbox(MAILBOX_ALICE_ONE, aliceSession).get();
        MailboxId bobMailbox = mailboxManager.createMailbox(MAILBOX_BOB_ONE, bobSession).get();
        MessageManager aliceMessageManager = mailboxManager.getMailbox(aliceMailbox, aliceSession);
        MessageManager bobMessageManager = mailboxManager.getMailbox(bobMailbox, bobSession);
        ComposedMessageId composedMessageId = appendMessage(aliceMessageManager);
        MessageId messageId = composedMessageId.getMessageId();

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB_ADDRESS)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(bobMailbox), bobSession);

        DeletedMessage deletedMessage = buildDeletedMessage(ImmutableList.of(bobMailbox), messageId, BOB);
        bobMessageManager.delete(ImmutableList.copyOf(bobMessageManager.search(searchQuery, bobSession)), bobSession);

        assertThat(messageVault.search(BOB, Query.ALL).blockFirst())
            .isEqualTo(deletedMessage);
    }

    @Test
    void notifyDeleteShouldNotAppendMessageToVaultOfMailboxOwnerWhenOtherUserDeleteMessageAfterMoveToAnotherMailbox() throws Exception {
        MailboxId aliceMailbox = mailboxManager.createMailbox(MAILBOX_ALICE_ONE, aliceSession).get();
        MailboxId bobMailbox = mailboxManager.createMailbox(MAILBOX_BOB_ONE, bobSession).get();
        MessageManager aliceMessageManager = mailboxManager.getMailbox(aliceMailbox, aliceSession);
        MessageManager bobMessageManager = mailboxManager.getMailbox(bobMailbox, bobSession);
        ComposedMessageId composedMessageId = appendMessage(aliceMessageManager);
        MessageId messageId = composedMessageId.getMessageId();

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB_ADDRESS)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(bobMailbox), bobSession);

        bobMessageManager.delete(ImmutableList.copyOf(bobMessageManager.search(searchQuery, bobSession)), bobSession);

        assertThat(messageVault.search(ALICE, Query.ALL).collectList().block())
            .isEmpty();
    }

    @Test
    void notifyDeleteShouldAppendMessageToVaultOfOtherUserOfMailboxWhenOtherUserDeleteMessageAfterCopyToAnotherMailbox() throws Exception {
        MailboxId aliceMailbox = mailboxManager.createMailbox(MAILBOX_ALICE_ONE, aliceSession).get();
        MailboxId bobMailbox = mailboxManager.createMailbox(MAILBOX_BOB_ONE, bobSession).get();
        MessageManager aliceMessageManager = mailboxManager.getMailbox(aliceMailbox, aliceSession);
        MessageManager bobMessageManager = mailboxManager.getMailbox(bobMailbox, bobSession);
        ComposedMessageId composedMessageId = appendMessage(aliceMessageManager);
        MessageId messageId = composedMessageId.getMessageId();

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB_ADDRESS)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox, bobMailbox), bobSession);

        DeletedMessage deletedMessage = buildDeletedMessage(ImmutableList.of(bobMailbox), messageId, BOB);
        bobMessageManager.delete(ImmutableList.copyOf(bobMessageManager.search(searchQuery, bobSession)), bobSession);

        assertThat(messageVault.search(BOB, Query.ALL).blockFirst())
            .isEqualTo(deletedMessage);
    }

    @Test
    void notifyDeleteShouldNotAppendMessageToVaultOfMailboxOwnerWhenOtherUserDeleteMessageAfterCopyToAnotherMailbox() throws Exception {
        MailboxId aliceMailbox = mailboxManager.createMailbox(MAILBOX_ALICE_ONE, aliceSession).get();
        MailboxId bobMailbox = mailboxManager.createMailbox(MAILBOX_BOB_ONE, bobSession).get();
        MessageManager aliceMessageManager = mailboxManager.getMailbox(aliceMailbox, aliceSession);
        MessageManager bobMessageManager = mailboxManager.getMailbox(bobMailbox, bobSession);
        ComposedMessageId composedMessageId = appendMessage(aliceMessageManager);
        MessageId messageId = composedMessageId.getMessageId();

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB_ADDRESS)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox, bobMailbox), bobSession);

        bobMessageManager.delete(ImmutableList.copyOf(bobMessageManager.search(searchQuery, bobSession)), bobSession);

        assertThat(messageVault.search(ALICE, Query.ALL).collectList().block())
            .isEmpty();
    }

}
