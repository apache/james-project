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

package org.apache.james.jmap.postgres.projections;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.projections.EmailQueryViewManager;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.util.streams.Limit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresEmailQueryViewManagerRLSTest {
    public static final PostgresMailboxId MAILBOX_ID_1 = PostgresMailboxId.generate();
    public static final PostgresMessageId.Factory MESSAGE_ID_FACTORY = new PostgresMessageId.Factory();
    public static final PostgresMessageId MESSAGE_ID_1 = MESSAGE_ID_FACTORY.generate();
    ZonedDateTime DATE_1 = ZonedDateTime.parse("2010-10-30T15:12:00Z");
    ZonedDateTime DATE_2 = ZonedDateTime.parse("2010-10-30T16:12:00Z");

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresEmailQueryViewModule.MODULE);

    private EmailQueryViewManager emailQueryViewManager;

    @BeforeEach
    public void setUp() {
        emailQueryViewManager = new PostgresEmailQueryViewManager(postgresExtension.getExecutorFactory());
    }

    @Test
    void emailQueryViewCanBeAccessedAtTheDataLevelByMembersOfTheSameDomain() {
        Username username = Username.of("alice@domain1");

        emailQueryViewManager.getEmailQueryView(username).save(MAILBOX_ID_1, DATE_1, DATE_2, MESSAGE_ID_1).block();

        assertThat(emailQueryViewManager.getEmailQueryView(username).listMailboxContentSortedByReceivedAt(MAILBOX_ID_1, Limit.limit(1)).collectList().block())
            .isNotEmpty();
    }

    @Test
    void emailQueryViewShouldBeIsolatedByDomain() {
        Username username = Username.of("alice@domain1");
        Username username2 = Username.of("bob@domain2");

        emailQueryViewManager.getEmailQueryView(username).save(MAILBOX_ID_1, DATE_1, DATE_2, MESSAGE_ID_1).block();

        assertThat(emailQueryViewManager.getEmailQueryView(username2).listMailboxContentSortedByReceivedAt(MAILBOX_ID_1, Limit.limit(1)).collectList().block())
            .isEmpty();
    }
}
