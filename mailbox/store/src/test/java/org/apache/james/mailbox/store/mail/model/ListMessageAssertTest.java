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

package org.apache.james.mailbox.store.mail.model;

import static org.apache.james.mailbox.store.mail.model.ListMessageAssert.assertMessages;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ListMessageAssertTest {
    static final String BODY_CONTENT2 = "Subject: Test2 \n\nBody2\n.\n";
    static final String BODY_CONTENT1 = "Subject: Test1 \n\nBody1\n.\n";
    static final int BODY_START = 16;
    static final UidValidity UID_VALIDITY = UidValidity.of(42);
    static final MailboxId MAILBOX_ID = TestId.of(1);
    static final MessageUid MESSAGE_UID = MessageUid.of(2);
    static final MessageId MESSAGE_ID = new DefaultMessageId();
    static final ThreadId THREAD_ID = ThreadId.fromBaseMessageId(MESSAGE_ID);
    static final Date INTERNAL_DATE = new Date();

    Mailbox benwaInboxMailbox;

    MailboxMessage message1;
    MailboxMessage message2;
    
    @BeforeEach
    void setUp() {
        benwaInboxMailbox = createMailbox(MailboxPath.inbox(Username.of("user")));

        message1 = createMessage(benwaInboxMailbox, MESSAGE_ID, THREAD_ID, BODY_CONTENT1, BODY_START, new PropertyBuilder());
        message2 = createMessage(benwaInboxMailbox, MESSAGE_ID, THREAD_ID, BODY_CONTENT2, BODY_START, new PropertyBuilder());
    }

    @Test
    void containsOnlyShouldWork() throws IOException {
        List<MailboxMessage> actual = ImmutableList.of(message1, message2);

        assertMessages(actual).containOnly(
                createMailboxMessage(MAILBOX_ID, MESSAGE_ID, THREAD_ID, MESSAGE_UID, INTERNAL_DATE, BODY_CONTENT1, BODY_START, new PropertyBuilder()),
                createMailboxMessage(MAILBOX_ID, MESSAGE_ID, THREAD_ID, MESSAGE_UID, INTERNAL_DATE, BODY_CONTENT2, BODY_START, new PropertyBuilder()));
    }

    @Test
    void containsOnlyShouldThrowExceptionWhenHavingElementDoesNotBelongToList() {
        List<MailboxMessage> actual = ImmutableList.of(message1);

        assertThatThrownBy(() -> assertMessages(actual).containOnly(
                createMailboxMessage(MAILBOX_ID, MESSAGE_ID, THREAD_ID, MESSAGE_UID, INTERNAL_DATE, BODY_CONTENT2, BODY_START, new PropertyBuilder())))
            .isInstanceOf(AssertionError.class);
    }

    private MailboxMessage createMailboxMessage(MailboxId mailboxId, MessageId messageId, ThreadId threadId, MessageUid uid, Date internalDate,
                                                String content, int bodyStart, PropertyBuilder propertyBuilder) {
        SimpleMailboxMessage simpleMailboxMessage = new SimpleMailboxMessage(messageId, threadId, internalDate, content.length(),
            bodyStart, new ByteContent(content.getBytes(StandardCharsets.UTF_8)), new Flags(), propertyBuilder.build(), mailboxId);

        simpleMailboxMessage.setUid(uid);
        simpleMailboxMessage.setModSeq(ModSeq.first());
        return simpleMailboxMessage;
    }

    private Mailbox createMailbox(MailboxPath mailboxPath) {
        return new Mailbox(mailboxPath, UID_VALIDITY, MAILBOX_ID);
    }

    private MailboxMessage createMessage(Mailbox mailbox, MessageId messageId, ThreadId threadId, String content, int bodyStart, PropertyBuilder propertyBuilder) {
        SimpleMailboxMessage simpleMailboxMessage = new SimpleMailboxMessage(messageId, threadId, INTERNAL_DATE, content.length(),
            bodyStart, new ByteContent(content.getBytes()), new Flags(), propertyBuilder.build(), mailbox.getMailboxId());

        simpleMailboxMessage.setUid(MESSAGE_UID);
        return simpleMailboxMessage;
    }

}
