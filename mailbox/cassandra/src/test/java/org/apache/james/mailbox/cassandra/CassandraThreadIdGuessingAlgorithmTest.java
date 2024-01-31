/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.mailbox.cassandra;

import static org.apache.james.mailbox.cassandra.mail.CassandraThreadDAOTest.hashMimeMessagesIds;
import static org.apache.james.mailbox.cassandra.mail.CassandraThreadDAOTest.hashSubject;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraThreadDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraThreadLookupDAO;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.cassandra.mail.ThreadTablePartitionKey;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
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

import reactor.core.publisher.Flux;

public class CassandraThreadIdGuessingAlgorithmTest extends ThreadIdGuessingAlgorithmContract {
    private CassandraMailboxManager mailboxManager;
    private CassandraThreadDAO threadDAO;
    private CassandraThreadLookupDAO threadLookupDAO;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MailboxAggregateModule.MODULE);

    @Override
    protected CombinationManagerTestSystem createTestingData() {
        eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        CassandraCombinationManagerTestSystem testSystem = (CassandraCombinationManagerTestSystem) CassandraCombinationManagerTestSystem.createTestingData(cassandraCluster.getCassandraCluster(), new NoQuotaManager(), eventBus);
        mailboxManager = (CassandraMailboxManager) testSystem.getMailboxManager();
        messageIdFactory = new CassandraMessageId.Factory();
        return testSystem;
    }

    @Override
    protected ThreadIdGuessingAlgorithm initThreadIdGuessingAlgorithm(CombinationManagerTestSystem testingData) {
        threadDAO = new CassandraThreadDAO(cassandraCluster.getCassandraCluster().getConf());
        threadLookupDAO = new CassandraThreadLookupDAO(cassandraCluster.getCassandraCluster().getConf());
        return new CassandraThreadIdGuessingAlgorithm(mailboxManager, threadDAO, threadLookupDAO);
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
        threadDAO.insertSome(username, hashMimeMessagesIds(mimeMessageIds), messageId, threadId, hashSubject(baseSubject))
            .then()
            .block();
    }

    @Test
    void guessThreadIdShouldSaveDataToThreadLookupTable() {
        testee.guessThreadIdReactive(newBasedMessageId,
            Optional.of(new MimeMessageId("Message-ID1")),
            Optional.of(new MimeMessageId("someInReplyTo")),
            Optional.of(List.of(new MimeMessageId("someReferences"), new MimeMessageId("Message-ID1"))),
            Optional.of(new Subject("test")), mailboxSession).block();

        Username username = mailboxSession.getUser();
        Set<MimeMessageId> mimeMessageIds = buildMimeMessageIdSet(Optional.of(new MimeMessageId("Message-ID1")),
            Optional.of(new MimeMessageId("someInReplyTo")),
            Optional.of(List.of(new MimeMessageId("someReferences"), new MimeMessageId("Message-ID1"))));

        assertThat(threadLookupDAO.selectOneRow(newBasedMessageId).block())
            .isEqualTo(new ThreadTablePartitionKey(username, hashMimeMessagesIds(mimeMessageIds)));
    }
}
