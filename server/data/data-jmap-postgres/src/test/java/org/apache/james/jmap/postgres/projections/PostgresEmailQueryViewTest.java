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

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.jmap.api.projections.EmailQueryViewContract;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresEmailQueryViewTest implements EmailQueryViewContract {
    public static final PostgresMailboxId MAILBOX_ID_1 = PostgresMailboxId.generate();
    public static final PostgresMessageId.Factory MESSAGE_ID_FACTORY = new PostgresMessageId.Factory();
    public static final PostgresMessageId MESSAGE_ID_1 = MESSAGE_ID_FACTORY.generate();
    public static final PostgresMessageId MESSAGE_ID_2 = MESSAGE_ID_FACTORY.generate();
    public static final PostgresMessageId MESSAGE_ID_3 = MESSAGE_ID_FACTORY.generate();
    public static final PostgresMessageId MESSAGE_ID_4 = MESSAGE_ID_FACTORY.generate();

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresEmailQueryViewModule.MODULE);

    @Override
    public EmailQueryView testee() {
        return new PostgresEmailQueryView(new PostgresEmailQueryViewDAO(postgresExtension.getPostgresExecutor()));
    }

    @Override
    public MailboxId mailboxId1() {
        return MAILBOX_ID_1;
    }

    @Override
    public MessageId messageId1() {
        return MESSAGE_ID_1;
    }

    @Override
    public MessageId messageId2() {
        return MESSAGE_ID_2;
    }

    @Override
    public MessageId messageId3() {
        return MESSAGE_ID_3;
    }

    @Override
    public MessageId messageId4() {
        return MESSAGE_ID_4;
    }
}
