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
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraThreadDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraThreadLookupDAO;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.cassandra.mail.ThreadTablePartitionKey;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.CombinationManagerTestSystem;
import org.apache.james.mailbox.store.ThreadIdGuessingAlgorithmContract;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraThreadIdGuessingAlgorithmTest extends ThreadIdGuessingAlgorithmContract {
    private CassandraThreadLookupDAO threadLookupDAO;
    private MessageId.Factory messageIdFactory;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MailboxAggregateModule.MODULE);

    @Override
    protected CombinationManagerTestSystem createTestingSystem() {
        EventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        CassandraCombinationManagerTestSystem testSystem = (CassandraCombinationManagerTestSystem) CassandraCombinationManagerTestSystem.createTestingData(cassandraCluster.getCassandraCluster(), new NoQuotaManager(), eventBus);
        messageIdFactory = new CassandraMessageId.Factory();
        return testSystem;
    }

    @Override
    protected ThreadIdGuessingAlgorithm initThreadIdGuessingAlgorithm(CombinationManagerTestSystem testingData) {
        CassandraThreadDAO threadDAO = new CassandraThreadDAO(cassandraCluster.getCassandraCluster().getConf());
        threadLookupDAO = new CassandraThreadLookupDAO(cassandraCluster.getCassandraCluster().getConf());

        return new CassandraThreadIdGuessingAlgorithm(testingData.getMessageIdManager(), threadDAO, threadLookupDAO);
    }

    @Override
    protected MessageId initNewBasedMessageId() {
        return messageIdFactory.generate();
    }

    @Override
    protected MessageId initOtherBasedMessageId() {
        return messageIdFactory.generate();
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

        assertThat(threadLookupDAO.selectOneRow(ThreadId.fromBaseMessageId(newBasedMessageId), newBasedMessageId).block())
            .isEqualTo(new ThreadTablePartitionKey(username, hashMimeMessagesIds(mimeMessageIds)));
    }
}
