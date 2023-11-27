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

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.utils.DomainImplPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresMailboxMapperRowLevelSecurityTest {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresMailboxModule.MODULE);

    private MailboxMapperFactory mailboxMapperFactory;

    @BeforeEach
    public void setUp() {
        PostgresExecutor.Factory executorFactory = new PostgresExecutor.Factory(new DomainImplPostgresConnectionFactory(postgresExtension.getConnectionFactory()));
        mailboxMapperFactory = session -> new PostgresMailboxMapper(new PostgresMailboxDAO(executorFactory.create(session.getUser().getDomainPart())));
    }

    @Test
    void mailboxesCanBeAccessedAtTheDataLevelByMembersOfTheSameDomain() throws Exception {
        Username username = Username.of("alice@domain1");
        Username username2 = Username.of("bob@domain1");

        MailboxSession session = MailboxSessionUtil.create(username);
        MailboxSession session2 = MailboxSessionUtil.create(username2);

        mailboxMapperFactory.getMailboxMapper(session)
            .create(MailboxPath.forUser(username, "INBOX"), UidValidity.of(1L))
            .block();

        assertThat(mailboxMapperFactory.getMailboxMapper(session2)
            .findMailboxByPath(MailboxPath.forUser(username, "INBOX")).block())
            .isNotNull();
    }

    @Test
    void mailboxesShouldBeIsolatedByDomain() throws Exception {
        Username username = Username.of("alice@domain1");
        Username username2 = Username.of("bob@domain2");

        MailboxSession session = MailboxSessionUtil.create(username);
        MailboxSession session2 = MailboxSessionUtil.create(username2);

        mailboxMapperFactory.getMailboxMapper(session)
            .create(MailboxPath.forUser(username, "INBOX"), UidValidity.of(1L))
            .block();

        assertThat(mailboxMapperFactory.getMailboxMapper(session2)
            .findMailboxByPath(MailboxPath.forUser(username, "INBOX")).block())
            .isNull();
    }
}
