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

package org.apache.james.jmap;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.probe.MailboxProbe;

import com.google.common.collect.ImmutableList;

public class MessageAppender {

    private MessageAppender() {

    }

    public static List<ComposedMessageId> fillMailbox(MailboxProbe mailboxProbe, String user, String mailbox) {
        ImmutableList.Builder<ComposedMessageId> insertedMessages = ImmutableList.builder();
        try {
            for (int i = 0; i < 1000; ++i) {
                String mailContent = "Subject: test\r\n\r\ntestmail" + String.valueOf(i);
                ByteArrayInputStream messagePayload = new ByteArrayInputStream(mailContent.getBytes(StandardCharsets.UTF_8));
                insertedMessages.add(
                    mailboxProbe.appendMessage(user, MailboxPath.forUser(Username.of(user), mailbox), messagePayload, new Date(), false, new Flags()));
            }
        } catch (MailboxException ignored) {
            //we expect an exception to be thrown because of quota reached
        }
        return insertedMessages.build();
    }
}
