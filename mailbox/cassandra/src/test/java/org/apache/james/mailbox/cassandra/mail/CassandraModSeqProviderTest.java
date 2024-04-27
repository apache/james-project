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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.backends.cassandra.Scenario.Builder.awaitOn;
import static org.apache.james.backends.cassandra.Scenario.Builder.executeNormally;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageModseqTable.TABLE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.Scenario.Barrier;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.core.Username;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;

import reactor.core.scheduler.Schedulers;

class CassandraModSeqProviderTest {
    private static final CassandraId CASSANDRA_ID = new CassandraId.Factory().fromString("e22b3ac0-a80b-11e7-bb00-777268d65503");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModSeqModule.MODULE);
    
    private CassandraModSeqProvider modSeqProvider;
    private Mailbox mailbox;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        modSeqProvider = new CassandraModSeqProvider(
            cassandra.getConf(),
            CassandraConfiguration.DEFAULT_CONFIGURATION);
        MailboxPath path = new MailboxPath("gsoc", Username.of("ieugen"), "Trash");
        mailbox = new Mailbox(path, UidValidity.of(1234), CASSANDRA_ID);
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
    void failedInsertsShouldBeRetried(CassandraCluster cassandra) throws Exception {
        Barrier insertBarrier = new Barrier(2);
        Barrier retryBarrier = new Barrier(1);
        cassandra.getConf()
            .registerScenario(
                executeNormally()
                    .times(2)
                    .whenQueryStartsWith("SELECT nextmodseq FROM modseq WHERE mailboxid=:mailboxid"),
                awaitOn(insertBarrier)
                    .thenExecuteNormally()
                    .times(2)
                    .whenQueryStartsWith("INSERT INTO modseq (nextmodseq,mailboxid)"),
                awaitOn(retryBarrier)
                    .thenExecuteNormally()
                    .times(1)
                    .whenQueryStartsWith("SELECT nextmodseq FROM modseq WHERE mailboxid=:mailboxid"));

        CompletableFuture<ModSeq> operation1 = modSeqProvider.nextModSeqReactive(CASSANDRA_ID)
            .subscribeOn(Schedulers.boundedElastic())
            .toFuture();
        CompletableFuture<ModSeq> operation2 = modSeqProvider.nextModSeqReactive(CASSANDRA_ID)
            .subscribeOn(Schedulers.boundedElastic())
            .toFuture();

        insertBarrier.awaitCaller();
        insertBarrier.releaseCaller();

        retryBarrier.awaitCaller();
        retryBarrier.releaseCaller();

        // Artificially fail the insert failure
        cassandra.getConf()
            .execute(deleteFrom(TABLE_NAME)
                .where(column(MAILBOX_ID).isEqualTo(literal(CASSANDRA_ID.asUuid())))
                .build());

        retryBarrier.releaseCaller();

        assertThatCode(() -> operation1.get(1, TimeUnit.SECONDS)).doesNotThrowAnyException();
        assertThatCode(() -> operation2.get(1, TimeUnit.SECONDS)).doesNotThrowAnyException();
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

    @Test
    void shouldHandleOffset(CassandraCluster cassandra) throws Exception {
        modSeqProvider = new CassandraModSeqProvider(cassandra.getConf(),
            CassandraConfiguration.builder()
                .uidModseqIncrement(10)
                .build());

        ModSeq modseq0 = modSeqProvider.highestModSeq(mailbox);
        ModSeq modseq1 = modSeqProvider.nextModSeq(mailbox);
        ModSeq modseq2 = modSeqProvider.nextModSeq(mailbox.getMailboxId());
        ModSeq modseq3 = modSeqProvider.nextModSeqReactive(mailbox.getMailboxId()).block();
        ModSeq modseq4 = modSeqProvider.highestModSeq(mailbox);
        ModSeq modseq5 = modSeqProvider.highestModSeq(mailbox.getMailboxId());
        ModSeq modseq6 = modSeqProvider.highestModSeqReactive(mailbox).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(modseq0).isEqualTo(ModSeq.of(10));
            softly.assertThat(modseq1).isEqualTo(ModSeq.of(11));
            softly.assertThat(modseq2).isEqualTo(ModSeq.of(12));
            softly.assertThat(modseq3).isEqualTo(ModSeq.of(13));
            softly.assertThat(modseq4).isEqualTo(ModSeq.of(13));
            softly.assertThat(modseq5).isEqualTo(ModSeq.of(13));
            softly.assertThat(modseq6).isEqualTo(ModSeq.of(13));
        });
    }
}
