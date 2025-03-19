/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.blob.cassandra.CassandraBlobDataDefinition;
import org.apache.james.core.Username;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreDataDefinition;
import org.apache.james.mailbox.cassandra.mail.utils.GuiceUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraAclDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxDataDefinition;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailboxMapperConcurrencyTest {

    private static final UidValidity UID_VALIDITY = UidValidity.of(52);
    private static final MailboxPath MAILBOX_PATH = MailboxPath.forUser(Username.of("user"), "name");
    private static final int THREAD_COUNT = 10;
    private static final int OPERATION_COUNT = 10;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraDataDefinition.aggregateModules(
            CassandraBlobDataDefinition.MODULE,
            CassandraSchemaVersionDataDefinition.MODULE,
            CassandraMailboxDataDefinition.MODULE,
            CassandraAclDataDefinition.MODULE,
            CassandraEventStoreDataDefinition.MODULE()));

    private CassandraMailboxMapper testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = GuiceUtils.testInjector(cassandra)
            .getInstance(CassandraMailboxMapper.class);
    }

    @Test
    void createShouldBeThreadSafe() throws Exception {
        ConcurrentTestRunner.builder()
            .operation((a, b) -> testee.create(MAILBOX_PATH, UID_VALIDITY).block())
            .threadCount(THREAD_COUNT)
            .operationCount(OPERATION_COUNT)
            .runAcceptingErrorsWithin(Duration.ofMinutes(1));

        assertThat(testee.list().collectList().block()).hasSize(1);
    }

    @Test
    void renameWithUpdateShouldBeThreadSafe() throws Exception {
        Mailbox mailbox = testee.create(MAILBOX_PATH, UID_VALIDITY).block();

        mailbox.setName("newName");

        ConcurrentTestRunner.builder()
            .operation((a, b) -> testee.rename(mailbox).block())
            .threadCount(THREAD_COUNT)
            .operationCount(OPERATION_COUNT)
            .runAcceptingErrorsWithin(Duration.ofMinutes(1));

        List<Mailbox> list = testee.list().collectList().block();
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).isEqualToComparingFieldByField(mailbox);
    }
}
