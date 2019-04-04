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
import java.util.List;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

import com.google.common.collect.ImmutableList;

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

    MessageId MESSAGE_ID_OTHER_USER_1 = MESSAGE_ID_FACTORY.generate();

    long SIZE_1 = 1000;
    long SIZE_2 = 2000;
    long MESSAGE_UID_1_VALUE = 1111L;
    long MESSAGE_UID_2_VALUE = 2222L;
    long MESSAGE_UID_OTHER_USER_1_VALUE = 1111L;
    MessageUid MESSAGE_UID_1 = MessageUid.of(MESSAGE_UID_1_VALUE);
    MessageUid MESSAGE_UID_2 = MessageUid.of(MESSAGE_UID_2_VALUE);
    MessageUid MESSAGE_UID_OTHER_USER_1 = MessageUid.of(MESSAGE_UID_OTHER_USER_1_VALUE);
    MailboxId MAILBOX_ID_1 = TestId.of(1L);
    MailboxId MAILBOX_ID_2 = TestId.of(2L);
    MailboxId MAILBOX_ID_11 = TestId.of(11L);
    Flags flags1 = new Flags("myFlags");

    MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create("user");

    String MAILBOX_1_NAME = "mailbox1";
    String MAILBOX_2_NAME = "mailbox2";
    String MAILBOX_OTHER_USER_NAME = "mailbox_other";
    Mailbox MAILBOX_1 = new Mailbox(MailboxPath.forUser("user", MAILBOX_1_NAME), 42, MAILBOX_ID_1);
    Mailbox MAILBOX_1_OTHER_USER = new Mailbox(MailboxPath.forUser("otherUser", MAILBOX_OTHER_USER_NAME), 42, MAILBOX_ID_11);
    Mailbox MAILBOX_1_SUB_1 = new Mailbox(MailboxPath.forUser("user", MAILBOX_1_NAME + MAILBOX_SESSION.getPathDelimiter() + "sub1"), 420, TestId.of(11L));
    Mailbox MAILBOX_2 = new Mailbox(MailboxPath.forUser("user", MAILBOX_2_NAME), 43, MAILBOX_ID_2);

    List<MailboxAnnotation> NO_ANNOTATION = ImmutableList.of();

    MailboxAnnotationKey ANNOTATION_1_KEY = new MailboxAnnotationKey("/annotation1/test");
    MailboxAnnotationKey ANNOTATION_2_KEY = new MailboxAnnotationKey("/annotation2/void");

    String ANNOTATION_1_CONTENT = "annotation1 content";
    String ANNOTATION_1_BIS_CONTENT = "annotation1 bis content";
    String ANNOTATION_2_CONTENT = "annotation2 content";

    MailboxAnnotation ANNOTATION_1 = MailboxAnnotation.newInstance(ANNOTATION_1_KEY, ANNOTATION_1_CONTENT);
    MailboxAnnotation ANNOTATION_1_BIS = MailboxAnnotation.newInstance(ANNOTATION_1_KEY, ANNOTATION_1_BIS_CONTENT);
    List<MailboxAnnotation> WITH_ANNOTATION_1 = ImmutableList.of(ANNOTATION_1);

    MailboxAnnotation ANNOTATION_2 = MailboxAnnotation.newInstance(ANNOTATION_2_KEY, ANNOTATION_2_CONTENT);
    List<MailboxAnnotation> WITH_ANNOTATION_1_AND_2 = ImmutableList.of(ANNOTATION_1, ANNOTATION_2);

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

    SimpleMailboxMessage MESSAGE_1_OTHER_USER = SimpleMailboxMessage.builder()
        .messageId(MESSAGE_ID_OTHER_USER_1)
        .uid(MESSAGE_UID_OTHER_USER_1)
        .content(CONTENT_STREAM_1)
        .size(SIZE_1)
        .internalDate(new Date(DATE_1.toEpochSecond()))
        .bodyStartOctet(0)
        .flags(flags1)
        .propertyBuilder(new PropertyBuilder())
        .mailboxId(MAILBOX_ID_11)
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
