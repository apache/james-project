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

package org.apache.james.mailbox.backup;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

public interface MailboxMessageFixture {

    String DATE_STRING_1 = "2018-02-15T15:54:02Z";
    String DATE_STRING_2 = "2018-03-15T15:54:02Z";
    ZonedDateTime DATE_1 = ZonedDateTime.parse(DATE_STRING_1);
    ZonedDateTime DATE_2 = ZonedDateTime.parse(DATE_STRING_2);

    MessageId.Factory MESSAGE_ID_FACTORY = new TestMessageId.Factory();
    Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;
    String MESSAGE_CONTENT_1 = "Simple message content";
    SharedByteArrayInputStream CONTENT_STREAM_1 = new SharedByteArrayInputStream(MESSAGE_CONTENT_1.getBytes(MESSAGE_CHARSET));
    String MESSAGE_CONTENT_2 = "Other message content";
    SharedByteArrayInputStream CONTENT_STREAM_2 = new SharedByteArrayInputStream(MESSAGE_CONTENT_2.getBytes(MESSAGE_CHARSET));
    MessageId MESSAGE_ID_1 = MESSAGE_ID_FACTORY.generate();
    MessageId MESSAGE_ID_2 = MESSAGE_ID_FACTORY.generate();
    long SIZE_1 = 1000;
    long SIZE_2 = 2000;
    long MESSAGE_UID_1_VALUE = 1111L;
    long MESSAGE_UID_2_VALUE = 2222L;
    MessageUid MESSAGE_UID_1 = MessageUid.of(MESSAGE_UID_1_VALUE);
    MessageUid MESSAGE_UID_2 = MessageUid.of(MESSAGE_UID_2_VALUE);
    MailboxId MAILBOX_ID_1 = TestId.of(1L);
    Flags flags1 = new Flags("myFlags");

    MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create("user");
    
    Mailbox MAILBOX_1 = new SimpleMailbox(MailboxPath.forUser("user", "mailbox1"), 42, TestId.of(1L));
    Mailbox MAILBOX_1_SUB_1 = new SimpleMailbox(MailboxPath.forUser("user", "mailbox1" + MAILBOX_SESSION.getPathDelimiter() + "sub1"), 420, TestId.of(11L));
    Mailbox MAILBOX_2 = new SimpleMailbox(MailboxPath.forUser("user", "mailbox2"), 43, TestId.of(2L));

    SimpleMailboxMessage MESSAGE_1 = SimpleMailboxMessage.builder()
        .messageId(MESSAGE_ID_1)
        .uid(MESSAGE_UID_1)
        .content(CONTENT_STREAM_1)
        .size(SIZE_1)
        .internalDate(new Date(DATE_1.toEpochSecond()))
        .bodyStartOctet(0)
        .flags(flags1)
        .propertyBuilder(new PropertyBuilder())
        .mailboxId(MAILBOX_ID_1)
        .build();
    SimpleMailboxMessage MESSAGE_2 = SimpleMailboxMessage.builder()
        .messageId(MESSAGE_ID_2)
        .uid(MESSAGE_UID_2)
        .content(CONTENT_STREAM_2)
        .size(SIZE_2)
        .internalDate(new Date(DATE_2.toEpochSecond()))
        .bodyStartOctet(0)
        .flags(new Flags())
        .propertyBuilder(new PropertyBuilder())
        .mailboxId(MAILBOX_ID_1)
        .build();

}
