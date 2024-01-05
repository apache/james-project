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

package org.apache.james.mailbox.postgres;

import static org.apache.james.mailbox.postgres.PostgresMailboxManagerProvider.BLOB_ID_FACTORY;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.mailbox.postgres.mail.PostgresMailboxManager;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DeleteMessageListenerTest extends DeleteMessageListenerContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    private static PostgresMailboxManager mailboxManager;

    @BeforeAll
    static void beforeAll() {
        mailboxManager = PostgresMailboxManagerProvider.provideMailboxManager(postgresExtension);
    }

    @Override
    PostgresMailboxManager provideMailboxManager() {
        return mailboxManager;
    }

    @Override
    PostgresMessageDAO providePostgresMessageDAO() {
        return new PostgresMessageDAO(postgresExtension.getPostgresExecutor(), BLOB_ID_FACTORY);
    }

    @Override
    PostgresMailboxMessageDAO providePostgresMailboxMessageDAO() {
        return new PostgresMailboxMessageDAO(postgresExtension.getPostgresExecutor());
    }
}
