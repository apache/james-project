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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.apache.james.vault.blob.BlobStoreDeletedMessageVault;
import org.apache.james.vault.blob.BucketNameGenerator;
import org.apache.james.vault.memory.metadata.MemoryDeletedMessageMetadataVault;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class DeletedMessageVaultHookTest {

    private static final String ALICE_ADDRESS = "alice@james.com";
    private static final String BOB_ADDRESS = "bob@james.com";
    private static final String TEST_ADDRESS = "test@james.com";
    private static final Username ALICE = Username.of(ALICE_ADDRESS);
    private static final Username BOB = Username.of(BOB_ADDRESS);
    private static final String MESSAGE_BODY = "testmail";

    private static final MailboxPath MAILBOX_ALICE_ONE = MailboxPath.forUser(ALICE, "ALICE_ONE");
    private static final MailboxPath MAILBOX_BOB_ONE = MailboxPath.forUser(BOB, "BOB_ONE");

    private MailboxManager mailboxManager;
    private MessageIdManager messageIdManager;
    private Message mailContent;
    private DeletedMessageVault messageVault;
    private Clock clock;
    private MailboxSession aliceSession;
    private MailboxSession bobSession;
    private SearchQuery searchQuery;

    private DeletedMessage buildDeletedMessage(List<MailboxId> mailboxIds, MessageId messageId, Username username, long messageSize) throws Exception {
        return DeletedMessage.builder()
            .messageId(messageId)
            .originMailboxes(mailboxIds)
            .user(username)
            .deliveryDate(DELIVERY_DATE)
            .deletionDate(DELETION_DATE)
            .sender(MaybeSender.getMailSender(ALICE_ADDRESS))
            .recipients(new MailAddress(TEST_ADDRESS))
            .hasAttachment(false)
            .size(messageSize)
            .subject("test")
            .build();
    }

    private ComposedMessageId appendMessage(MessageManager messageManager) throws Exception {
        return messageManager.appendMessage(MessageManager.AppendCommand.builder()
                .withInternalDate(INTERNAL_DATE)
                .build(mailContent), aliceSession)
            .getId();
    }

    @BeforeEach
    void setUp() throws Exception {
        clock = Clock.fixed(DELETION_DATE.toInstant(), ZoneOffset.UTC);
        MemoryBlobStoreDAO blobStoreDAO = new MemoryBlobStoreDAO();
        messageVault = new BlobStoreDeletedMessageVault(new RecordingMetricFactory(), new MemoryDeletedMessageMetadataVault(),
            BlobStoreFactory.builder()
                .blobStoreDAO(blobStoreDAO)
                .blobIdFactory(new HashBlobId.Factory())
                .defaultBucketName()
                .passthrough(), blobStoreDAO, new BucketNameGenerator(clock), clock,
            VaultConfiguration.ENABLED_DEFAULT);

        DeletedMessageConverter deletedMessageConverter = new DeletedMessageConverter();

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .preDeletionHookFactory(preInstanciationStage -> new DeletedMessageVaultHook(preInstanciationStage.getSessionProvider(), messageVault, deletedMessageConverter, preInstanciationStage.getMapperFactory(), clock))
            .storeQuotaManager()
            .build();

        mailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();

        mailContent = Message.Builder.of()
            .setSubject("test")
            .setBody(MESSAGE_BODY, StandardCharsets.UTF_8)
            .setSender(ALICE_ADDRESS)
            .setTo(TEST_ADDRESS)
            .setDate(INTERNAL_DATE)
            .build();

        searchQuery = SearchQuery.of(SearchQuery.internalDateOn(INTERNAL_DATE, SearchQuery.DateResolution.Second));

        aliceSession = mailboxManager.createSystemSession(ALICE);
        bobSession = mailboxManager.createSystemSession(BOB);
    }

    @Test
    void notifyDeleteShouldAppendMessageVault() throws Exception {
        MailboxId aliceMailbox = mailboxManager.createMailbox(MAILBOX_ALICE_ONE, aliceSession).get();
        MessageManager messageManager = mailboxManager.getMailbox(aliceMailbox, aliceSession);
        ComposedMessageId composedId = appendMessage(messageManager);
        MessageId messageId = composedId.getMessageId();
        long messageSize = messageSize(messageManager, composedId);

        messageIdManager.delete(messageId, aliceSession);

        DeletedMessage deletedMessage = buildDeletedMessage(ImmutableList.of(aliceMailbox), messageId, ALICE, messageSize);
        assertThat(Flux.from(messageVault.search(ALICE, Query.ALL)).blockFirst())
            .isEqualTo(deletedMessage);
    }

    @Test
    void deletingManyMessagesShouldSucceed() throws Exception {
        MailboxId aliceMailbox = mailboxManager.createMailbox(MAILBOX_ALICE_ONE, aliceSession).get();
        MessageManager messageManager = mailboxManager.getMailbox(aliceMailbox, aliceSession);

        ImmutableSet<MessageId> ids = IntStream.range(0, 1000)
            .mapToObj(Throwing.intFunction(i -> appendMessage(messageManager).getMessageId()))
            .collect(ImmutableSet.toImmutableSet());

        assertThatCode(() -> Mono.from(messageIdManager.delete(ids, aliceSession)).subscribeOn(Schedulers.newSingle("test")).block())
            .doesNotThrowAnyException();
    }

    @Test
    void notifyDeleteShouldAppendMessageToVaultOfMailboxOwnerWhenOtherUserDeleteMessageInSharingMailbox() throws Exception {
        MailboxId aliceMailbox = mailboxManager.createMailbox(MAILBOX_ALICE_ONE, aliceSession).get();
        MessageManager aliceMessageManager = mailboxManager.getMailbox(aliceMailbox, aliceSession);

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        MessageManager bobMessageManager = mailboxManager.getMailbox(aliceMailbox, bobSession);
        ComposedMessageId composedMessageId = appendMessage(aliceMessageManager);
        MessageId messageId = composedMessageId.getMessageId();
        long messageSize = messageSize(bobMessageManager, composedMessageId);

        DeletedMessage deletedMessage = buildDeletedMessage(ImmutableList.of(aliceMailbox), messageId, ALICE, messageSize);
        bobMessageManager.delete(Flux.from(bobMessageManager.search(searchQuery, bobSession)).collect(ImmutableList.toImmutableList()).block(), bobSession);

        assertThat(Flux.from(messageVault.search(ALICE, Query.ALL)).blockFirst())
            .isEqualTo(deletedMessage);
    }

    @Test
    void notifyDeleteShouldNotAppendMessageToVaultOfOtherUserOfMailboxWhenOtherUserDeleteMessageInSharingMailbox() throws Exception {
        MailboxId aliceMailbox = mailboxManager.createMailbox(MAILBOX_ALICE_ONE, aliceSession).get();
        MessageManager aliceMessageManager = mailboxManager.getMailbox(aliceMailbox, aliceSession);

        mailboxManager.setRights(MAILBOX_ALICE_ONE,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        MessageManager bobMessageManager = mailboxManager.getMailbox(aliceMailbox, bobSession);
        appendMessage(aliceMessageManager);

        bobMessageManager.delete(Flux.from(bobMessageManager.search(searchQuery, bobSession)).collect(ImmutableList.toImmutableList()).block(), bobSession);

        assertThat(Flux.from(messageVault.search(BOB, Query.ALL)).collectList().block())
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
                .forUser(BOB)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(bobMailbox), bobSession);

        long messageSize = messageSize(bobMessageManager, composedMessageId);
        DeletedMessage deletedMessage = buildDeletedMessage(ImmutableList.of(bobMailbox), messageId, BOB, messageSize);
        bobMessageManager.delete(Flux.from(bobMessageManager.search(searchQuery, bobSession)).collect(ImmutableList.toImmutableList()).block(), bobSession);

        assertThat(Flux.from(messageVault.search(BOB, Query.ALL)).blockFirst())
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
                .forUser(BOB)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(bobMailbox), bobSession);

        bobMessageManager.delete(Flux.from(bobMessageManager.search(searchQuery, bobSession)).collect(ImmutableList.toImmutableList()).block(), bobSession);

        assertThat(Flux.from(messageVault.search(ALICE, Query.ALL)).collectList().block())
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
                .forUser(BOB)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox, bobMailbox), bobSession);

        long messageSize = messageSize(bobMessageManager, composedMessageId);
        DeletedMessage deletedMessage = buildDeletedMessage(ImmutableList.of(bobMailbox), messageId, BOB, messageSize);
        bobMessageManager.delete(Flux.from(bobMessageManager.search(searchQuery, bobSession)).collect(ImmutableList.toImmutableList()).block(), bobSession);

        assertThat(Flux.from(messageVault.search(BOB, Query.ALL)).blockFirst())
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
                .forUser(BOB)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.DeleteMessages, MailboxACL.Right.PerformExpunge)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox, bobMailbox), bobSession);

        bobMessageManager.delete(Flux.from(bobMessageManager.search(searchQuery, bobSession)).collect(ImmutableList.toImmutableList()).block(), bobSession);

        assertThat(Flux.from(messageVault.search(ALICE, Query.ALL)).collectList().block())
            .isEmpty();
    }

    private long messageSize(MessageManager messageManager, ComposedMessageId composedMessageId) throws MailboxException {
        return messageManager.getMessages(MessageRange.one(composedMessageId.getUid()), FetchGroup.MINIMAL, aliceSession)
            .next()
            .getSize();
    }
}
