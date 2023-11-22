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


import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.mail.PostgresMessageModule;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresMailboxMessageDAOTest {
    static String MESSAGE_CONTENT_1 = "Simple message content";
    static byte[] MESSAGE_CONTENT_BYTES_1 = MESSAGE_CONTENT_1.getBytes(StandardCharsets.UTF_8);
    static ByteContent CONTENT_STREAM_1 = new ByteContent(MESSAGE_CONTENT_BYTES_1);

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMessageModule.MODULE);

    private PostgresMailboxMessageDAO testee;
    private PostgresMessageDAO messageDAO;

    @BeforeEach
    void setUp() {
        testee = new PostgresMailboxMessageDAO(postgresExtension.getPostgresExecutor());
        messageDAO = new PostgresMessageDAO(postgresExtension.getPostgresExecutor());
    }

    @Test
    void insertMessage() {
        PostgresMessageId messageId = new PostgresMessageId.Factory().generate();
        PostgresMailboxId mailboxId = PostgresMailboxId.generate();
        SimpleMailboxMessage message1 = SimpleMailboxMessage.builder()
            .messageId(messageId)
            .threadId(ThreadId.fromBaseMessageId(messageId))
            .uid(MessageUid.of(11L))
            .content(CONTENT_STREAM_1)
            .size(MESSAGE_CONTENT_BYTES_1.length)
            .internalDate(new Date(ZonedDateTime.parse("2018-02-15T15:54:02Z").toEpochSecond()))
            .bodyStartOctet(0)
            .flags(new Flags("myFlags"))
            .properties(new PropertyBuilder())
            .mailboxId(mailboxId)
            .build();

        messageDAO.insert(message1, "blobId1").block();
        testee.insert(message1).block();
        List<MailboxMessage> simpleMailboxMessages = testee.findMessagesByMailboxId(mailboxId)
            .collectList()
            .block();
        assertThat(simpleMailboxMessages).hasSize(1);
    }
}
