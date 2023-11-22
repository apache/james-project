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

package org.apache.james.mailbox.postgres.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.stream.LongStream;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;

public class PostgresUidProviderTest {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxModule.MODULE);

    private UidProvider uidProvider;

    private Mailbox mailbox;

    @BeforeEach
    void setup() {
        PostgresMailboxDAO mailboxDAO = new PostgresMailboxDAO(postgresExtension.getPostgresExecutor());
        uidProvider = new PostgresUidProvider(mailboxDAO);
        MailboxPath mailboxPath = new MailboxPath("gsoc", Username.of("ieugen" + UUID.randomUUID()), "INBOX");
        UidValidity uidValidity = UidValidity.of(1234);
        mailbox = mailboxDAO.create(mailboxPath, uidValidity).block();
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
    void nextUidShouldGenerateUniqueValuesWhenParallelCalls() throws ExecutionException, InterruptedException, MailboxException {
        uidProvider.nextUid(mailbox);
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

    @Test
    void nextUidsShouldGenerateUniqueValuesWhenParallelCalls() throws ExecutionException, InterruptedException, MailboxException {
        uidProvider.nextUid(mailbox);

        int threadCount = 10;
        int nbOperations = 100;

        ConcurrentSkipListSet<MessageUid> messageUids = new ConcurrentSkipListSet<>();
        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> messageUids.addAll(uidProvider.nextUids(mailbox.getMailboxId(), 10).block()))
            .threadCount(threadCount)
            .operationCount(nbOperations / threadCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(messageUids).hasSize(nbOperations * 10);
    }

    @Test
    void nextUidWithCountShouldReturnCorrectUids() {
        int count = 10;
        List<MessageUid> messageUids = uidProvider.nextUids(mailbox.getMailboxId(), count).block();
        assertThat(messageUids).hasSize(count)
            .containsExactlyInAnyOrder(
                MessageUid.of(1),
                MessageUid.of(2),
                MessageUid.of(3),
                MessageUid.of(4),
                MessageUid.of(5),
                MessageUid.of(6),
                MessageUid.of(7),
                MessageUid.of(8),
                MessageUid.of(9),
                MessageUid.of(10));
    }

}
