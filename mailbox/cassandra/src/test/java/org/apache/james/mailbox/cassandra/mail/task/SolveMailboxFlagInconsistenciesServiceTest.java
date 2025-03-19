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

package org.apache.james.mailbox.cassandra.mail.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.Optional;
import java.util.Set;

import jakarta.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxFlagInconsistenciesService.Context;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxFlagInconsistenciesService.SolveMailboxDeletedFlagInconsistenciesStrategy;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxFlagInconsistenciesService.TargetFlag;
import org.apache.james.mailbox.cassandra.modules.CassandraAclDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageDataDefinition;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SolveMailboxFlagInconsistenciesServiceTest {
    private static final UidValidity UID_VALIDITY_1 = UidValidity.of(145);
    private static final Username USER = Username.of("user");
    private static final MailboxPath MAILBOX_PATH = MailboxPath.forUser(USER, "abc");
    private static final CassandraId CASSANDRA_ID_1 = CassandraId.timeBased();
    private static final MessageUid MESSAGE_UID_1 = MessageUid.of(1);
    private static final MessageUid MESSAGE_UID_2 = MessageUid.of(2);
    private static final Mailbox MAILBOX = new Mailbox(MAILBOX_PATH, UID_VALIDITY_1, CASSANDRA_ID_1);
    private static final CassandraId CASSANDRA_ID_2 = CassandraId.timeBased();
    private static final Mailbox MAILBOX_2 = new Mailbox(MAILBOX_PATH, UID_VALIDITY_1, CASSANDRA_ID_2);
    private static final PlainBlobId HEADER_BLOB_ID_1 = new PlainBlobId.Factory().of("abc");
    private static final CassandraMessageId MESSAGE_ID_1 = new CassandraMessageId.Factory().fromString("d2bee791-7e63-11ea-883c-95b84008f979");
    private static final CassandraMessageId MESSAGE_ID_2 = new CassandraMessageId.Factory().fromString("eeeeeeee-7e63-11ea-883c-95b84008f979");


    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraDataDefinition.aggregateModules(
            CassandraSchemaVersionDataDefinition.MODULE,
            CassandraDeletedMessageDataDefinition.MODULE,
            CassandraMessageDataDefinition.MODULE,
            CassandraMailboxDataDefinition.MODULE,
            CassandraAclDataDefinition.MODULE));

    SolveMailboxFlagInconsistenciesService testee;
    CassandraMailboxDAO mailboxDAO;
    CassandraMessageIdDAO messageIdDAO;
    CassandraDeletedMessageDAO deletedMessageDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new PlainBlobId.Factory());
        deletedMessageDAO = new CassandraDeletedMessageDAO(cassandra.getConf());
        mailboxDAO = new CassandraMailboxDAO(
            cassandra.getConf(),
            cassandra.getTypesProvider());

        SolveMailboxDeletedFlagInconsistenciesStrategy inconsistenciesStrategy = new SolveMailboxDeletedFlagInconsistenciesStrategy(deletedMessageDAO);
        testee = new SolveMailboxFlagInconsistenciesService(Set.of(inconsistenciesStrategy), messageIdDAO, mailboxDAO);
    }

    @Test
    void fixInconsistenciesShouldReturnCompletedWhenNoData() {
        assertThat(testee.fixInconsistencies(new Context(), TargetFlag.DELETED).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void fixMessageInconsistenciesShouldReturnCompletedWhenConsistentData() {
        MessageUid messageUid = MessageUid.of(1);
        CassandraMessageId messageId1 = new CassandraMessageId.Factory().generate();

        mailboxDAO.save(MAILBOX).block();
        messageIdDAO.insert(CassandraMessageMetadata.builder()
                .ids(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(CASSANDRA_ID_1, messageId1, messageUid))
                    .flags(new Flags(Flags.Flag.DELETED))
                    .modSeq(ModSeq.of(1))
                    .threadId(ThreadId.fromBaseMessageId(messageId1))
                    .build())
                .internalDate(new Date())
                .bodyStartOctet(18L)
                .headerContent(Optional.of(HEADER_BLOB_ID_1))
                .size(36L)
                .build())
            .block();

        deletedMessageDAO.addDeleted(CASSANDRA_ID_1, messageUid).block();

        Context context = new Context();
        assertThat(testee.fixInconsistencies(context, TargetFlag.DELETED).block())
            .isEqualTo(Task.Result.COMPLETED);

        assertThat(context.snapshot().processedMailboxEntries()).isEqualTo(1);
    }

    @Test
    void fixMessageInconsistenciesShouldFixInconsistency() {
        // Given inconsistent data
        mailboxDAO.save(MAILBOX).block();
        messageIdDAO.insert(CassandraMessageMetadata.builder()
                .ids(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(CASSANDRA_ID_1, MESSAGE_ID_1, MESSAGE_UID_1))
                    .flags(new Flags(Flags.Flag.DELETED))
                    .modSeq(ModSeq.of(1))
                    .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_1))
                    .build())
                .internalDate(new Date())
                .bodyStartOctet(18L)
                .headerContent(Optional.of(HEADER_BLOB_ID_1))
                .size(36L)
                .build())
            .block();

        deletedMessageDAO.addDeleted(CASSANDRA_ID_1, MESSAGE_UID_2).block();

        // When fixing inconsistencies
        assertThat(testee.fixInconsistencies(new Context(), TargetFlag.DELETED).block())
            .isEqualTo(Task.Result.COMPLETED);

        // Then the inconsistency should be fixed
        // CASSANDRA_ID_1 - MESSAGE_UID_2 should be removed
        // CASSANDRA_ID_1 - MESSAGE_UID_1 should be added
        assertThat(deletedMessageDAO.retrieveDeletedMessage(CASSANDRA_ID_1, MessageRange.all()).collectList().block())
            .contains(MESSAGE_UID_1);
    }

    @Test
    void fixInconsistenciesShouldWorkOnSeveralMailbox() {
        mailboxDAO.save(MAILBOX).block();
        mailboxDAO.save(MAILBOX_2).block();

        messageIdDAO.insert(CassandraMessageMetadata.builder()
                .ids(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(CASSANDRA_ID_1, MESSAGE_ID_1, MESSAGE_UID_1))
                    .flags(new Flags(Flags.Flag.DELETED))
                    .modSeq(ModSeq.of(1))
                    .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_1))
                    .build())
                .internalDate(new Date())
                .bodyStartOctet(18L)
                .headerContent(Optional.of(HEADER_BLOB_ID_1))
                .size(36L)
                .build())
            .block();

        messageIdDAO.insert(CassandraMessageMetadata.builder()
                .ids(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(CASSANDRA_ID_2, MESSAGE_ID_2, MESSAGE_UID_2))
                    .flags(new Flags(Flags.Flag.DELETED))
                    .modSeq(ModSeq.of(1))
                    .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_2))
                    .build())
                .internalDate(new Date())
                .bodyStartOctet(18L)
                .headerContent(Optional.of(HEADER_BLOB_ID_1))
                .size(36L)
                .build())
            .block();

        MessageUid messageUid3 = MessageUid.of(3);
        deletedMessageDAO.addDeleted(CASSANDRA_ID_1, messageUid3).block();
        deletedMessageDAO.addDeleted(CASSANDRA_ID_2, MESSAGE_UID_2).block();

        // When fixing inconsistencies
        assertThat(testee.fixInconsistencies(new Context(), TargetFlag.DELETED).block())
            .isEqualTo(Task.Result.COMPLETED);

        // Then the inconsistency should be fixed
        // CASSANDRA_ID_1 - messageUid3 should be removed
        // CASSANDRA_ID_1 - messageUid1 should be added
        // CASSANDRA_ID_2 - messageUid2 should be kept
        assertThat(deletedMessageDAO.retrieveDeletedMessage(CASSANDRA_ID_1, MessageRange.all()).collectList().block())
            .contains(MESSAGE_UID_1);
        assertThat(deletedMessageDAO.retrieveDeletedMessage(CASSANDRA_ID_2, MessageRange.all()).collectList().block())
            .contains(MESSAGE_UID_2);
    }
}