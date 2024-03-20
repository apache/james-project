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

package org.apache.james.mailbox.postgres.search;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

interface SearchOverrideFixture {
    MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create(Username.of("benwa"));
    Mailbox MAILBOX = new Mailbox(MailboxPath.inbox(MAILBOX_SESSION), UidValidity.of(12), PostgresMailboxId.generate());
    String BLOB_ID = "abc";
    Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;
    String MESSAGE_CONTENT = "Simple message content";
    byte[] MESSAGE_CONTENT_BYTES = MESSAGE_CONTENT.getBytes(MESSAGE_CHARSET);
    ByteContent CONTENT_STREAM = new ByteContent(MESSAGE_CONTENT_BYTES);
    long SIZE = MESSAGE_CONTENT_BYTES.length;

    static MailboxMessage createMessage(MessageUid messageUid, MailboxId mailboxId, Flags flags) {
        PostgresMessageId messageId = new PostgresMessageId.Factory().generate();
        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .threadId(ThreadId.fromBaseMessageId(messageId))
            .uid(messageUid)
            .content(CONTENT_STREAM)
            .size(SIZE)
            .internalDate(new Date())
            .bodyStartOctet(18)
            .flags(flags)
            .properties(new PropertyBuilder())
            .mailboxId(mailboxId)
            .modseq(ModSeq.of(1))
            .build();
    }
}
