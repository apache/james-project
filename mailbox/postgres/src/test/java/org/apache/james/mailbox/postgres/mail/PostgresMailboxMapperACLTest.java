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
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMapperACLTest;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableMap;

class PostgresMailboxMapperACLTest extends MailboxMapperACLTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxModule.MODULE);

    private PostgresMailboxMapper mailboxMapper;

    @Override
    protected MailboxMapper createMailboxMapper() {
        mailboxMapper = new PostgresMailboxMapper(new PostgresMailboxDAO(postgresExtension.getDefaultPostgresExecutor()));
        return mailboxMapper;
    }

    @Test
    protected void updateAclShouldWorkWellInMultiThreadEnv() throws ExecutionException, InterruptedException {
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Administer, MailboxACL.Right.Write);
        MailboxACL.Rfc4314Rights newRights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Write);

        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> {
                int userNumber = threadNumber / 2;
                MailboxACL.EntryKey key = MailboxACL.EntryKey.createUserEntryKey("user" + userNumber);
                if (threadNumber % 2 == 0) {
                    return mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement())
                        .then();
                } else {
                    return mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(newRights).asAddition())
                        .then();
                }
            })
            .threadCount(10)
            .operationCount(1)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        MailboxACL expectedMailboxACL = new MailboxACL(IntStream.range(0, 5).boxed()
            .collect(ImmutableMap.toImmutableMap(userNumber -> MailboxACL.EntryKey.createUserEntryKey("user" + userNumber), userNumber -> rights)));

        assertThat(
            mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())
                .block()
                .getACL())
            .isEqualTo(expectedMailboxACL);
    }
}
