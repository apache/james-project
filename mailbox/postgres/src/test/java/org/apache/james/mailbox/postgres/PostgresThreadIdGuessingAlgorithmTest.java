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

package org.apache.james.mailbox.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.store.CombinationManagerTestSystem;
import org.apache.james.mailbox.store.ThreadIdGuessingAlgorithmContract;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.google.common.collect.ImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;

import reactor.core.publisher.Flux;

public class PostgresThreadIdGuessingAlgorithmTest extends ThreadIdGuessingAlgorithmContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    private PostgresMailboxManager mailboxManager;

    @Override
    protected CombinationManagerTestSystem createTestingData() {
        eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        PostgresCombinationManagerTestSystem testSystem = (PostgresCombinationManagerTestSystem) PostgresCombinationManagerTestSystem.createTestingData(postgresExtension, new NoQuotaManager(), eventBus);
        mailboxManager = (PostgresMailboxManager) testSystem.getMailboxManager();
        messageIdFactory = new PostgresMessageId.Factory();
        return testSystem;
    }

    @Override
    protected ThreadIdGuessingAlgorithm initThreadIdGuessingAlgorithm(CombinationManagerTestSystem testingData) {
        return new PostgresThreadIdGuessingAlgorithm(new PostgresMailboxMessageDAO.Factory(postgresExtension.getExecutorFactory()), mailboxManager);
    }

    @Override
    protected MessageMapper createMessageMapper(MailboxSession mailboxSession) {
        return mailboxManager.getMapperFactory().createMessageMapper(mailboxSession);
    }

    @Override
    protected MessageId initNewBasedMessageId() {
        return messageIdFactory.generate();
    }

    @Override
    protected MessageId initOtherBasedMessageId() {
        return messageIdFactory.generate();
    }

    @Override
    protected void saveThreadData(Username username, Set<MimeMessageId> mimeMessageIds, MessageId messageId, ThreadId threadId, Optional<Subject> baseSubject) {
        // TODO insert a message
    }

    @Test
    void givenAMailInAThreadThenGetThreadShouldReturnAListWithOnlyOneMessageIdInThatThread() throws MailboxException {
        Set<MimeMessageId> mimeMessageIds = buildMimeMessageIdSet(Optional.of(new MimeMessageId("Message-ID")),
            Optional.of(new MimeMessageId("someInReplyTo")),
            Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("references2"))));

        MessageId messageId = initNewBasedMessageId();
        ThreadId threadId = ThreadId.fromBaseMessageId(newBasedMessageId);
        saveThreadData(mailboxSession.getUser(), mimeMessageIds, messageId, threadId, Optional.of(new Subject("Test")));

        Flux<MessageId> messageIds = testee.getMessageIdsInThread(threadId, mailboxSession);

        assertThat(messageIds.collectList().block())
            .containsOnly(messageId);
    }

    @Test
    void givenTwoDistinctThreadsThenGetThreadShouldNotReturnUnrelatedMails() throws MailboxException {
        Set<MimeMessageId> mimeMessageIds = buildMimeMessageIdSet(Optional.of(new MimeMessageId("Message-ID")),
            Optional.of(new MimeMessageId("someInReplyTo")),
            Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("references2"))));

        MessageId messageId1 = initNewBasedMessageId();
        MessageId messageId2 = initNewBasedMessageId();
        MessageId messageId3 = initNewBasedMessageId();
        ThreadId threadId1 = ThreadId.fromBaseMessageId(newBasedMessageId);
        ThreadId threadId2 = ThreadId.fromBaseMessageId(otherBasedMessageId);

        saveThreadData(mailboxSession.getUser(), mimeMessageIds, messageId1, threadId1, Optional.of(new Subject("Test")));
        saveThreadData(mailboxSession.getUser(), mimeMessageIds, messageId2, threadId1, Optional.of(new Subject("Test")));
        saveThreadData(mailboxSession.getUser(), mimeMessageIds, messageId3, threadId2, Optional.of(new Subject("Test")));

        Flux<MessageId> messageIds = testee.getMessageIdsInThread(ThreadId.fromBaseMessageId(otherBasedMessageId), mailboxSession);

        assertThat(messageIds.collectList().block())
            .doesNotContain(messageId1, messageId2);
    }

    @Test
    void givenThreeMailsInAThreadThenGetThreadShouldReturnAListWithThreeMessageIdsSortedByArrivalDate() {
        Set<MimeMessageId> mimeMessageIds = ImmutableSet.of(new MimeMessageId("Message-ID"));

        MessageId messageId1 = initNewBasedMessageId();
        MessageId messageId2 = initNewBasedMessageId();
        MessageId messageId3 = initNewBasedMessageId();
        ThreadId threadId1 = ThreadId.fromBaseMessageId(newBasedMessageId);

        saveThreadData(mailboxSession.getUser(), mimeMessageIds, messageId1, threadId1, Optional.of(new Subject("Test1")));
        saveThreadData(mailboxSession.getUser(), mimeMessageIds, messageId2, threadId1, Optional.of(new Subject("Test2")));
        saveThreadData(mailboxSession.getUser(), mimeMessageIds, messageId3, threadId1, Optional.of(new Subject("Test3")));

        Flux<MessageId> messageIds = testee.getMessageIdsInThread(ThreadId.fromBaseMessageId(newBasedMessageId), mailboxSession);

        assertThat(messageIds.collectList().block())
            .isEqualTo(ImmutableList.of(messageId1, messageId2, messageId3));
    }

    private Set<Integer> hashMimeMessagesIds(Set<MimeMessageId> mimeMessageIds) {
        return mimeMessageIds.stream()
            .map(mimeMessageId -> Hashing.murmur3_32_fixed().hashBytes(mimeMessageId.getValue().getBytes()).asInt())
            .collect(Collectors.toSet());
    }

    private Optional<Integer> hashSubject(Optional<Subject> baseSubjectOptional) {
        return baseSubjectOptional.map(baseSubject -> Hashing.murmur3_32_fixed().hashBytes(baseSubject.getValue().getBytes()).asInt());
    }
}
