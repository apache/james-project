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
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.stream.LongStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.core.Username;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;

class CassandraModSeqProviderTest {
    private static final CassandraId CASSANDRA_ID = new CassandraId.Factory().fromString("e22b3ac0-a80b-11e7-bb00-777268d65503");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModSeqModule.MODULE);
    
    private CassandraModSeqProvider modSeqProvider;
    private Mailbox mailbox;


    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        modSeqProvider = new CassandraModSeqProvider(cassandra.getConf(), CassandraConfiguration.DEFAULT_CONFIGURATION);
        MailboxPath path = new MailboxPath("gsoc", Username.of("ieugen"), "Trash");
        mailbox = new Mailbox(path, 1234, CASSANDRA_ID);
    }

    @Test
    void highestModSeqShouldRetrieveValueStoredNextModSeq() throws Exception {
        int nbEntries = 100;
        ModSeq result = modSeqProvider.highestModSeq(mailbox);
        assertThat(result).isEqualTo(ModSeq.first());
        LongStream.range(0, nbEntries)
            .forEach(Throwing.longConsumer(value -> {
                    ModSeq modSeq = modSeqProvider.nextModSeq(mailbox);
                    assertThat(modSeq).isEqualTo(modSeqProvider.highestModSeq(mailbox));
                })
            );
    }

    @Test
    void nextModSeqShouldIncrementValueByOne() throws Exception {
        int nbEntries = 100;
        ModSeq lastModSeq = modSeqProvider.highestModSeq(mailbox);
        LongStream.range(lastModSeq.asLong() + 1, lastModSeq.asLong() + nbEntries)
            .forEach(Throwing.longConsumer(value -> {
                ModSeq result = modSeqProvider.nextModSeq(mailbox);
                assertThat(result.asLong()).isEqualTo(value);
            }));
    }

    @Test
    void nextModSeqShouldGenerateUniqueValuesWhenParallelCalls() throws ExecutionException, InterruptedException {
        int nbEntries = 10;

        ConcurrentSkipListSet<ModSeq> modSeqs = new ConcurrentSkipListSet<>();
        ConcurrentTestRunner.builder()
            .operation(
                (threadNumber, step) -> modSeqs.add(modSeqProvider.nextModSeq(mailbox)))
            .threadCount(10)
            .operationCount(nbEntries)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(modSeqs).hasSize(100);
    }
}
