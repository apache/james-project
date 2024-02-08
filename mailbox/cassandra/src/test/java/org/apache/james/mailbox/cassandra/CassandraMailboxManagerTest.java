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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.apache.james.mailbox.cassandra.mail.CassandraThreadDAOTest.hashMimeMessagesIds;
import static org.apache.james.mailbox.cassandra.mail.CassandraThreadDAOTest.hashSubject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.BlobTables;
import org.apache.james.core.Username;
import org.apache.james.events.EventBus;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.EventStoreDao;
import org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.AppendResult;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraACLDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraApplicableFlagDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraFirstUnseenDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraThreadDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraThreadLookupDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraUserMailboxRightsDAO;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.cassandra.mail.ThreadTablePartitionKey;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.ACLModule;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.ThreadInformation;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.RawField;
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

import reactor.core.publisher.Mono;

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
        return new StoreSubscriptionManager(provideMailboxManager().getMapperFactory(), provideMailboxManager().getMapperFactory(), provideMailboxManager().getEventBus());
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

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.METADATA)
                    .blockOptional()).isEmpty();

                softly.assertThat(imapUidDAO(cassandraCluster).retrieve(cassandraMessageId, Optional.of(mailboxId)).collectList().block())
                    .isEmpty();

                softly.assertThat(messageIdDAO(cassandraCluster).retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();
            });
        }

        @Test
        void deleteMailboxShouldDeleteMessageAndAttachmentBlobs(CassandraCluster cassandraCluster) throws Exception {
            inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(cassandraCluster.getConf().execute(selectFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME).all().build()))
                .isEmpty();
        }

        @Test
        void deleteMessageShouldDeleteMessageAndAttachmentBlobs(CassandraCluster cassandraCluster) throws Exception {
            AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

            assertThat(cassandraCluster.getConf().execute(selectFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME).all().build()))
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
                .whenQueryStartsWith("DELETE FROM attachmentv2 WHERE idAsUUID=:idAsUUID"));

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();
                CassandraId mailboxId = (CassandraId) appendResult.getId().getMailboxId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.METADATA)
                    .blockOptional()).isEmpty();

                softly.assertThat(imapUidDAO(cassandraCluster).retrieve(cassandraMessageId, Optional.of(mailboxId)).collectList().block())
                    .isEmpty();

                softly.assertThat(messageIdDAO(cassandraCluster).retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();
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
                .whenQueryStartsWith("DELETE FROM messagev2 WHERE messageId=:messageId"));

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();
                CassandraId mailboxId = (CassandraId) appendResult.getId().getMailboxId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.METADATA)
                    .blockOptional()).isEmpty();

                softly.assertThat(imapUidDAO(cassandraCluster).retrieve(cassandraMessageId, Optional.of(mailboxId)).collectList().block())
                    .isEmpty();

                softly.assertThat(messageIdDAO(cassandraCluster).retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();
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

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.METADATA)
                    .blockOptional()).isEmpty();

                softly.assertThat(imapUidDAO(cassandraCluster).retrieve(cassandraMessageId, Optional.of(mailboxId)).collectList().block())
                    .isEmpty();

                softly.assertThat(messageIdDAO(cassandraCluster).retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();
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
                .whenQueryStartsWith("DELETE FROM imapuidtable"));

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();
                CassandraId mailboxId = (CassandraId) appendResult.getId().getMailboxId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.METADATA)
                    .blockOptional()).isEmpty();

                softly.assertThat(imapUidDAO(cassandraCluster).retrieve(cassandraMessageId, Optional.of(mailboxId)).collectList().block())
                    .isEmpty();

                softly.assertThat(messageIdDAO(cassandraCluster).retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();
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

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.METADATA)
                    .blockOptional()).isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();
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
                .whenQueryStartsWith("DELETE FROM messagev2 WHERE messageid=:messageid"));

            inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.METADATA)
                    .blockOptional()).isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();
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
                .whenQueryStartsWith("DELETE FROM attachmentv2 WHERE idasuuid=:idasuuid"));

            inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

            SoftAssertions.assertSoftly(softly -> {
                CassandraMessageId cassandraMessageId = (CassandraMessageId) appendResult.getId().getMessageId();

                softly.assertThat(messageDAO(cassandraCluster).retrieveMessage(cassandraMessageId, MessageMapper.FetchType.METADATA)
                    .blockOptional()).isEmpty();

                softly.assertThat(attachmentDAO(cassandraCluster).getAttachment(attachmentId).blockOptional())
                    .isEmpty();
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
                .whenQueryStartsWith("DELETE FROM usermailboxacl WHERE username=:username AND mailboxid=:mailboxid"));

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
                .whenQueryStartsWith("DELETE FROM acl WHERE id=:id IF EXISTS"));

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
                .whenQueryStartsWith("DELETE FROM applicableflag WHERE mailboxid=:mailboxid"));

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
                .whenQueryStartsWith("DELETE FROM firstunseen WHERE mailboxid=:mailboxid"));

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
                .whenQueryStartsWith("DELETE FROM messagedeleted WHERE mailboxid=:mailboxid"));

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
                .whenQueryStartsWith("DELETE FROM mailboxcounters WHERE mailboxid=:mailboxid"));

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
                .whenQueryStartsWith("DELETE FROM mailboxrecents WHERE mailboxid=:mailboxid"));

            mailboxManager.deleteMailbox(inbox, session);

            assertThat(new CassandraMailboxRecentsDAO(cassandraCluster.getConf()).getRecentMessageUidsInMailbox((CassandraId) inboxId)
                    .collectList()
                    .block())
                .isEmpty();
        }

        @Test
        void deleteMailboxShouldCleanUpThreadData(CassandraCluster cassandraCluster) throws Exception {
            // append a message
            MessageManager.AppendResult message = inboxManager.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("Test")
                .setMessageId("Message-ID")
                .setField(new RawField("In-Reply-To", "someInReplyTo"))
                .addField(new RawField("References", "references1"))
                .addField(new RawField("References", "references2"))
                .setBody("testmail", StandardCharsets.UTF_8)), session);

            Set<MimeMessageId> mimeMessageIds = buildMimeMessageIdSet(Optional.of(new MimeMessageId("Message-ID")),
                Optional.of(new MimeMessageId("someInReplyTo")),
                Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("references2"))));
            saveThreadData(session.getUser(), mimeMessageIds, message.getId().getMessageId(), message.getThreadId(), Optional.of(new Subject("Test"))).block();
            CassandraMessageId cassandraMessageId = (CassandraMessageId) message.getId().getMessageId();
            ThreadTablePartitionKey partitionKey = threadLookupDAO(cassandraCluster)
                .selectOneRow(cassandraMessageId).block();

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(threadDAO(cassandraCluster)
                    .selectSome(partitionKey.getUsername(), partitionKey.getMimeMessageIds()).collectList().block())
                    .isEmpty();

                softly.assertThat(threadLookupDAO(cassandraCluster)
                    .selectOneRow(cassandraMessageId).block())
                    .isNull();
            });
        }

        @Test
        void deleteMailboxShouldCleanUpThreadDataWhenFailure(CassandraCluster cassandraCluster) throws Exception {
            // append a message
            MessageManager.AppendResult message = inboxManager.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("Test")
                .setMessageId("Message-ID")
                .setField(new RawField("In-Reply-To", "someInReplyTo"))
                .addField(new RawField("References", "references1"))
                .addField(new RawField("References", "references2"))
                .setBody("testmail", StandardCharsets.UTF_8)), session);

            Set<MimeMessageId> mimeMessageIds = buildMimeMessageIdSet(Optional.of(new MimeMessageId("Message-ID")),
                Optional.of(new MimeMessageId("someInReplyTo")),
                Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("references2"))));
            saveThreadData(session.getUser(), mimeMessageIds, message.getId().getMessageId(), message.getThreadId(), Optional.of(new Subject("Test"))).block();
            CassandraMessageId cassandraMessageId = (CassandraMessageId) message.getId().getMessageId();
            ThreadTablePartitionKey partitionKey = threadLookupDAO(cassandraCluster)
                .selectOneRow(cassandraMessageId).block();

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM threadtable"));
            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM threadlookuptable"));

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(threadDAO(cassandraCluster)
                        .selectSome(partitionKey.getUsername(), partitionKey.getMimeMessageIds()).collectList().block())
                    .isEmpty();

                softly.assertThat(threadLookupDAO(cassandraCluster)
                        .selectOneRow(cassandraMessageId).block())
                    .isNull();
            });
        }

        @Test
        void deleteMessageShouldCleanUpThreadData(CassandraCluster cassandraCluster) throws Exception {
            // append a message
            MessageManager.AppendResult message = inboxManager.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("Test")
                .setMessageId("Message-ID")
                .setField(new RawField("In-Reply-To", "someInReplyTo"))
                .addField(new RawField("References", "references1"))
                .addField(new RawField("References", "references2"))
                .setBody("testmail", StandardCharsets.UTF_8)), session);

            Set<MimeMessageId> mimeMessageIds = buildMimeMessageIdSet(Optional.of(new MimeMessageId("Message-ID")),
                Optional.of(new MimeMessageId("someInReplyTo")),
                Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("references2"))));
            saveThreadData(session.getUser(), mimeMessageIds, message.getId().getMessageId(), message.getThreadId(), Optional.of(new Subject("Test"))).block();
            CassandraMessageId cassandraMessageId = (CassandraMessageId) message.getId().getMessageId();
            ThreadTablePartitionKey partitionKey = threadLookupDAO(cassandraCluster)
                .selectOneRow(cassandraMessageId).block();

            inboxManager.delete(ImmutableList.of(message.getId().getUid()), session);

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(threadDAO(cassandraCluster)
                        .selectSome(partitionKey.getUsername(), partitionKey.getMimeMessageIds()).collectList().block())
                    .isEmpty();

                softly.assertThat(threadLookupDAO(cassandraCluster)
                        .selectOneRow(cassandraMessageId).block())
                    .isNull();
            });
        }

        @Test
        void deleteMessageShouldCleanUpThreadDataWhenFailure(CassandraCluster cassandraCluster) throws Exception {
            // append a message
            MessageManager.AppendResult message = inboxManager.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("Test")
                .setMessageId("Message-ID")
                .setField(new RawField("In-Reply-To", "someInReplyTo"))
                .addField(new RawField("References", "references1"))
                .addField(new RawField("References", "references2"))
                .setBody("testmail", StandardCharsets.UTF_8)), session);

            Set<MimeMessageId> mimeMessageIds = buildMimeMessageIdSet(Optional.of(new MimeMessageId("Message-ID")),
                Optional.of(new MimeMessageId("someInReplyTo")),
                Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("references2"))));
            saveThreadData(session.getUser(), mimeMessageIds, message.getId().getMessageId(), message.getThreadId(), Optional.of(new Subject("Test"))).block();
            CassandraMessageId cassandraMessageId = (CassandraMessageId) message.getId().getMessageId();
            ThreadTablePartitionKey partitionKey = threadLookupDAO(cassandraCluster)
                .selectOneRow(cassandraMessageId).block();

            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM threadtable"));
            cassandraCluster.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM threadlookuptable"));

            inboxManager.delete(ImmutableList.of(message.getId().getUid()), session);

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(threadDAO(cassandraCluster)
                        .selectSome(partitionKey.getUsername(), partitionKey.getMimeMessageIds()).collectList().block())
                    .isEmpty();

                softly.assertThat(threadLookupDAO(cassandraCluster)
                        .selectOneRow(cassandraMessageId).block())
                    .isNull();
            });
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
            CassandraACLDAOV2 aclDAOv2 = new CassandraACLDAOV2(cassandraCluster.getConf());
            JsonEventSerializer jsonEventSerializer = JsonEventSerializer
                .forModules(ACLModule.ACL_UPDATE)
                .withoutNestedType();
            CassandraUserMailboxRightsDAO usersRightDAO = new CassandraUserMailboxRightsDAO(cassandraCluster.getConf());
            CassandraEventStore eventStore = new CassandraEventStore(new EventStoreDao(cassandraCluster.getConf(), jsonEventSerializer));
            return new CassandraACLMapper(
                new CassandraACLMapper.StoreV2(usersRightDAO, aclDAOv2, eventStore),
                CassandraConfiguration.DEFAULT_CONFIGURATION);
        }

        private CassandraUserMailboxRightsDAO rightsDAO(CassandraCluster cassandraCluster) {
            return new CassandraUserMailboxRightsDAO(cassandraCluster.getConf());
        }

        private CassandraAttachmentDAOV2 attachmentDAO(CassandraCluster cassandraCluster) {
            return new CassandraAttachmentDAOV2(
                new HashBlobId.Factory(),
                cassandraCluster.getConf());
        }

        private CassandraMessageIdDAO messageIdDAO(CassandraCluster cassandraCluster) {
            return new CassandraMessageIdDAO(cassandraCluster.getConf(), new HashBlobId.Factory());
        }

        private CassandraMessageIdToImapUidDAO imapUidDAO(CassandraCluster cassandraCluster) {
            return new CassandraMessageIdToImapUidDAO(
                cassandraCluster.getConf(),
                new HashBlobId.Factory(),
                CassandraConfiguration.DEFAULT_CONFIGURATION);
        }

        private CassandraMessageDAOV3 messageDAO(CassandraCluster cassandraCluster) {
            return new CassandraMessageDAOV3(
                cassandraCluster.getConf(),
                cassandraCluster.getTypesProvider(),
                mock(BlobStore.class),
                new HashBlobId.Factory());
        }

        private CassandraThreadDAO threadDAO(CassandraCluster cassandraCluster) {
            return new CassandraThreadDAO(cassandraCluster.getConf());
        }

        private CassandraThreadLookupDAO threadLookupDAO(CassandraCluster cassandraCluster) {
            return new CassandraThreadLookupDAO(cassandraCluster.getConf());
        }

        private Mono<Void> saveThreadData(Username username, Set<MimeMessageId> mimeMessageIds, MessageId messageId, ThreadId threadId, Optional<Subject> baseSubject) {
            ThreadInformation.Hashed hashed = new ThreadInformation.Hashed(hashMimeMessagesIds(mimeMessageIds), hashSubject(baseSubject));
            return threadDAO(cassandra.getCassandraCluster())
                .insertSome(username, messageId, threadId, hashed)
                .then(threadLookupDAO(cassandra.getCassandraCluster())
                    .insert(messageId, username, hashMimeMessagesIds(mimeMessageIds)));
        }

        private Set<MimeMessageId> buildMimeMessageIdSet(Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references) {
            Set<MimeMessageId> mimeMessageIds = new HashSet<>();
            mimeMessageId.ifPresent(mimeMessageIds::add);
            inReplyTo.ifPresent(mimeMessageIds::add);
            references.ifPresent(mimeMessageIds::addAll);
            return mimeMessageIds;
        }
    }

    @Nested
    class WithBatchSize extends MailboxManagerTest<CassandraMailboxManager> {
        @Override
        protected CassandraMailboxManager provideMailboxManager() {
            CassandraMailboxManager mgt = CassandraMailboxManagerProvider.provideMailboxManager(
                cassandra.getCassandraCluster(),
                new PreDeletionHooks(preDeletionHooks(), new RecordingMetricFactory()),
                CassandraConfiguration.DEFAULT_CONFIGURATION,
                new MailboxManagerConfiguration(BatchSizes.uniqueBatchSize(5)));
            return mgt;
        }

        @Override
        protected SubscriptionManager provideSubscriptionManager() {
            return new StoreSubscriptionManager(provideMailboxManager().getMapperFactory(), provideMailboxManager().getMapperFactory(), provideMailboxManager().getEventBus());
        }

        @Override
        protected EventBus retrieveEventBus(CassandraMailboxManager mailboxManager) {
            return mailboxManager.getEventBus();
        }

    }
}
