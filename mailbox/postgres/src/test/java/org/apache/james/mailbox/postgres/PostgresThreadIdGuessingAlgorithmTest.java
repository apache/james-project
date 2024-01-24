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
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresThreadDAO;
import org.apache.james.mailbox.store.CombinationManagerTestSystem;
import org.apache.james.mailbox.store.ThreadIdGuessingAlgorithmContract;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.hash.Hashing;

public class PostgresThreadIdGuessingAlgorithmTest extends ThreadIdGuessingAlgorithmContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    private PostgresMailboxManager mailboxManager;
    private PostgresMailboxFactory mailboxFactory;

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
        mailboxFactory = new PostgresMailboxFactory(postgresExtension.getExecutorFactory());
        return new PostgresThreadIdGuessingAlgorithm(mailboxManager, mailboxFactory);
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
        PostgresThreadDAO threadDAO = mailboxFactory.createThreadDAO(username.getDomainPart());
        threadDAO.insertSome(username, hashMimeMessagesIds(mimeMessageIds), messageId, threadId, hashSubject(baseSubject)).block();
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
