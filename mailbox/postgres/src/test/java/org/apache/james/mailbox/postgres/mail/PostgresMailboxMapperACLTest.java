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
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMapperACLTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresMailboxMapperACLTest extends MailboxMapperACLTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxDataDefinition.MODULE);

    private PostgresMailboxMapper mailboxMapper;

    @Override
    protected MailboxMapper createMailboxMapper() {
        mailboxMapper = new PostgresMailboxMapper(new PostgresMailboxDAO(postgresExtension.getDefaultPostgresExecutor()));
        return mailboxMapper;
    }

    @Test
    void findNonPersonalMailboxesShouldSupportUsernamesContainingSingleQuote() {
        // findMailboxesByUsername used to inline the username into the SQL text. A username
        // containing a single quote then broke the query (SQL injection / syntax error). The
        // username is now a bind parameter, so such usernames must be handled correctly.
        Username trickyUser = Username.of("o'brien@domain.tld");
        MailboxACL.EntryKey key = MailboxACL.EntryKey.createUserEntryKey(trickyUser);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Lookup);
        mailboxMapper.updateACL(benwaInboxMailbox, MailboxACL.command().key(key).rights(rights).asReplacement()).block();

        assertThat(mailboxMapper.findNonPersonalMailboxes(trickyUser, MailboxACL.Right.Lookup).collectList().block())
            .containsOnly(benwaInboxMailbox);
    }
}
