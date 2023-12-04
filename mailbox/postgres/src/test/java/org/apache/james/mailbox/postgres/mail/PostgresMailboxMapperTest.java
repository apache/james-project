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
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
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
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxModule.MODULE);

    @Override
    protected MailboxMapper createMailboxMapper() {
        return new PostgresMailboxMapper(new PostgresMailboxDAO(postgresExtension.getPostgresExecutor()));
    }

    @Override
    protected MailboxId generateId() {
        return PostgresMailboxId.generate();
    }

    @Test
    void retrieveMailboxShouldReturnCorrectHighestModSeqAndLastUidWhenDefault() {
        Mailbox mailbox = mailboxMapper.create(benwaInboxPath, UidValidity.of(43)).block();

        PostgresMailbox metaData = (PostgresMailbox) mailboxMapper.findMailboxById(mailbox.getMailboxId()).block();

        assertThat(metaData.getHighestModSeq()).isEqualTo(ModSeq.first());
        assertThat(metaData.getLastUid()).isEqualTo(null);
    }

    @Test
    void retrieveMailboxShouldReturnCorrectHighestModSeqAndLastUid() {
        Username BENWA = Username.of("benwa");
        MailboxPath benwaInboxPath = MailboxPath.forUser(BENWA, "INBOX");

        Mailbox mailbox = mailboxMapper.create(benwaInboxPath, UidValidity.of(43)).block();

        // increase modSeq
        ModSeq nextModSeq = new PostgresModSeqProvider.Factory(postgresExtension.getExecutorFactory()).create(MailboxSessionUtil.create(BENWA))
            .nextModSeqReactive(mailbox.getMailboxId()).block();

        // increase lastUid
        MessageUid nextUid = new PostgresUidProvider.Factory(postgresExtension.getExecutorFactory()).create(MailboxSessionUtil.create(BENWA))
            .nextUidReactive(mailbox.getMailboxId()).block();

        PostgresMailbox metaData = (PostgresMailbox) mailboxMapper.findMailboxById(mailbox.getMailboxId()).block();

        assertThat(metaData.getHighestModSeq()).isEqualTo(nextModSeq);
        assertThat(metaData.getLastUid()).isEqualTo(nextUid);
    }

}
