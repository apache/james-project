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
package org.apache.james.mailbox.store;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

public class MessageBuilder {
    
    public TestId mailboxId = TestId.of(113);
    public MessageUid uid = MessageUid.of(776);
    public Date internalDate = new Date();
    public int size = 8867;
    public Flags flags = new Flags();
    public byte[] body = {};
    public final Map<String, String> headers = new HashMap<>();
    public int lineNumber = 0;
    
    public MailboxMessage build() throws Exception {
        return build(new DefaultMessageId());
    }

    public MailboxMessage build(MessageId messageId) throws Exception {
        return new SimpleMailboxMembership(messageId, mailboxId, uid, -1,  internalDate, size, flags, body, headers);
    }

    public MessageBuilder size(int size) {
        this.size = size;
        return this;
    }

    public void header(String field, String value) {
        headers.put(field, value);
    }

    public void setKey(int mailboxId, MessageUid uid) {
        this.uid = uid;
        this.mailboxId = TestId.of(mailboxId);
    }
    
    public void setFlags(boolean seen, boolean flagged, boolean answered,
            boolean draft, boolean deleted, boolean recent) {
        if (seen) {
            flags.add(Flags.Flag.SEEN);
        }
        if (flagged) {
            flags.add(Flags.Flag.FLAGGED);
        }
        if (answered) {
            flags.add(Flags.Flag.ANSWERED);
        }
        if (draft) {
            flags.add(Flags.Flag.DRAFT);
        }
        if (deleted) {
            flags.add(Flags.Flag.DELETED);
        }
        if (recent) {
            flags.add(Flags.Flag.RECENT);
        }
    }
}
