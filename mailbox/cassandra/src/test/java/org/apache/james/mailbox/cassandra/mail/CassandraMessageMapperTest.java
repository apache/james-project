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

package org.apache.james.mailbox.cassandra.mail;

import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_SECOND;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.backends.cassandra.StatementRecorder.Selector;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageAssert;
import org.apache.james.mailbox.store.mail.model.MessageMapperTest;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.streams.Limit;
import org.apache.james.utils.UpdatableTickingClock;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

class CassandraMessageMapperTest extends MessageMapperTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MailboxAggregateModule.MODULE);

    private CassandraMapperProvider cassandraMapperProvider;

    @Override
    protected MapperProvider createMapperProvider() {
        cassandraMapperProvider = new CassandraMapperProvider(
            cassandraCluster.getCassandraCluster(),
            CassandraConfiguration.DEFAULT_CONFIGURATION);
        return cassandraMapperProvider;
    }

    @Override
    protected UpdatableTickingClock updatableTickingClock() {
        return cassandraMapperProvider.getUpdatableTickingClock();
    }

    @Nested
    class StatementLimitationTests {
        @Test
        void updateFlagsShouldNotRetryOnDeletedMessages(CassandraCluster cassandra) throws MailboxException {
            saveMessages();

            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("DELETE FROM messageidtable"));
            try {
                messageMapper.deleteMessages(benwaInboxMailbox, ImmutableList.of(message1.getUid(), message2.getUid(), message3.getUid()));
            } catch (Exception e) {
                // expected
            }

            StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

            FlagsUpdateCalculator markAsRead = new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.ADD);
            messageMapper.updateFlags(benwaInboxMailbox, markAsRead, MessageRange.all());

            assertThat(statementRecorder.listExecutedStatements(Selector.preparedStatementStartingWith(
                "UPDATE modseq SET nextmodseq=:nextmodseq WHERE mailboxid=:mailboxid")))
                .hasSize(2);
        }

        @Test
        void deleteMessagesShouldGroupMessageReads(CassandraCluster cassandra) throws MailboxException {
            saveMessages();

            StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

            messageMapper.deleteMessages(benwaInboxMailbox, ImmutableList.of(message1.getUid(), message2.getUid(), message3.getUid()));

            assertThat(statementRecorder.listExecutedStatements(Selector.preparedStatementStartingWith(
                "SELECT * FROM messageidtable WHERE mailboxid=:mailboxid AND ")))
                .hasSize(1);
        }

        @Test
        void deleteMessagesShouldGroupCounterUpdates(CassandraCluster cassandra) throws MailboxException {
            saveMessages();

            StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

            messageMapper.deleteMessages(benwaInboxMailbox, ImmutableList.of(message1.getUid(), message2.getUid(), message3.getUid()));

            assertThat(statementRecorder.listExecutedStatements(
                Selector.preparedStatementStartingWith("UPDATE mailboxcounters SET ")))
                .hasSize(1);
        }

        @Test
        void deleteMessagesShouldNotDeleteMessageNotMarkedAsDeletedInDeletedProjection(CassandraCluster cassandra) throws MailboxException {
            saveMessages();

            StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

            messageMapper.deleteMessages(benwaInboxMailbox, ImmutableList.of(message1.getUid(), message2.getUid(), message3.getUid()));

            assertThat(statementRecorder.listExecutedStatements(
                Selector.preparedStatement("DELETE FROM messageDeleted WHERE mailboxId=:mailboxId AND uid=:uid;")))
                .isEmpty();
        }

        @Test
        void deleteMessagesShouldNotDeleteMessageNotMarkedAsRecentInRecentProjection(CassandraCluster cassandra) throws MailboxException {
            saveMessages();

            StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

            messageMapper.deleteMessages(benwaInboxMailbox, ImmutableList.of(message1.getUid(), message2.getUid(), message3.getUid()));

            assertThat(statementRecorder.listExecutedStatements(
                Selector.preparedStatement("DELETE FROM messageDeleted WHERE mailboxId=:mailboxId AND uid=:uid;")))
                .isEmpty();
        }

        @Test
        void deleteMessagesShouldNotDeleteMessageNotMarkedAsUnSeenInFirstUnseenProjection(CassandraCluster cassandra) throws MailboxException {
            saveMessages();
            FlagsUpdateCalculator markAsRead = new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.ADD);
            messageMapper.updateFlags(benwaInboxMailbox, markAsRead, MessageRange.all());

            StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

            messageMapper.deleteMessages(benwaInboxMailbox, ImmutableList.of(message1.getUid(), message2.getUid(), message3.getUid()));

            assertThat(statementRecorder.listExecutedStatements(
                Selector.preparedStatement("DELETE FROM firstunseen WHERE mailboxid=:mailboxid AND uid=:uid")))
                .isEmpty();
        }

        @Test
        void updateFlagsShouldUpdateMailboxCountersOnce(CassandraCluster cassandra) throws MailboxException {
            saveMessages();

            StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

            messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.REPLACE), MessageRange.all());


            assertThat(statementRecorder.listExecutedStatements(Selector.preparedStatementStartingWith(
                "UPDATE mailboxcounters SET ")))
                .hasSize(1);
        }

        @Test
        void findInMailboxLimitShouldLimitProjectionReadCassandraQueries(CassandraCluster cassandra) throws MailboxException {
            saveMessages();

            StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

            int limit = 2;
            consume(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.FULL, limit));


            assertThat(statementRecorder.listExecutedStatements(Selector.preparedStatement(
                "SELECT * FROM messagev3 WHERE messageid=:messageid")))
                .hasSize(limit);
        }

        @Test
        void updateFlagsShouldLimitModSeqAllocation(CassandraCluster cassandra) throws MailboxException {
            saveMessages();

            StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

            messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.ANSWERED), MessageManager.FlagsUpdateMode.REPLACE), MessageRange.all());

            assertThat(statementRecorder.listExecutedStatements(Selector.preparedStatementStartingWith(
                "UPDATE modseq SET nextmodseq=:nextmodseq WHERE mailboxid=:mailboxid")))
                .hasSize(1);
        }

        private void consume(Iterator<MailboxMessage> inMailbox) {
            ImmutableList.copyOf(inMailbox);
        }
    }

    @Nested
    class FailureTesting {
        @Test
        void retrieveMessagesShouldNotReturnMessagesWhenFailToPersistInMessageDAO(CassandraCluster cassandra) {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("UPDATE messagev3"));

            try {
                messageMapper.add(benwaInboxMailbox, message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new HashBlobId.Factory());
            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.METADATA, 1))
                    .toIterable()
                    .isEmpty();
                softly.assertThat(messageIdDAO.retrieveMessages((CassandraId) benwaInboxMailbox.getMailboxId(), MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();
            }));
        }

        @Test
        void retrieveMessagesShouldNotReturnMessagesWhenFailsToPersistBlobParts(CassandraCluster cassandra) {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO blobparts (id,chunknumber,data)"));

            try {
                messageMapper.add(benwaInboxMailbox, message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new HashBlobId.Factory());
            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.METADATA, 1))
                    .toIterable()
                    .isEmpty();
                softly.assertThat(messageIdDAO.retrieveMessages((CassandraId) benwaInboxMailbox.getMailboxId(), MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();
            }));
        }

        @Test
        void retrieveMessagesShouldNotReturnMessagesWhenFailsToPersistBlobs(CassandraCluster cassandra) {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO blobs (id,position)"));

            try {
                messageMapper.add(benwaInboxMailbox, message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new HashBlobId.Factory());
            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.METADATA, 1))
                    .toIterable()
                    .isEmpty();
                softly.assertThat(messageIdDAO.retrieveMessages((CassandraId) benwaInboxMailbox.getMailboxId(), MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();
            }));
        }

        @Test
        void retrieveMessagesShouldNotReturnMessagesWhenFailsToPersistInImapUidTable(CassandraCluster cassandra) {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO imapuidtable"));

            try {
                messageMapper.add(benwaInboxMailbox, message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new HashBlobId.Factory());
            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.METADATA, 1))
                    .toIterable()
                    .isEmpty();
                softly.assertThat(messageIdDAO.retrieveMessages((CassandraId) benwaInboxMailbox.getMailboxId(), MessageRange.all(), Limit.unlimited()).collectList().block())
                    .isEmpty();
            }));
        }

        @Test
        void addShouldPersistInTableOfTruthWhenMessageIdTableWritesFails(CassandraCluster cassandra) {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("UPDATE messageidtable"));

            try {
                messageMapper.add(benwaInboxMailbox, message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdToImapUidDAO imapUidDAO = new CassandraMessageIdToImapUidDAO(
                cassandra.getConf(),
                new HashBlobId.Factory(),
                CassandraConfiguration.DEFAULT_CONFIGURATION);

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.METADATA, 1))
                    .toIterable()
                    .isEmpty();
                softly.assertThat(imapUidDAO.retrieve((CassandraMessageId) message1.getMessageId(), Optional.empty()).collectList().block())
                    .hasSize(1);
            }));
        }

        @Test
        void addShouldRetryMessageDenormalization(CassandraCluster cassandra) throws Exception {
            cassandra.getConf()
                .registerScenario(fail()
                    .times(5)
                    .whenQueryStartsWith("INSERT INTO messageidtable"));

            messageMapper.add(benwaInboxMailbox, message1);

            assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.METADATA, 1))
                .toIterable()
                .hasSize(1);
        }
    }

    @Nested
    class ReadRepairsTesting {
        @Test
        void readingShouldEventuallyFixCountersInconsistencies(CassandraCluster cassandra) throws MailboxException {
            saveMessages();
            FlagsUpdateCalculator newFlags = new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.REPLACE);
            messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), newFlags);
            // Expected count of unseen is 4 see MessageMapperTest::mailboxUnSeenCountShouldBeDecrementedAfterAMessageIsMarkedSeen

            // Create an inconsistency
            new CassandraMailboxCounterDAO(cassandra.getConf())
                .incrementUnseenAndCount((CassandraId) benwaInboxMailbox.getMailboxId())
                .block();

            // 100 poll with a 0.1 probability to trigger read repair
            Awaitility.await()
                .pollInterval(Duration.ofMillis(10))
                .atMost(ONE_SECOND)
                .untilAsserted(() ->
                    assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(4));
        }

        @Test
        void readingShouldEventuallyFixMissingCountersInconsistencies(CassandraCluster cassandra) throws MailboxException {
            saveMessages();
            FlagsUpdateCalculator newFlags = new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.REPLACE);
            messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), newFlags);
            // Expected count of unseen is 4 see MessageMapperTest::mailboxUnSeenCountShouldBeDecrementedAfterAMessageIsMarkedSeen

            // Create an inconsistency
            new CassandraMailboxCounterDAO(cassandra.getConf())
                .delete((CassandraId) benwaInboxMailbox.getMailboxId())
                .block();

            // 100 poll with a 0.1 probability to trigger read repair
            Awaitility.await()
                .pollInterval(Duration.ofMillis(10))
                .atMost(ONE_SECOND)
                .untilAsserted(() ->
                    assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(4));
        }

        @Test
        void readingShouldFixInvalidCounters(CassandraCluster cassandra) throws MailboxException {
            saveMessages();
            FlagsUpdateCalculator newFlags = new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.REPLACE);
            messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), newFlags);
            // Expected count of unseen is 4 see MessageMapperTest::mailboxUnSeenCountShouldBeDecrementedAfterAMessageIsMarkedSeen

            // Create an inconsistency
            new CassandraMailboxCounterDAO(cassandra.getConf())
                .incrementUnseen((CassandraId) benwaInboxMailbox.getMailboxId())
                .repeat(5)
                .blockLast();

            assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(4);
        }
    }

    @Test
    void messagesRetrievedUsingFetchTypeAttachmentsMetadataShouldNotHaveBodyDataLoaded() throws MailboxException, IOException {
        saveMessages();
        MessageMapper.FetchType fetchType = FetchType.ATTACHMENTS_METADATA;
        MailboxMessage retrievedMessage = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), fetchType, 1).next();
        MessageAssert.assertThat(retrievedMessage).isEqualToWithoutUid(message1, fetchType);
        assertThat(retrievedMessage.getBodyContent().readAllBytes()).isEmpty();
    }

    @Test
    void messagesRetrievedUsingFetchTypeAttachmentsMetadataShouldHaveAttachmentsMetadataLoaded() throws MailboxException {
        AttachmentMapper attachmentMapper = mapperProvider.createAttachmentMapper();
        MessageId messageId = mapperProvider.generateMessageId();
        String content = "Subject: Test1 \n\nBody1\n.\n";
        ParsedAttachment attachment1 = ParsedAttachment.builder()
            .contentType("content")
            .content(ByteSource.wrap("attachment".getBytes(StandardCharsets.UTF_8)))
            .noName()
            .cid(Cid.from("cid"))
            .inline();
        List<MessageAttachmentMetadata> messageAttachments = attachmentMapper.storeAttachments(ImmutableList.of(attachment1), messageId);
        MailboxMessage message = new SimpleMailboxMessage(messageId, ThreadId.fromBaseMessageId(messageId), new Date(), content.length(), 16,
            new ByteContent(content.getBytes()), new Flags(), new PropertyBuilder().build(), benwaInboxMailbox.getMailboxId(),
            messageAttachments, Optional.empty());
        messageMapper.add(benwaInboxMailbox, message);
        message.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        MessageMapper.FetchType fetchType = FetchType.ATTACHMENTS_METADATA;
        MailboxMessage retrievedMessage = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message.getUid()), fetchType, 1).next();

        assertThat(retrievedMessage.getAttachments()).isEqualTo(message.getAttachments());
    }

    @Tag(Unstable.TAG)
    @Override
    public void setFlagsShouldWorkWithConcurrencyWithRemove() throws Exception {
        super.setFlagsShouldWorkWithConcurrencyWithRemove();
    }

    @Tag(Unstable.TAG)
    @Override
    public void userFlagsUpdateShouldWorkInConcurrentEnvironment() throws Exception {
        super.userFlagsUpdateShouldWorkInConcurrentEnvironment();
    }
}
