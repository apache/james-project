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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Date;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.Test;

class MailboxMessageAssertTest {

    static final TestId MAILBOX_ID = TestId.of(42L);
    static final MessageUid UID = MessageUid.of(24);
    static final MessageId MESSAGE_ID = new DefaultMessageId();
    static final ThreadId THREAD_ID = ThreadId.fromBaseMessageId(MESSAGE_ID);

    @Test
    void messageAssertShouldSucceedWithTwoEqualsMessages() throws IOException {
        String headerString = "name: headerName\n\n";
        String bodyString = "body\n.\n";
        Date date = new Date();

        SimpleMailboxMessage message1 = new SimpleMailboxMessage(MESSAGE_ID, THREAD_ID, date, headerString.length() + bodyString.length(),
            headerString.length(), new ByteContent((headerString + bodyString).getBytes()), new Flags(), new PropertyBuilder().build(), MAILBOX_ID);
        message1.setUid(UID);

        SimpleMailboxMessage message2 = new SimpleMailboxMessage(MESSAGE_ID, THREAD_ID, date, headerString.length() + bodyString.length(),
            headerString.length(), new ByteContent((headerString + bodyString).getBytes()), new Flags(), new PropertyBuilder().build(), MAILBOX_ID);
        message2.setUid(UID);

        MessageAssert.assertThat(message1).isEqualTo(message2, MessageMapper.FetchType.FULL);
    }

    @Test
    void messageAssertShouldSucceedWhenBodyMismatchInFetchHeaderMode() throws IOException {
        String headerString = "name: headerName\n\n";
        String bodyString = "body\n.\n";
        Date date = new Date();

        SimpleMailboxMessage message1 = new SimpleMailboxMessage(MESSAGE_ID, THREAD_ID, date, headerString.length() + bodyString.length(),
            headerString.length(), new ByteContent((headerString + bodyString).getBytes()), new Flags(), new PropertyBuilder().build(), MAILBOX_ID);
        message1.setUid(UID);

        bodyString = "work\n.\n";
        SimpleMailboxMessage message2 = new SimpleMailboxMessage(MESSAGE_ID, THREAD_ID, date, headerString.length() + bodyString.length(),
            headerString.length(), new ByteContent((headerString + bodyString).getBytes()), new Flags(), new PropertyBuilder().build(), MAILBOX_ID);
        message2.setUid(UID);

        MessageAssert.assertThat(message1).isEqualTo(message2, MessageMapper.FetchType.HEADERS);
    }

    @Test
    void messageAssertShouldFailWhenBodyMismatchInFetchFullMode() {
        String headerString = "name: headerName\n\n";
        String bodyString = "body\n.\n";
        Date date = new Date();

        SimpleMailboxMessage message1 = new SimpleMailboxMessage(MESSAGE_ID, THREAD_ID, date, headerString.length() + bodyString.length(),
            headerString.length(), new ByteContent((headerString + bodyString).getBytes()), new Flags(), new PropertyBuilder().build(), MAILBOX_ID);
        message1.setUid(UID);

        bodyString = "work\n.\n";
        SimpleMailboxMessage message2 = new SimpleMailboxMessage(MESSAGE_ID, THREAD_ID, date, headerString.length() + bodyString.length(),
            headerString.length(), new ByteContent((headerString + bodyString).getBytes()), new Flags(), new PropertyBuilder().build(), MAILBOX_ID);
        message2.setUid(UID);

        assertThatThrownBy(() -> MessageAssert.assertThat(message1).isEqualTo(message2, MessageMapper.FetchType.FULL))
            .isInstanceOf(AssertionError.class);
    }

}
