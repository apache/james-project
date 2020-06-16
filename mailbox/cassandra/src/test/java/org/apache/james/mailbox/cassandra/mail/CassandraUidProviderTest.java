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
package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.stream.LongStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;

class CassandraUidProviderTest {
    private static final CassandraId CASSANDRA_ID = new CassandraId.Factory().fromString("e22b3ac0-a80b-11e7-bb00-777268d65503");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraUidModule.MODULE);

    private CassandraUidProvider uidProvider;
    private Mailbox mailbox;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        uidProvider = new CassandraUidProvider(
            cassandra.getConf(),
            CassandraConfiguration.DEFAULT_CONFIGURATION,
            cassandraCluster.getCassandraConsistenciesConfiguration());
        MailboxPath path = new MailboxPath("gsoc", Username.of("ieugen"), "Trash");
        mailbox = new Mailbox(path, UidValidity.of(1234), CASSANDRA_ID);
    }

    @Test
    void lastUidShouldRetrieveValueStoredByNextUid() throws Exception {
        int nbEntries = 100;
        Optional<MessageUid> result = uidProvider.lastUid(mailbox);
        assertThat(result).isEmpty();
        LongStream.range(0, nbEntries)
            .forEach(Throwing.longConsumer(value -> {
                        MessageUid uid = uidProvider.nextUid(mailbox);
                        assertThat(uid).isEqualTo(uidProvider.lastUid(mailbox).get());
                })
            );
    }

    @Test
    void nextUidShouldIncrementValueByOne() {
        int nbEntries = 100;
        LongStream.range(1, nbEntries)
            .forEach(Throwing.longConsumer(value -> {
                MessageUid result = uidProvider.nextUid(mailbox);
                assertThat(value).isEqualTo(result.asLong());
            }));
    }

    @Test
    void nextUidShouldGenerateUniqueValuesWhenParallelCalls() throws ExecutionException, InterruptedException {
        int threadCount = 10;
        int nbEntries = 100;

        ConcurrentSkipListSet<MessageUid> messageUids = new ConcurrentSkipListSet<>();
        ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> messageUids.add(uidProvider.nextUid(mailbox)))
            .threadCount(threadCount)
            .operationCount(nbEntries / threadCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(messageUids).hasSize(nbEntries);
    }
}
