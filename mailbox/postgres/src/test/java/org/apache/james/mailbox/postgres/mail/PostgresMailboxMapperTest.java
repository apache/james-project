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
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMapperTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresMailboxMapperTest extends MailboxMapperTest {
    protected static final Username ALICE = Username.of("alice");
    protected static final Username BOB = Username.of("bob");
    protected static final MailboxPath ALICE_INBOX_PATH = MailboxPath.forUser(ALICE, "INBOX");
    protected static final MailboxPath BOB_INBOX_PATH = MailboxPath.forUser(BOB, "INBOX");

    @RegisterExtension
    static PostgresExtension postgresExtension = new PostgresExtension(PostgresMailboxModule.MODULE);

    private MailboxMapper mailboxMapper;

    @Override
    protected MailboxMapper createMailboxMapper() {
        mailboxMapper = new PostgresMailboxMapper(new PostgresMailboxDAO(postgresExtension.getPostgresExecutor()));
        return mailboxMapper;
    }

    @Override
    protected MailboxId generateId() {
        return PostgresMailboxId.generate();
    }

    @Test
    void renameShouldUpdateOnyOneMailbox() {
        MailboxId aliceMailboxId = mailboxMapper.create(ALICE_INBOX_PATH, UidValidity.of(1L)).block().getMailboxId();
        MailboxId bobMailboxId = mailboxMapper.create(BOB_INBOX_PATH, UidValidity.of(2L)).block().getMailboxId();

        MailboxPath newMailboxPath = new MailboxPath(ALICE_INBOX_PATH.getNamespace(), ALICE_INBOX_PATH.getUser(), "ENBOX");
        mailboxMapper.rename(new Mailbox(newMailboxPath, UidValidity.of(1L), aliceMailboxId)).block();

        Mailbox actualAliceMailbox = mailboxMapper.findMailboxById(aliceMailboxId).block();
        Mailbox actualBobMailbox = mailboxMapper.findMailboxById(bobMailboxId).block();

        assertThat(actualAliceMailbox.getName()).isEqualTo("ENBOX");
        assertThat(actualBobMailbox.getName()).isEqualTo(BOB_INBOX_PATH.getName());
    }
}
