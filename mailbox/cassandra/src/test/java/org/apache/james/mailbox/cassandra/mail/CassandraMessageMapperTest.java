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

import java.util.Iterator;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageMapperTest;
import org.apache.james.util.streams.Limit;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.datastax.driver.core.BoundStatement;
import com.github.fge.lambdas.Throwing;

class CassandraMessageMapperTest extends MessageMapperTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MailboxAggregateModule.MODULE);
    
    @Override
    protected MapperProvider createMapperProvider() {
        return new CassandraMapperProvider(cassandraCluster.getCassandraCluster());
    }

    @Test
    void findInMailboxLimitShouldLimitProjectionReadCassandraQueries(CassandraCluster cassandra) throws MailboxException {
        saveMessages();

        StatementRecorder statementRecorder = new StatementRecorder();
        cassandra.getConf().recordStatements(statementRecorder);

        int limit = 2;
        consume(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.Full, limit));

        assertThat(statementRecorder.listExecutedStatements())
            .filteredOn(statement -> statement instanceof BoundStatement)
            .extracting(BoundStatement.class::cast)
            .extracting(statement -> statement.preparedStatement().getQueryString())
            .filteredOn(statementString -> statementString.equals("SELECT messageId,internalDate,bodyStartOctet,fullContentOctets,bodyOctets,bodyContent,headerContent,textualLineCount,properties,attachments FROM messageV2 WHERE messageId=:messageId;"))
            .hasSize(limit);
    }

    @Test
    void updateFlagsShouldLimitModSeqAllocation(CassandraCluster cassandra) throws MailboxException {
        saveMessages();

        StatementRecorder statementRecorder = new StatementRecorder();
        cassandra.getConf().recordStatements(statementRecorder);

        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.ANSWERED), MessageManager.FlagsUpdateMode.REPLACE), MessageRange.all());

        assertThat(statementRecorder.listExecutedStatements())
            .filteredOn(statement -> statement instanceof BoundStatement)
            .extracting(BoundStatement.class::cast)
            .extracting(statement -> statement.preparedStatement().getQueryString())
            .filteredOn(statementString -> statementString.equals("UPDATE modseq SET nextModseq=:nextModseq WHERE mailboxId=:mailboxId IF nextModseq=:modSeqCondition;"))
            .hasSize(1);
    }

    private void consume(Iterator<MailboxMessage> inMailbox) {
        ImmutableList.copyOf(inMailbox);
    }

    @Nested
    class FailureTesting {
        @Test
        void retrieveMessagesShouldNotReturnMessagesWhenFailToPersistInMessageDAO(CassandraCluster cassandra) {
            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("INSERT INTO messageV2 (messageId,internalDate,bodyStartOctet,fullContentOctets,bodyOctets,bodyContent,headerContent,properties,textualLineCount,attachments)"));

            try {
                messageMapper.add(benwaInboxMailbox, message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new CassandraMessageId.Factory());
            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.Metadata, 1))
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
                    .whenQueryStartsWith("INSERT INTO blobParts (id,chunkNumber,data) VALUES (:id,:chunkNumber,:data);"));

            try {
                messageMapper.add(benwaInboxMailbox, message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new CassandraMessageId.Factory());
            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.Metadata, 1))
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
                    .whenQueryStartsWith("INSERT INTO blobs (id,position) VALUES (:id,:position);"));

            try {
                messageMapper.add(benwaInboxMailbox, message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new CassandraMessageId.Factory());
            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.Metadata, 1))
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
                    .whenQueryStartsWith("INSERT INTO imapUidTable (messageId,mailboxId,uid,modSeq,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags)"));

            try {
                messageMapper.add(benwaInboxMailbox, message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new CassandraMessageId.Factory());
            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.Metadata, 1))
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
                    .whenQueryStartsWith("INSERT INTO messageIdTable (mailboxId,uid,modSeq,messageId,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags)"));

            try {
                messageMapper.add(benwaInboxMailbox, message1);
            } catch (Exception e) {
                // ignoring expected error
            }

            CassandraMessageIdToImapUidDAO imapUidDAO = new CassandraMessageIdToImapUidDAO(cassandra.getConf(), new CassandraMessageId.Factory());

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.Metadata, 1))
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
                    .whenQueryStartsWith("INSERT INTO messageIdTable (mailboxId,uid,modSeq,messageId,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags)"));

            messageMapper.add(benwaInboxMailbox, message1);

            assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), FetchType.Metadata, 1))
                .toIterable()
                .hasSize(1);
        }
    }
}
