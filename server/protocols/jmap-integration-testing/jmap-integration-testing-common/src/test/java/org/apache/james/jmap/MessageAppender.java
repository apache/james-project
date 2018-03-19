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

import javax.mail.Flags;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.probe.MailboxProbe;

public class MessageAppender {

    private MessageAppender() {}

    public static void fillMailbox(MailboxProbe mailboxProbe, String user, String mailbox) {
        try {
            for (int i = 0; i < 1000; ++i) {
                ComposedMessageId message = mailboxProbe.appendMessage(user, MailboxPath.forUser(user, mailbox),
                    new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes(StandardCharsets.UTF_8)), new Date(), false, new Flags());
            }
        } catch (MailboxException ignored) {
            //we expect an exception to be thrown because of quota reached
        }
    }
}
