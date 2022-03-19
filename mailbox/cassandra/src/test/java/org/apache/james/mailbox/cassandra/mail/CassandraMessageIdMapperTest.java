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

import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.core.Username;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.TestCassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MessageIdMapperTest;
import org.apache.james.util.streams.Limit;
import org.apache.james.utils.UpdatableTickingClock;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

class CassandraMessageIdMapperTest extends MessageIdMapperTest {
    private static final MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create(Username.of("benwa"));

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MailboxAggregateModule.MODULE);

    private CassandraMapperProvider mapperProvider;

    @Override
    protected CassandraMapperProvider provideMapper() {
        mapperProvider = new CassandraMapperProvider(
            cassandraCluster.getCassandraCluster(),
            CassandraConfiguration.DEFAULT_CONFIGURATION);
        return mapperProvider;
    }

    @Override
    protected UpdatableTickingClock updatableTickingClock() {
        return mapperProvider.getUpdatableTickingClock();
    }

    @Test
    void findShouldReturnCorrectElementsWhenChunking() throws Exception {
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        CassandraMailboxSessionMapperFactory mapperFactory = TestCassandraMailboxSessionMapperFactory.forTests(
            cassandraCluster.getCassandraCluster(),
            messageIdFactory,
            CassandraConfiguration.DEFAULT_CONFIGURATION,
            BatchSizes.builder()
                .fetchMetadata(3)
                .fetchHeaders(3)
                .fetchFull(3)
                .build());

        saveMessages();

        List<MailboxMessage> messages = mapperFactory.getMessageIdMapper(MAILBOX_SESSION)
            .find(
                ImmutableList.of(message1.getMessageId(),
                    message2.getMessageId(),
                    message3.getMessageId(),
                    message4.getMessageId()),
                MessageMapper.FetchType.METADATA);

        assertThat(messages)
            .extracting(MailboxMessage::getMessageId)
            .containsOnly(message1.getMessageId(), message2.getMessageId(), message3.getMessageId(), message4.getMessageId());
    }

    @Test
    @Tag(Unstable.TAG)
    /*
    https://builds.apache.org/blue/organizations/jenkins/james%2FApacheJames/detail/PR-264/5/tests
    Error
    Could not send request, session is closed
    Stacktrace
    java.lang.IllegalStateException: Could not send request, session is closed
     */
    void setFlagsShouldMinimizeMessageReads(CassandraCluster cassandra) throws Exception {
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        CassandraMailboxSessionMapperFactory mapperFactory = TestCassandraMailboxSessionMapperFactory.forTests(
            cassandraCluster.getCassandraCluster(),
            messageIdFactory,
            CassandraConfiguration.DEFAULT_CONFIGURATION,
            BatchSizes.builder()
                .fetchMetadata(3)
                .fetchHeaders(3)
                .fetchFull(3)
                .build());

        saveMessages();

        StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

        mapperFactory.getMessageIdMapper(MAILBOX_SESSION).setFlags(message1.getMessageId(),
            ImmutableList.of(message1.getMailboxId()),
            new Flags(Flags.Flag.DELETED),
            MessageManager.FlagsUpdateMode.REPLACE)
            .block();

        assertThat(statementRecorder.listExecutedStatements(
            StatementRecorder.Selector.preparedStatementStartingWith("SELECT * FROM imapuidtable")))
            .hasSize(1);
    }

    @Nested
    class FailureTest {
        @Test
        void retrieveMessagesShouldNotReturnMessagesWhenFailToPersistInMessageDAO(CassandraCluster cassandra) {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("UPDATE messagev3"));

            try {
                message1.setUid(mapperProvider.generateMessageUid());
                message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
                sut.save(message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new HashBlobId.Factory());
            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(sut.find(ImmutableList.of(message1.getMessageId()), MessageMapper.FetchType.METADATA))
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
                message1.setUid(mapperProvider.generateMessageUid());
                message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
                sut.save(message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new HashBlobId.Factory());
            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(sut.find(ImmutableList.of(message1.getMessageId()), MessageMapper.FetchType.METADATA))
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
                    .whenQueryStartsWith("INSERT INTO blobs (id,position) VALUES (:id,:position)"));

            try {
                message1.setUid(mapperProvider.generateMessageUid());
                message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
                sut.save(message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new HashBlobId.Factory());
            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(sut.find(ImmutableList.of(message1.getMessageId()), MessageMapper.FetchType.METADATA))
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
                message1.setUid(mapperProvider.generateMessageUid());
                message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
                sut.save(message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new HashBlobId.Factory());
            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(sut.find(ImmutableList.of(message1.getMessageId()), MessageMapper.FetchType.METADATA))
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
                    .whenQueryStartsWith("INSERT INTO messageidtable"));

            try {
                message1.setUid(mapperProvider.generateMessageUid());
                message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
                sut.save(message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdToImapUidDAO imapUidDAO = new CassandraMessageIdToImapUidDAO(
                cassandra.getConf(),
                new HashBlobId.Factory(),
                CassandraConfiguration.DEFAULT_CONFIGURATION);

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(sut.find(ImmutableList.of(message1.getMessageId()), MessageMapper.FetchType.METADATA))
                    .hasSize(1);
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

            message1.setUid(mapperProvider.generateMessageUid());
            message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
            sut.save(message1);

            assertThat(sut.find(ImmutableList.of(message1.getMessageId()), MessageMapper.FetchType.METADATA))
                .hasSize(1);
        }
    }
}
