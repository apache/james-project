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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

public interface MailboxMessageFixture {

    SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    Date DATE_1 = parseDate("2018-02-15 15:54:02");
    Date DATE_2 = parseDate("2018-03-15 15:54:02");

    MessageId.Factory MESSAGE_ID_FACTORY = new TestMessageId.Factory();
    Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;
    String MESSAGE_CONTENT_1 = "Simple message content";
    SharedByteArrayInputStream CONTENT_STREAM_1 = new SharedByteArrayInputStream(MESSAGE_CONTENT_1.getBytes(MESSAGE_CHARSET));
    String MESSAGE_CONTENT_2 = "Other message content";
    SharedByteArrayInputStream CONTENT_STREAM_2 = new SharedByteArrayInputStream(MESSAGE_CONTENT_2.getBytes(MESSAGE_CHARSET));
    MessageId MESSAGE_ID_1 = MESSAGE_ID_FACTORY.generate();
    MessageId MESSAGE_ID_2 = MESSAGE_ID_FACTORY.generate();
    int SIZE_1 = 1000;
    int SIZE_2 = 2000;

    SimpleMailboxMessage MESSAGE_1 = SimpleMailboxMessage.builder()
        .messageId(MESSAGE_ID_1)
        .content(CONTENT_STREAM_1)
        .size(SIZE_1)
        .internalDate(DATE_1)
        .bodyStartOctet(0)
        .flags(new Flags())
        .propertyBuilder(new PropertyBuilder())
        .mailboxId(TestId.of(1L))
        .build();
    SimpleMailboxMessage MESSAGE_2 = SimpleMailboxMessage.builder()
        .messageId(MESSAGE_ID_2)
        .content(CONTENT_STREAM_2)
        .size(SIZE_2)
        .internalDate(DATE_2)
        .bodyStartOctet(0)
        .flags(new Flags())
        .propertyBuilder(new PropertyBuilder())
        .mailboxId(TestId.of(1L))
        .build();

    static Date parseDate(String input) {
        try {
            return SIMPLE_DATE_FORMAT.parse(input);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
