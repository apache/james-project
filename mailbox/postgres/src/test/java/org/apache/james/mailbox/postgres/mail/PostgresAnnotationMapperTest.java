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

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateModule;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxAnnotationDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.AnnotationMapperTest;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresAnnotationMapperTest extends AnnotationMapperTest {
    private static final UidValidity UID_VALIDITY = UidValidity.of(42);
    private static final Username BENWA = Username.of("benwa");
    protected static final MailboxPath benwaInboxPath = MailboxPath.forUser(BENWA, "INBOX");

    private MailboxId mailboxId;

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    @Override
    protected AnnotationMapper createAnnotationMapper() {
        return new PostgresAnnotationMapper(new PostgresMailboxAnnotationDAO(postgresExtension.getPostgresExecutor()));
    }

    @Override
    protected MailboxId generateMailboxId() {
        MailboxMapper mailboxMapper = new PostgresMailboxMapper(new PostgresMailboxDAO(postgresExtension.getPostgresExecutor()));
        mailboxId = mailboxMapper.create(benwaInboxPath, UID_VALIDITY).block().getMailboxId();
        return mailboxId;
    }
}
