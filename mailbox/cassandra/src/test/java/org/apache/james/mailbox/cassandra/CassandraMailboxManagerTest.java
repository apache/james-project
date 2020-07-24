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
package org.apache.james.mailbox.cassandra;

import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Collection;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.BlobTables;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.AppendResult;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraApplicableFlagDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentOwnerDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraFirstUnseenDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraUserMailboxRightsDAO;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.streams.Iterators;
import org.apache.james.util.streams.Limit;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class CassandraMailboxManagerTest extends MailboxManagerTest<CassandraMailboxManager> {
    public static final Username BOB = Username.of("Bob");
    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(MailboxAggregateModule.MODULE_WITH_QUOTA);

    @Override
    protected CassandraMailboxManager provideMailboxManager() {
        return CassandraMailboxManagerProvider.provideMailboxManager(
            cassandra.getCassandraCluster(),
            new PreDeletionHooks(preDeletionHooks(), new RecordingMetricFactory()));
    }

    @Override
    protected SubscriptionManager provideSubscriptionManager() {
        return new StoreSubscriptionManager(provideMailboxManager().getMapperFactory());
    }

    @Override
    protected EventBus retrieveEventBus(CassandraMailboxManager mailboxManager) {
        return mailboxManager.getEventBus();
    }

    @Nested
    class DeletionTests {
        private MailboxSession session;
        private MailboxPath inbox;
        private MailboxId inboxId;
        private MessageManager inboxManager;
        private MessageManager otherBoxManager;
        private MailboxPath newPath;

        @BeforeEach
        void setUp() throws Exception {
            session = mailboxManager.createSystemSession(USER_1);
            inbox = MailboxPath.inbox(session);
            newPath = MailboxPath.forUser(USER_1, "specialMailbox");

            inboxId = mailboxManager.createMailbox(inbox, session).get();
            inboxManager = mailboxManager.getMailbox(inbox, session);
            MailboxId otherId = mailboxManager.createMailbox(newPath, session).get();
            otherBoxManager = mailboxManager.getMailbox(otherId, session);
        }

        @Test
        void deleteMailboxShouldUnreferenceMessageMetadata(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            AttachmentId attachmentId = Iterators.toStream(inboxManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(Throwing.function(MessageResult::getLoadedAttachments))
                .flatMap(Collection::stream)
                .map(MessageAttachmentMetadata::getAttachmentId)
                .findFirst()
                .get();

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();
                CassandraId mailboxId = (CassandraId) appendResult.getId().getMailboxId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.Metadata)
                    .blockOptional()).isEmpty();

                softly.assertThat(imapUidDAO(cassandraCluster).retrieve(cassandraMessageId, Optional.of(mailboxId)).collectList().block())
                    .isEmpty();

                softly.assertThat(messageIdDAO(cassandraCluster).retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();

                softly.assertThat(attachmentMessageIdDAO(cassandraCluster).getOwnerMessageIds(attachmentId).collectList().block())
                    .doesNotContain(cassandraMessageId);
            });
        }

        @Test
        void deleteMailboxShouldDeleteMessageAndAttachmentBlobs(CassandraCluster cassandraCluster) throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(cassandraCluster.getConf().execute(select().from(BlobTables.DefaultBucketBlobTable.TABLE_NAME)))
                .isEmpty();
        }

        @Test
        void deleteMessageShouldDeleteMessageAndAttachmentBlobs(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

            assertThat(cassandraCluster.getConf().execute(select().from(BlobTables.DefaultBucketBlobTable.TABLE_NAME)))
                .isEmpty();
        }

        @Test
        void deleteMailboxShouldEventuallyUnreferenceMessageMetadataWhenDeleteAttachmentFails(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            AttachmentId attachmentId = Iterators.toStream(inboxManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(Throwing.function(MessageResult::getLoadedAttachments))
                .flatMap(Collection::stream)
                .map(MessageAttachmentMetadata::getAttachmentId)
                .findFirst()
                .get();

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM attachmentV2 WHERE idAsUUID=:idAsUUID;"));

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();
                CassandraId mailboxId = (CassandraId) appendResult.getId().getMailboxId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.Metadata)
                    .blockOptional()).isEmpty();

                softly.assertThat(imapUidDAO(cassandraCluster).retrieve(cassandraMessageId, Optional.of(mailboxId)).collectList().block())
                    .isEmpty();

                softly.assertThat(messageIdDAO(cassandraCluster).retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();

                softly.assertThat(attachmentMessageIdDAO(cassandraCluster).getOwnerMessageIds(attachmentId).collectList().block())
                    .doesNotContain(cassandraMessageId);
            });
        }

        @Test
        void deleteMailboxShouldEventuallyUnreferenceMessageMetadataWhenDeleteMessageFails(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            AttachmentId attachmentId = Iterators.toStream(inboxManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(Throwing.function(MessageResult::getLoadedAttachments))
                .flatMap(Collection::stream)
                .map(MessageAttachmentMetadata::getAttachmentId)
                .findFirst()
                .get();

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM messageV2 WHERE messageId=:messageId;"));

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();
                CassandraId mailboxId = (CassandraId) appendResult.getId().getMailboxId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.Metadata)
                    .blockOptional()).isEmpty();

                softly.assertThat(imapUidDAO(cassandraCluster).retrieve(cassandraMessageId, Optional.of(mailboxId)).collectList().block())
                    .isEmpty();

                softly.assertThat(messageIdDAO(cassandraCluster).retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();

                softly.assertThat(attachmentMessageIdDAO(cassandraCluster).getOwnerMessageIds(attachmentId).collectList().block())
                    .doesNotContain(cassandraMessageId);
            });
        }

        @Test
        void deleteMailboxShouldEventuallyUnreferenceMessageMetadataWhenDeleteMailboxContextFails(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            AttachmentId attachmentId = Iterators.toStream(inboxManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(Throwing.function(MessageResult::getLoadedAttachments))
                .flatMap(Collection::stream)
                .map(MessageAttachmentMetadata::getAttachmentId)
                .findFirst()
                .get();

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM messageIdTable"));

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();
                CassandraId mailboxId = (CassandraId) appendResult.getId().getMailboxId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.Metadata)
                    .blockOptional()).isEmpty();

                softly.assertThat(imapUidDAO(cassandraCluster).retrieve(cassandraMessageId, Optional.of(mailboxId)).collectList().block())
                    .isEmpty();

                softly.assertThat(messageIdDAO(cassandraCluster).retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();

                softly.assertThat(attachmentMessageIdDAO(cassandraCluster).getOwnerMessageIds(attachmentId).collectList().block())
                    .doesNotContain(cassandraMessageId);
            });
        }

        @Test
        void deleteMailboxShouldEventuallyUnreferenceMessageMetadataWhenDeleteMailboxContextByIdFails(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            AttachmentId attachmentId = Iterators.toStream(inboxManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(Throwing.function(MessageResult::getLoadedAttachments))
                .flatMap(Collection::stream)
                .map(MessageAttachmentMetadata::getAttachmentId)
                .findFirst()
                .get();

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM imapUidTable"));

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();
                CassandraId mailboxId = (CassandraId) appendResult.getId().getMailboxId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.Metadata)
                    .blockOptional()).isEmpty();

                softly.assertThat(imapUidDAO(cassandraCluster).retrieve(cassandraMessageId, Optional.of(mailboxId)).collectList().block())
                    .isEmpty();

                softly.assertThat(messageIdDAO(cassandraCluster).retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();

                softly.assertThat(attachmentMessageIdDAO(cassandraCluster).getOwnerMessageIds(attachmentId).collectList().block())
                    .doesNotContain(cassandraMessageId);
            });
        }

        @Test
        void deleteShouldUnreferenceMessageMetadata(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            AttachmentId attachmentId = Iterators.toStream(inboxManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(Throwing.function(MessageResult::getLoadedAttachments))
                .flatMap(Collection::stream)
                .map(MessageAttachmentMetadata::getAttachmentId)
                .findFirst()
                .get();

            inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.Metadata)
                    .blockOptional()).isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();

                softly.assertThat(attachmentMessageIdDAO(cassandraCluster).getOwnerMessageIds(attachmentId).collectList().block())
                    .doesNotContain(cassandraMessageId);
            });
        }

        @Test
        void deleteShouldUnreferenceMessageMetadataWhenDeleteMessageFails(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            AttachmentId attachmentId = Iterators.toStream(inboxManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(Throwing.function(MessageResult::getLoadedAttachments))
                .flatMap(Collection::stream)
                .map(MessageAttachmentMetadata::getAttachmentId)
                .findFirst()
                .get();

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM messageV2 WHERE messageId=:messageId;"));

            inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.Metadata)
                    .blockOptional()).isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();

                softly.assertThat(attachmentMessageIdDAO(cassandraCluster).getOwnerMessageIds(attachmentId).collectList().block())
                    .doesNotContain(cassandraMessageId);
            });
        }

        @Test
        void deleteShouldUnreferenceMessageMetadataWhenDeleteAttachmentFails(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            AttachmentId attachmentId = Iterators.toStream(inboxManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(Throwing.function(MessageResult::getLoadedAttachments))
                .flatMap(Collection::stream)
                .map(MessageAttachmentMetadata::getAttachmentId)
                .findFirst()
                .get();

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM attachmentV2 WHERE idAsUUID=:idAsUUID;"));

            inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.Metadata)
                    .blockOptional()).isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();

                softly.assertThat(attachmentMessageIdDAO(cassandraCluster).getOwnerMessageIds(attachmentId).collectList().block())
                    .doesNotContain(cassandraMessageId);
            });
        }

        @Test
        void deleteMailboxShouldNotUnreferenceMessageMetadataWhenOtherReference(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            AttachmentId attachmentId = Iterators.toStream(inboxManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(Throwing.function(MessageResult::getLoadedAttachments))
                .flatMap(Collection::stream)
                .map(MessageAttachmentMetadata::getAttachmentId)
                .findFirst()
                .get();

            mailboxManager.copyMessages(MessageRange.all(), inboxId, otherBoxManager.getId(), session);

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();
                CassandraId mailboxId = (CassandraId) appendResult.getId().getMailboxId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.Metadata)
                    .blockOptional()).isPresent();

                softly.assertThat(imapUidDAO(cassandraCluster).retrieve(cassandraMessageId, Optional.of(mailboxId)).collectList().block())
                    .isEmpty();

                softly.assertThat(messageIdDAO(cassandraCluster).retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isPresent();

                softly.assertThat(attachmentMessageIdDAO(cassandraCluster).getOwnerMessageIds(attachmentId).collectList().block())
                    .contains(cassandraMessageId);
            });
        }

        @Test
        void deleteShouldNotUnreferenceMessageMetadataWhenOtherReference(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            AttachmentId attachmentId = Iterators.toStream(inboxManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(Throwing.function(MessageResult::getLoadedAttachments))
                .flatMap(Collection::stream)
                .map(MessageAttachmentMetadata::getAttachmentId)
                .findFirst()
                .get();

            mailboxManager.copyMessages(MessageRange.all(), inboxId, otherBoxManager.getId(), session);

            inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.Metadata)
                    .blockOptional()).isPresent();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isPresent();

                softly.assertThat(attachmentMessageIdDAO(cassandraCluster).getOwnerMessageIds(attachmentId).collectList().block())
                    .contains(cassandraMessageId);
            });
        }

        @Test
        void deleteMailboxShouldNotUnreferenceAttachmentWhenOtherReference(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            AttachmentId attachmentId = Iterators.toStream(inboxManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(Throwing.function(MessageResult::getLoadedAttachments))
                .flatMap(Collection::stream)
                .map(MessageAttachmentMetadata::getAttachmentId)
                .findFirst()
                .get();

            new CassandraAttachmentOwnerDAO(cassandraCluster.getConf()).addOwner(attachmentId, Username.of("bob")).block();

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();
                CassandraId mailboxId = (CassandraId) appendResult.getId().getMailboxId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.Metadata)
                    .blockOptional()).isEmpty();

                softly.assertThat(imapUidDAO(cassandraCluster).retrieve(cassandraMessageId, Optional.of(mailboxId)).collectList().block())
                    .isEmpty();

                softly.assertThat(messageIdDAO(cassandraCluster).retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isPresent();

                softly.assertThat(attachmentMessageIdDAO(cassandraCluster).getOwnerMessageIds(attachmentId).collectList().block())
                    .doesNotContain(cassandraMessageId);
            });
        }

        @Test
        void deleteShouldNotUnreferenceAttachmentWhenOtherReference(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            AttachmentId attachmentId = Iterators.toStream(inboxManager.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(Throwing.function(MessageResult::getLoadedAttachments))
                .flatMap(Collection::stream)
                .map(MessageAttachmentMetadata::getAttachmentId)
                .findFirst()
                .get();

            new CassandraAttachmentOwnerDAO(cassandraCluster.getConf()).addOwner(attachmentId, Username.of("bob")).block();

            inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.Metadata)
                    .blockOptional()).isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isPresent();

                softly.assertThat(attachmentMessageIdDAO(cassandraCluster).getOwnerMessageIds(attachmentId).collectList().block())
                    .doesNotContain(cassandraMessageId);
            });
        }

        @Test
        void deleteMailboxShouldCleanupACL(CassandraCluster cassandraCluster) throws Exception {
            mailboxManager.setRights(inboxId, new MailboxACL(
                Pair.of(MailboxACL.EntryKey.createUserEntryKey(BOB), new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read))), session);

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraId id = (CassandraId) this.inboxId;

                softly.assertThat(aclMapper(cassandraCluster).getACL(id).blockOptional()).isEmpty();

                softly.assertThat(rightsDAO(cassandraCluster).listRightsForUser(BOB).collectList().block()).isEmpty();
            });
        }

        @Test
        void deleteMailboxShouldCleanupACLWhenRightsDeleteFails(CassandraCluster cassandraCluster) throws Exception {
            mailboxManager.setRights(inboxId, new MailboxACL(
                Pair.of(MailboxACL.EntryKey.createUserEntryKey(BOB), new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read))), session);

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM UserMailboxACL WHERE userName=:userName AND mailboxid=:mailboxid;"));

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraId id = (CassandraId) this.inboxId;

                softly.assertThat(aclMapper(cassandraCluster).getACL(id).blockOptional()).isEmpty();

                softly.assertThat(rightsDAO(cassandraCluster).listRightsForUser(BOB).collectList().block()).isEmpty();
            });
        }

        @Test
        void deleteMailboxShouldCleanupACLWhenACLDeleteFails(CassandraCluster cassandraCluster) throws Exception {
            mailboxManager.setRights(inboxId, new MailboxACL(
                Pair.of(MailboxACL.EntryKey.createUserEntryKey(BOB), new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read))), session);

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM acl WHERE id=:id IF EXISTS;"));

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraId id = (CassandraId) this.inboxId;

                softly.assertThat(aclMapper(cassandraCluster).getACL(id).blockOptional()).isEmpty();

                softly.assertThat(rightsDAO(cassandraCluster).listRightsForUser(BOB).collectList().block()).isEmpty();
            });
        }

        @Test
        void deleteMailboxShouldCleanUpApplicableFlags(CassandraCluster cassandraCluster) throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags("custom"))
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(applicableFlagDAO(cassandraCluster).retrieveApplicableFlag((CassandraId) inboxId).blockOptional())
                .isEmpty();
        }

        @Test
        void deleteMailboxShouldCleanUpApplicableFlagsAfterAFailure(CassandraCluster cassandraCluster) throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags("custom"))
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM applicableFlag WHERE mailboxId=:mailboxId;"));

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(applicableFlagDAO(cassandraCluster).retrieveApplicableFlag((CassandraId) inboxId).blockOptional())
                .isEmpty();
        }

        @Test
        void deleteMailboxShouldCleanUpFirstUnseenWhenFail(CassandraCluster cassandraCluster) throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags())
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(firstUnseenDAO(cassandraCluster).retrieveFirstUnread((CassandraId) inboxId).blockOptional())
                .isEmpty();
        }

        @Test
        void deleteMailboxShouldCleanUpFirstUnseen(CassandraCluster cassandraCluster) throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags())
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM firstUnseen WHERE mailboxId=:mailboxId;"));

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(firstUnseenDAO(cassandraCluster).retrieveFirstUnread((CassandraId) inboxId).blockOptional())
                .isEmpty();
        }

        @Test
        void deleteMailboxShouldCleanUpDeletedMessages(CassandraCluster cassandraCluster) throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags(Flags.Flag.DELETED))
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(deletedMessageDAO(cassandraCluster).retrieveDeletedMessage((CassandraId) inboxId, MessageRange.all())
                    .collectList()
                    .block())
                .isEmpty();
        }

        @Test
        void deleteMailboxShouldCleanUpDeletedMessagesWhenFailure(CassandraCluster cassandraCluster) throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags(Flags.Flag.DELETED))
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM messageDeleted WHERE mailboxId=:mailboxId;"));

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(deletedMessageDAO(cassandraCluster).retrieveDeletedMessage((CassandraId) inboxId, MessageRange.all())
                    .collectList()
                    .block())
                .isEmpty();
        }

        @Test
        void deleteMailboxShouldCleanUpMailboxCounters(CassandraCluster cassandraCluster) throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(countersDAO(cassandraCluster).retrieveMailboxCounters((CassandraId) inboxId)
                    .blockOptional())
                .isEmpty();
        }

        @Test
        void deleteMailboxShouldCleanUpMailboxCountersWhenFailure(CassandraCluster cassandraCluster) throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM mailboxCounters WHERE mailboxId=:mailboxId;"));

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(countersDAO(cassandraCluster).retrieveMailboxCounters((CassandraId) inboxId)
                    .blockOptional())
                .isEmpty();
        }

        @Test
        void deleteMailboxShouldCleanUpRecent(CassandraCluster cassandraCluster) throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags(Flags.Flag.RECENT))
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(new CassandraMailboxRecentsDAO(cassandraCluster.getConf()).getRecentMessageUidsInMailbox((CassandraId) inboxId)
                    .collectList()
                    .block())
                .isEmpty();
        }

        @Test
        void deleteMailboxShouldCleanUpRecentWhenFailure(CassandraCluster cassandraCluster) throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(new Flags(Flags.Flag.RECENT))
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);


            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM mailboxRecents WHERE mailboxId=:mailboxId;"));

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(new CassandraMailboxRecentsDAO(cassandraCluster.getConf()).getRecentMessageUidsInMailbox((CassandraId) inboxId)
                    .collectList()
                    .block())
                .isEmpty();
        }

        private CassandraMailboxCounterDAO countersDAO(CassandraCluster cassandraCluster) {
            return new CassandraMailboxCounterDAO(cassandraCluster.getConf());
        }

        private CassandraDeletedMessageDAO deletedMessageDAO(CassandraCluster cassandraCluster) {
            return new CassandraDeletedMessageDAO(cassandraCluster.getConf());
        }

        private CassandraFirstUnseenDAO firstUnseenDAO(CassandraCluster cassandraCluster) {
            return new CassandraFirstUnseenDAO(cassandraCluster.getConf());
        }

        private CassandraApplicableFlagDAO applicableFlagDAO(CassandraCluster cassandraCluster) {
            return new CassandraApplicableFlagDAO(cassandraCluster.getConf());
        }

        private CassandraACLMapper aclMapper(CassandraCluster cassandraCluster) {
            return new CassandraACLMapper(
                cassandraCluster.getConf(),
                rightsDAO(cassandraCluster),
                CassandraConfiguration.DEFAULT_CONFIGURATION,
                cassandra.getCassandraConsistenciesConfiguration());
        }

        private CassandraUserMailboxRightsDAO rightsDAO(CassandraCluster cassandraCluster) {
            return new CassandraUserMailboxRightsDAO(cassandraCluster.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
        }

        private CassandraAttachmentMessageIdDAO attachmentMessageIdDAO(CassandraCluster cassandraCluster) {
            return new CassandraAttachmentMessageIdDAO(cassandraCluster.getConf(), new CassandraMessageId.Factory());
        }

        private CassandraAttachmentDAOV2 attachmentDAO(CassandraCluster cassandraCluster) {
            return new CassandraAttachmentDAOV2(
                new HashBlobId.Factory(),
                cassandraCluster.getConf(),
                cassandra.getCassandraConsistenciesConfiguration());
        }

        private CassandraMessageIdDAO messageIdDAO(CassandraCluster cassandraCluster) {
            return new CassandraMessageIdDAO(cassandraCluster.getConf(), new CassandraMessageId.Factory());
        }

        private CassandraMessageIdToImapUidDAO imapUidDAO(CassandraCluster cassandraCluster) {
            return new CassandraMessageIdToImapUidDAO(
                cassandraCluster.getConf(),
                cassandra.getCassandraConsistenciesConfiguration(),
                new CassandraMessageId.Factory());
        }

        private CassandraMessageDAO messageDAO(CassandraCluster cassandraCluster) {
            return new CassandraMessageDAO(
                cassandraCluster.getConf(),
                cassandraCluster.getTypesProvider(),
                mock(BlobStore.class),
                new HashBlobId.Factory(),
                new CassandraMessageId.Factory(),
                cassandra.getCassandraConsistenciesConfiguration());
        }
    }
}
