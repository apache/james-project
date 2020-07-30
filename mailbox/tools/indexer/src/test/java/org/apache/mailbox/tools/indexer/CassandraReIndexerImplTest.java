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

package org.apache.mailbox.tools.indexer;

import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.MessageManager.AppendResult;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxManagerProvider;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures.ReIndexingFailure;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.task.Task;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.mailbox.tools.indexer.ReprocessingContextInformationDTO.ReprocessingContextInformationForErrorRecoveryIndexationTask;
import org.apache.mailbox.tools.indexer.ReprocessingContextInformationDTO.ReprocessingContextInformationForFullReindexingTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class CassandraReIndexerImplTest {
    private static final Username USERNAME = Username.of("benwa@apache.org");
    public static final MailboxPath INBOX = MailboxPath.inbox(USERNAME);
    private CassandraMailboxManager mailboxManager;
    private ListeningMessageSearchIndex messageSearchIndex;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MailboxAggregateModule.MODULE_WITH_QUOTA);

    private ReIndexer reIndexer;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        mailboxManager = CassandraMailboxManagerProvider.provideMailboxManager(cassandra, PreDeletionHooks.NO_PRE_DELETION_HOOK);
        MailboxSessionMapperFactory mailboxSessionMapperFactory = mailboxManager.getMapperFactory();
        messageSearchIndex = mock(ListeningMessageSearchIndex.class);
        when(messageSearchIndex.add(any(), any(), any())).thenReturn(Mono.empty());
        when(messageSearchIndex.deleteAll(any(), any())).thenReturn(Mono.empty());
        reIndexer = new ReIndexerImpl(new ReIndexerPerformer(mailboxManager, messageSearchIndex, mailboxSessionMapperFactory),
            mailboxManager, mailboxSessionMapperFactory);
    }

    @Test
    void reIndexShouldBeWellPerformed() throws Exception {
        // Given a mailbox with 1000 messages * 150 KB
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(INBOX, systemSession);

        byte[] bigBody = (Strings.repeat("header: value\r\n", 10000) + "\r\nbody").getBytes(StandardCharsets.UTF_8);

        int threadCount = 10;
        int operationCount = 100;
        MessageManager mailbox = mailboxManager.getMailbox(INBOX, systemSession);
        ConcurrentTestRunner.builder()
            .operation((a, b) -> mailbox
                .appendMessage(
                    AppendCommand.builder().build(bigBody),
                    systemSession))
            .threadCount(threadCount)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofMinutes(10));

        // When We re-index
        reIndexer.reIndex(INBOX, ReIndexer.RunningOptions.DEFAULT).run();

        // The indexer is called for each message
        verify(messageSearchIndex).deleteAll(any(MailboxSession.class), any(MailboxId.class));
        verify(messageSearchIndex, times(threadCount * operationCount))
            .add(any(MailboxSession.class), any(Mailbox.class),any(MailboxMessage.class));
        verifyNoMoreInteractions(messageSearchIndex);
    }

    @Nested
    class FailureTesting {
        @Test
        void fullReindexingShouldReturnPartialUponFailure(CassandraCluster cassandra) throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USERNAME);
            mailboxManager.createMailbox(INBOX, session);

            MessageManager mailbox = mailboxManager.getMailbox(INBOX, session);
            mailbox.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), session);

            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("SELECT messageId,mailboxId,uid,modSeq,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags FROM messageIdTable WHERE mailboxId=:mailboxId;"));

            Task task = reIndexer.reIndex(ReIndexer.RunningOptions.DEFAULT);
            Task.Result result = task.run();

            assertThat(result).isEqualTo(Task.Result.PARTIAL);
        }

        @Test
        void fullReindexingShouldUpdateDetailsUponFailure(CassandraCluster cassandra) throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USERNAME);
            mailboxManager.createMailbox(INBOX, session);

            MessageManager mailbox = mailboxManager.getMailbox(INBOX, session);
            mailbox.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), session);

            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("SELECT messageId,mailboxId,uid,modSeq,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags FROM messageIdTable WHERE mailboxId=:mailboxId;"));

            Task task = reIndexer.reIndex(ReIndexer.RunningOptions.DEFAULT);
            task.run();

            ReprocessingContextInformationForFullReindexingTask information = (ReprocessingContextInformationForFullReindexingTask) task.details().get();
            assertThat(information.failures().mailboxFailures()).containsExactly(mailbox.getId());
        }

        @Test
        void singleMailboxReindexingShouldReturnPartialUponFailure(CassandraCluster cassandra) throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USERNAME);
            mailboxManager.createMailbox(INBOX, session);

            MessageManager mailbox = mailboxManager.getMailbox(INBOX, session);
            mailbox.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), session);

            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("SELECT messageId,mailboxId,uid,modSeq,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags FROM messageIdTable WHERE mailboxId=:mailboxId;"));

            Task task = reIndexer.reIndex(mailbox.getId(), ReIndexer.RunningOptions.DEFAULT);
            Task.Result result = task.run();

            assertThat(result).isEqualTo(Task.Result.PARTIAL);
        }

        @Test
        void singleMailboxReindexingShouldUpdateDetailsUponFailure(CassandraCluster cassandra) throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USERNAME);
            mailboxManager.createMailbox(INBOX, session);

            MessageManager mailbox = mailboxManager.getMailbox(INBOX, session);
            mailbox.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), session);

            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("SELECT messageId,mailboxId,uid,modSeq,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags FROM messageIdTable WHERE mailboxId=:mailboxId;"));

            Task task = reIndexer.reIndex(mailbox.getId(), ReIndexer.RunningOptions.DEFAULT);
            task.run();

            SingleMailboxReindexingTask.AdditionalInformation information = (SingleMailboxReindexingTask.AdditionalInformation) task.details().get();
            assertThat(information.failures().mailboxFailures()).containsExactly(mailbox.getId());
        }

        @Test
        void userMailboxReindexingShouldReturnPartialUponFailure(CassandraCluster cassandra) throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USERNAME);
            mailboxManager.createMailbox(INBOX, session);

            MessageManager mailbox = mailboxManager.getMailbox(INBOX, session);
            mailbox.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), session);

            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("SELECT messageId,mailboxId,uid,modSeq,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags FROM messageIdTable WHERE mailboxId=:mailboxId;"));

            Task task = reIndexer.reIndex(USERNAME, ReIndexer.RunningOptions.DEFAULT);
            Task.Result result = task.run();

            assertThat(result).isEqualTo(Task.Result.PARTIAL);
        }

        @Test
        void userMailboxReindexingShouldUpdateDetailsUponFailure(CassandraCluster cassandra) throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USERNAME);
            mailboxManager.createMailbox(INBOX, session);

            MessageManager mailbox = mailboxManager.getMailbox(INBOX, session);
            mailbox.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), session);

            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("SELECT messageId,mailboxId,uid,modSeq,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags FROM messageIdTable WHERE mailboxId=:mailboxId;"));

            Task task = reIndexer.reIndex(USERNAME, ReIndexer.RunningOptions.DEFAULT);
            task.run();

            UserReindexingTask.AdditionalInformation information = (UserReindexingTask.AdditionalInformation) task.details().get();
            assertThat(information.failures().mailboxFailures()).containsExactly(mailbox.getId());
        }

        @Test
        void errorReindexingShouldReturnPartialUponFailure(CassandraCluster cassandra) throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USERNAME);
            mailboxManager.createMailbox(INBOX, session);

            MessageManager mailbox = mailboxManager.getMailbox(INBOX, session);
            AppendResult appendResult = mailbox.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), session);

            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("SELECT messageId,mailboxId,uid,modSeq,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags FROM messageIdTable WHERE mailboxId=:mailboxId;"));

            Task task = reIndexer.reIndex(new ReIndexingExecutionFailures(
                ImmutableList.of(new ReIndexingFailure(mailbox.getId(),
                    appendResult.getId().getUid())),
                ImmutableList.of(mailbox.getId())),
                ReIndexer.RunningOptions.DEFAULT);
            Task.Result result = task.run();

            assertThat(result).isEqualTo(Task.Result.PARTIAL);
        }

        @Test
        void errorReindexingShouldUpdateDetailsUponFailure(CassandraCluster cassandra) throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USERNAME);
            mailboxManager.createMailbox(INBOX, session);

            MessageManager mailbox = mailboxManager.getMailbox(INBOX, session);
            AppendResult appendResult = mailbox.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), session);

            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("SELECT messageId,mailboxId,uid,modSeq,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags FROM messageIdTable WHERE mailboxId=:mailboxId;"));

            Task task = reIndexer.reIndex(new ReIndexingExecutionFailures(
                ImmutableList.of(new ReIndexingFailure(mailbox.getId(),
                    appendResult.getId().getUid())),
                    ImmutableList.of(mailbox.getId())),
                ReIndexer.RunningOptions.DEFAULT);
            task.run();

            ReprocessingContextInformationForErrorRecoveryIndexationTask information = (ReprocessingContextInformationForErrorRecoveryIndexationTask) task.details().get();
            assertThat(information.failures().mailboxFailures()).containsExactly(mailbox.getId());
        }

        @Test
        void errorReindexingShouldUpdateDetailsUponReadingMailboxError(CassandraCluster cassandra) throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USERNAME);
            mailboxManager.createMailbox(INBOX, session);

            MessageManager mailbox = mailboxManager.getMailbox(INBOX, session);
            AppendResult appendResult = mailbox.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), session);

            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("SELECT id,mailboxbase,uidvalidity,name FROM mailbox WHERE id=:id;"));

            Task task = reIndexer.reIndex(new ReIndexingExecutionFailures(
                    ImmutableList.of(new ReIndexingFailure(mailbox.getId(),
                        appendResult.getId().getUid())),
                    ImmutableList.of()),
                ReIndexer.RunningOptions.DEFAULT);
            task.run();

            ReprocessingContextInformationForErrorRecoveryIndexationTask information = (ReprocessingContextInformationForErrorRecoveryIndexationTask) task.details().get();
            assertThat(information.failures().messageFailures()).containsExactly(new ReIndexingFailure(mailbox.getId(), appendResult.getId().getUid()));
        }

        @Test
        void fullReindexingShouldUpdateDetailsUponSingleMessageFullReadError(CassandraCluster cassandra) throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(USERNAME);
            mailboxManager.createMailbox(INBOX, session);

            MessageManager mailbox = mailboxManager.getMailbox(INBOX, session);
            AppendResult appendResult = mailbox.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), session);

            cassandra.getConf()
                .registerScenario(fail()
                    .forever()
                    .whenQueryStartsWith("SELECT * FROM messageV2 WHERE messageId=:messageId;"));

            Task task = reIndexer.reIndex(ReIndexer.RunningOptions.DEFAULT);
            task.run();

            ReprocessingContextInformationForFullReindexingTask information = (ReprocessingContextInformationForFullReindexingTask) task.details().get();
            assertThat(information.failures().messageFailures()).containsExactly(new ReIndexingFailure(mailbox.getId(), appendResult.getId().getUid()));
        }
    }

    @Test
    void errorReindexingShouldReindexPreviouslyFailedMailbox() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(INBOX, session);

        MessageManager mailbox = mailboxManager.getMailbox(INBOX, session);
        mailbox.appendMessage(AppendCommand.builder().build("header: value\r\n\r\nbody"), session);

        Task task = reIndexer.reIndex(new ReIndexingExecutionFailures(
                ImmutableList.of(),
                ImmutableList.of(mailbox.getId())),
            ReIndexer.RunningOptions.DEFAULT);
        task.run();

        verify(messageSearchIndex).deleteAll(any(MailboxSession.class), any(MailboxId.class));
        verify(messageSearchIndex, times(1))
            .add(any(MailboxSession.class), any(Mailbox.class),any(MailboxMessage.class));
    }
}
