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

package org.apache.james.jmap.cassandra.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.jmap.api.change.MailboxChange;
import org.apache.james.jmap.api.change.MailboxChangeRepository;
import org.apache.james.jmap.api.change.MailboxChangeRepositoryContract;
import org.apache.james.jmap.api.change.State;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.MailboxId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

public class CassandraMailboxChangeRepositoryTest implements MailboxChangeRepositoryContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraMailboxChangeModule.MODULE,
            CassandraSchemaVersionModule.MODULE,
            CassandraZonedDateTimeModule.MODULE));

    MailboxChangeRepository mailboxChangeRepository;
    MailboxChangeRepositoryDAO mailboxChangeRepositoryDAO;

    @BeforeEach
    public void setUp(CassandraCluster cassandra) {
        mailboxChangeRepositoryDAO = new MailboxChangeRepositoryDAO(cassandra.getConf(), cassandra.getTypesProvider(),
            CassandraChangesConfiguration.DEFAULT);
        mailboxChangeRepository = new CassandraMailboxChangeRepository(mailboxChangeRepositoryDAO, DEFAULT_NUMBER_OF_CHANGES);
    }

    @Override
    public State.Factory stateFactory() {
        return new CassandraStateFactory();
    }

    @Override
    public MailboxChangeRepository mailboxChangeRepository() {
        return mailboxChangeRepository;
    }

    @Override
    public MailboxId generateNewMailboxId() {
        return CassandraId.timeBased();
    }

    @Test
    void mailboxChangeRecordsShouldBeDeletedAfterTTL(CassandraCluster cassandra) {
        mailboxChangeRepositoryDAO = new MailboxChangeRepositoryDAO(cassandra.getConf(), cassandra.getTypesProvider(),
            new CassandraChangesConfiguration.Builder()
            .mailboxChangeTtl(Duration.ofSeconds(1))
            .build());

        MailboxChange mailboxChange = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory().generate())
            .date(DATE)
            .isCountChange(false)
            .created(ImmutableList.of(generateNewMailboxId()))
            .build();

        assertThatCode(() -> mailboxChangeRepositoryDAO.insert(mailboxChange).block())
            .doesNotThrowAnyException();

        await().atMost(Duration.ofSeconds(3))
            .await()
            .untilAsserted(() -> assertThat(mailboxChangeRepositoryDAO.getAllChanges(ACCOUNT_ID).collectList().block())
                .isEmpty());
    }
}
