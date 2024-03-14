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

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.mail.Flags;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateModule;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresMessageBlobReferenceSourceTest {
    private static final int BODY_START = 16;
    private static final PostgresMailboxId MAILBOX_ID = PostgresMailboxId.generate();
    private static final String CONTENT = "Subject: Test7 \n\nBody7\n.\n";
    private static final String CONTENT_2 = "Subject: Test3 \n\nBody23\n.\n";
    private static final MessageUid MESSAGE_UID = MessageUid.of(1);

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    PostgresMessageBlobReferenceSource blobReferenceSource;
    PostgresMessageDAO postgresMessageDAO;

    @BeforeEach
    void beforeEach() {
        postgresMessageDAO = new PostgresMessageDAO(postgresExtension.getPostgresExecutor(), new HashBlobId.Factory());
        blobReferenceSource = new PostgresMessageBlobReferenceSource(postgresMessageDAO);
    }

    @Test
    void blobReferencesShouldBeEmptyByDefault() {
        assertThat(blobReferenceSource.listReferencedBlobs().collectList().block())
            .isEmpty();
    }

    @Test
    void blobReferencesShouldReturnAllBlobs() {
        MessageId messageId1 = PostgresMessageId.Factory.of(UUID.randomUUID());
        SimpleMailboxMessage message = createMessage(messageId1, ThreadId.fromBaseMessageId(messageId1),  CONTENT, BODY_START, new PropertyBuilder());
        MessageId messageId2 = PostgresMessageId.Factory.of(UUID.randomUUID());
        MailboxMessage message2 = createMessage(messageId2, ThreadId.fromBaseMessageId(messageId2),  CONTENT_2, BODY_START, new PropertyBuilder());
        postgresMessageDAO.insert(message, "1").block();
        postgresMessageDAO.insert(message2, "2").block();

        assertThat(blobReferenceSource.listReferencedBlobs().collectList().block())
            .hasSize(2);
    }

    private SimpleMailboxMessage createMessage(MessageId messageId, ThreadId threadId, String content, int bodyStart, PropertyBuilder propertyBuilder) {
        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .threadId(threadId)
            .mailboxId(MAILBOX_ID)
            .uid(MESSAGE_UID)
            .internalDate(new Date())
            .bodyStartOctet(bodyStart)
            .size(content.length())
            .content(new ByteContent(content.getBytes(StandardCharsets.UTF_8)))
            .flags(new Flags())
            .properties(propertyBuilder)
            .build();
    }

}
