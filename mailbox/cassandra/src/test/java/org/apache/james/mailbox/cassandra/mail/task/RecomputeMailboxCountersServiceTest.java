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

import java.util.UUID;

import javax.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService.Context;
import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService.Options;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.task.Task.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import nl.jqno.equalsverifier.EqualsVerifier;

class RecomputeMailboxCountersServiceTest {
    private static final UidValidity UID_VALIDITY_1 = UidValidity.of(145);
    private static final Username USER = Username.of("user");
    private static final MailboxPath MAILBOX_PATH = MailboxPath.forUser(USER, "abc");
    private static final CassandraMessageId.Factory MESSAGE_ID_FACTORY = new CassandraMessageId.Factory();
    private static final CassandraMessageId MESSAGE_ID_1 = MESSAGE_ID_FACTORY.fromString("40ff9e30-6022-11ea-9a94-d300cbf968c0");
    private static CassandraId CASSANDRA_ID_1 = CassandraId.of(UUID.fromString("16d681e0-6023-11ea-a7f2-0f94ad804b0d"));
    private static final ComposedMessageIdWithMetaData METADATA_UNSEEN = new ComposedMessageIdWithMetaData(new ComposedMessageId(CASSANDRA_ID_1, MESSAGE_ID_1, MessageUid.of(45)), new Flags(), ModSeq.of(45));
    private static final ComposedMessageIdWithMetaData METADATA_SEEN = new ComposedMessageIdWithMetaData(new ComposedMessageId(CASSANDRA_ID_1, MESSAGE_ID_1, MessageUid.of(45)), new Flags(Flags.Flag.SEEN), ModSeq.of(45));
    private static final Mailbox MAILBOX = new Mailbox(MAILBOX_PATH, UID_VALIDITY_1, CASSANDRA_ID_1);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraMailboxModule.MODULE,
            CassandraMessageModule.MODULE,
            CassandraMailboxCounterModule.MODULE,
            CassandraAclModule.MODULE));

    CassandraMailboxDAO mailboxDAO;
    CassandraMessageIdDAO imapUidToMessageIdDAO;
    CassandraMessageIdToImapUidDAO messageIdToImapUidDAO;
    CassandraMailboxCounterDAO counterDAO;
    RecomputeMailboxCountersService testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        mailboxDAO = new CassandraMailboxDAO(cassandra.getConf(), cassandra.getTypesProvider());
        imapUidToMessageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), MESSAGE_ID_FACTORY);
        messageIdToImapUidDAO = new CassandraMessageIdToImapUidDAO(cassandra.getConf(), MESSAGE_ID_FACTORY);
        counterDAO = new CassandraMailboxCounterDAO(cassandra.getConf());
        testee = new RecomputeMailboxCountersService(mailboxDAO, imapUidToMessageIdDAO, messageIdToImapUidDAO, counterDAO);
    }

    @Test
    void optionsShouldMatchBeanContract() {
        EqualsVerifier.forClass(Options.class).verify();
    }

    @Nested
    class TrustMessageDenormalizationTest implements Contract {
        @Override
        public RecomputeMailboxCountersService testee() {
            return testee;
        }

        @Override
        public CassandraMailboxDAO mailboxDAO() {
            return mailboxDAO;
        }

        @Override
        public Options options() {
            return Options.trustMessageDenormalization();
        }

        @Override
        public CassandraMessageIdDAO imapUidToMessageIdDAO() {
            return imapUidToMessageIdDAO;
        }

        @Override
        public CassandraMessageIdToImapUidDAO messageIdToImapUidDAO() {
            return messageIdToImapUidDAO;
        }

        @Override
        public CassandraMailboxCounterDAO counterDAO() {
            return counterDAO;
        }

        @Disabled("Inconsitencies can not be corrected on the fly as trust avoid their detection")
        @Override
        public void recomputeMailboxCountersShouldIgnoreMissingMailboxListReferences() {

        }

        @Disabled("Inconsitencies can not be corrected on the fly as trust avoid their detection")
        @Override
        public void recomputeMailboxCountersShouldUseSourceOfTruthForComputation() {

        }

        @Disabled("Inconsitencies can not be corrected on the fly as trust avoid their detection")
        @Override
        public void recomputeMailboxCountersShouldIgnoreOrphanMailboxListReference() {

        }
    }

    @Nested
    class RecheckMessageDenormalizationTest implements Contract {
        @Override
        public RecomputeMailboxCountersService testee() {
            return testee;
        }

        @Override
        public CassandraMailboxDAO mailboxDAO() {
            return mailboxDAO;
        }

        @Override
        public Options options() {
            return Options.recheckMessageDenormalization();
        }

        @Override
        public CassandraMessageIdDAO imapUidToMessageIdDAO() {
            return imapUidToMessageIdDAO;
        }

        @Override
        public CassandraMessageIdToImapUidDAO messageIdToImapUidDAO() {
            return messageIdToImapUidDAO;
        }

        @Override
        public CassandraMailboxCounterDAO counterDAO() {
            return counterDAO;
        }
    }

    interface Contract {
        RecomputeMailboxCountersService testee();

        CassandraMailboxDAO mailboxDAO();

        Options options();

        CassandraMessageIdDAO imapUidToMessageIdDAO();

        CassandraMessageIdToImapUidDAO messageIdToImapUidDAO();

        CassandraMailboxCounterDAO counterDAO();

        @Test
        default void recomputeMailboxCountersShouldReturnCompletedWhenNoMailboxes() {
            assertThat(testee().recomputeMailboxCounters(new Context(), options()).block())
                .isEqualTo(Result.COMPLETED);
        }

        @Test
        default void recomputeMailboxCountersShouldReturnCompletedWhenMailboxWithNoMessages() {
            mailboxDAO().save(MAILBOX).block();

            assertThat(testee().recomputeMailboxCounters(new Context(), options()).block())
                .isEqualTo(Result.COMPLETED);
        }

        @Test
        default void recomputeMailboxCountersShouldReturnCompletedWhenMailboxWithMessages() {
            mailboxDAO().save(MAILBOX).block();
            imapUidToMessageIdDAO().insert(METADATA_UNSEEN).block();
            messageIdToImapUidDAO().insert(METADATA_UNSEEN).block();
            counterDAO().incrementUnseenAndCount(CASSANDRA_ID_1).block();
            counterDAO().incrementCount(CASSANDRA_ID_1).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(testee().recomputeMailboxCounters(new Context(), options()).block())
                .isEqualTo(Result.COMPLETED);
        }

        @Test
        default void recomputeMailboxCountersShouldReturnCompletedWhenMessageDenormalizationIssue() {
            mailboxDAO().save(MAILBOX).block();
            imapUidToMessageIdDAO().insert(METADATA_UNSEEN).block();
            messageIdToImapUidDAO().insert(METADATA_SEEN).block();
            counterDAO().incrementUnseenAndCount(CASSANDRA_ID_1).block();
            counterDAO().incrementCount(CASSANDRA_ID_1).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(testee().recomputeMailboxCounters(new Context(), options()).block())
                .isEqualTo(Result.COMPLETED);
        }

        @Test
        default void recomputeMailboxCountersShouldReturnCountersAreIncorrect() {
            mailboxDAO().save(MAILBOX).block();
            imapUidToMessageIdDAO().insert(METADATA_UNSEEN).block();
            messageIdToImapUidDAO().insert(METADATA_UNSEEN).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(testee().recomputeMailboxCounters(new Context(), options()).block())
                .isEqualTo(Result.COMPLETED);
        }

        @Test
        default void recomputeMailboxCountersShouldReturnCompletedWhenOrphanMailboxRegistration() {
            mailboxDAO().save(MAILBOX).block();
            imapUidToMessageIdDAO().insert(METADATA_UNSEEN).block();
            counterDAO().incrementUnseenAndCount(CASSANDRA_ID_1).block();
            counterDAO().incrementCount(CASSANDRA_ID_1).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(testee().recomputeMailboxCounters(new Context(), options()).block())
                .isEqualTo(Result.COMPLETED);
        }

        @Test
        default void recomputeMailboxCountersShouldReturnCompletedWhenMailboxListReferenceIsMissing() {
            mailboxDAO().save(MAILBOX).block();
            messageIdToImapUidDAO().insert(METADATA_UNSEEN).block();
            counterDAO().incrementUnseenAndCount(CASSANDRA_ID_1).block();
            counterDAO().incrementCount(CASSANDRA_ID_1).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(testee().recomputeMailboxCounters(new Context(), options()).block())
                .isEqualTo(Result.COMPLETED);
        }

        @Test
        default void recomputeMailboxCountersShouldNoopWhenMailboxWithoutMessage() {
            mailboxDAO().save(MAILBOX).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(counterDAO().retrieveMailboxCounters(CASSANDRA_ID_1).blockOptional())
                .isEmpty();
        }

        @Test
        default void recomputeMailboxCountersShouldNoopWhenValidCounters() {
            mailboxDAO().save(MAILBOX).block();
            imapUidToMessageIdDAO().insert(METADATA_UNSEEN).block();
            messageIdToImapUidDAO().insert(METADATA_UNSEEN).block();
            counterDAO().incrementUnseenAndCount(CASSANDRA_ID_1).block();
            counterDAO().incrementCount(CASSANDRA_ID_1).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(counterDAO().retrieveMailboxCounters(CASSANDRA_ID_1).blockOptional())
                .contains(MailboxCounters.builder()
                    .mailboxId(CASSANDRA_ID_1)
                    .count(1)
                    .unseen(1)
                    .build());
        }

        @Test
        default void recomputeMailboxCountersShouldRecreateMissingCounters() {
            mailboxDAO().save(MAILBOX).block();
            imapUidToMessageIdDAO().insert(METADATA_UNSEEN).block();
            messageIdToImapUidDAO().insert(METADATA_UNSEEN).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(counterDAO().retrieveMailboxCounters(CASSANDRA_ID_1).blockOptional())
                .contains(MailboxCounters.builder()
                    .mailboxId(CASSANDRA_ID_1)
                    .count(1)
                    .unseen(1)
                    .build());
        }

        @Test
        default void recomputeMailboxCountersShouldResetIncorrectCounters() {
            mailboxDAO().save(MAILBOX).block();
            imapUidToMessageIdDAO().insert(METADATA_UNSEEN).block();
            messageIdToImapUidDAO().insert(METADATA_UNSEEN).block();
            counterDAO().incrementCount(CASSANDRA_ID_1).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(counterDAO().retrieveMailboxCounters(CASSANDRA_ID_1).blockOptional())
                .contains(MailboxCounters.builder()
                    .mailboxId(CASSANDRA_ID_1)
                    .count(1)
                    .unseen(1)
                    .build());
        }

        @Test
        default void recomputeMailboxCountersShouldTakeSeenIntoAccount() {
            mailboxDAO().save(MAILBOX).block();
            imapUidToMessageIdDAO().insert(METADATA_SEEN).block();
            messageIdToImapUidDAO().insert(METADATA_SEEN).block();
            counterDAO().incrementCount(CASSANDRA_ID_1).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(counterDAO().retrieveMailboxCounters(CASSANDRA_ID_1).blockOptional())
                .contains(MailboxCounters.builder()
                    .mailboxId(CASSANDRA_ID_1)
                    .count(1)
                    .unseen(0)
                    .build());
        }

        @Test
        default void recomputeMailboxCountersShouldUseSourceOfTruthForComputation() {
            mailboxDAO().save(MAILBOX).block();
            imapUidToMessageIdDAO().insert(METADATA_SEEN).block();
            messageIdToImapUidDAO().insert(METADATA_UNSEEN).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(counterDAO().retrieveMailboxCounters(CASSANDRA_ID_1).blockOptional())
                .contains(MailboxCounters.builder()
                    .mailboxId(CASSANDRA_ID_1)
                    .count(1)
                    .unseen(1)
                    .build());
        }

        @Test
        default void recomputeMailboxCountersShouldIgnoreMissingMailboxListReferences() {
            mailboxDAO().save(MAILBOX).block();
            imapUidToMessageIdDAO().insert(METADATA_SEEN).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(counterDAO().retrieveMailboxCounters(CASSANDRA_ID_1).blockOptional())
                .isEmpty();
        }

        @Test
        default void recomputeMailboxCountersShouldIgnoreOrphanMailboxListReference() {
            mailboxDAO().save(MAILBOX).block();
            imapUidToMessageIdDAO().insert(METADATA_UNSEEN).block();

            testee().recomputeMailboxCounters(new Context(), options()).block();

            assertThat(counterDAO().retrieveMailboxCounters(CASSANDRA_ID_1).blockOptional())
                .isEmpty();
        }
    }
}