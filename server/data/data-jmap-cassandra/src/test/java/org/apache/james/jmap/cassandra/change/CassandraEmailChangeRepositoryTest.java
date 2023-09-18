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
import org.apache.james.jmap.api.change.EmailChange;
import org.apache.james.jmap.api.change.EmailChangeRepository;
import org.apache.james.jmap.api.change.EmailChangeRepositoryContract;
import org.apache.james.jmap.api.change.State;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraEmailChangeRepositoryTest implements EmailChangeRepositoryContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraEmailChangeModule.MODULE,
            CassandraSchemaVersionModule.MODULE,
            CassandraZonedDateTimeModule.MODULE));

    EmailChangeRepository emailChangeRepository;
    EmailChangeRepositoryDAO emailChangeRepositoryDAO;

    @BeforeEach
    public void setUp(CassandraCluster cassandra) {
        emailChangeRepositoryDAO = new EmailChangeRepositoryDAO(cassandra.getConf(), cassandra.getTypesProvider(),
            CassandraChangesConfiguration.DEFAULT);
        emailChangeRepository = new CassandraEmailChangeRepository(emailChangeRepositoryDAO, DEFAULT_NUMBER_OF_CHANGES);
    }

    @Override
    public EmailChangeRepository emailChangeRepository() {
        return emailChangeRepository;
    }

    @Override
    public State generateNewState() {
        return new CassandraStateFactory().generate();
    }

    @Override
    public MessageId generateNewMessageId() {
        return new CassandraMessageId.Factory().generate();
    }

    @Test
    void emailChangeRecordsShouldBeDeletedAfterTTL(CassandraCluster cassandra) {
        emailChangeRepositoryDAO = new EmailChangeRepositoryDAO(cassandra.getConf(), cassandra.getTypesProvider(),
            new CassandraChangesConfiguration.Builder()
                .emailChangeTtl(Duration.ofSeconds(1))
                .build());

        EmailChange emailChange = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(generateNewMessageId())
            .build();

        assertThatCode(() -> emailChangeRepositoryDAO.insert(emailChange).block())
            .doesNotThrowAnyException();

        await().atMost(Duration.ofSeconds(3))
            .await()
            .untilAsserted(() -> assertThat(emailChangeRepositoryDAO.getAllChanges(ACCOUNT_ID).collectList().block())
                .isEmpty());
    }

    @Test
    void emailChangeRecordsShouldNotBeDeletedWhenTtlIsZero(CassandraCluster cassandra) throws InterruptedException {
        emailChangeRepositoryDAO = new EmailChangeRepositoryDAO(cassandra.getConf(), cassandra.getTypesProvider(),
            new CassandraChangesConfiguration.Builder()
                .emailChangeTtl(Duration.ofSeconds(0))
                .build());

        EmailChange emailChange = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(generateNewMessageId())
            .build();

        assertThatCode(() -> emailChangeRepositoryDAO.insert(emailChange).block())
            .doesNotThrowAnyException();

        Thread.sleep(200L);

        assertThat(emailChangeRepositoryDAO.getAllChanges(ACCOUNT_ID).collectList().block())
            .isNotEmpty();
    }
}
