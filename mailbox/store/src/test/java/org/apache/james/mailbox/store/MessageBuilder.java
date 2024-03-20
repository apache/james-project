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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;
import reactor.core.publisher.Mono;

public class MessageBuilder {
    private static final char[] NEW_LINE = { 0x0D, 0x0A };
    private static final ImmutableList<MessageAttachmentMetadata> NO_ATTACHMENTS = ImmutableList.of();

    private TestId mailboxId = TestId.of(113);
    private MessageUid uid = MessageUid.of(776);
    private Date internalDate = new Date();
    private Optional<Date> saveDate = Optional.of(new Date());
    private int size = 8867;
    private Flags flags = new Flags();
    private byte[] body = {};
    private final Map<String, String> headers = new HashMap<>();

    public MessageBuilder mailboxId(TestId testId) {
        this.mailboxId = testId;
        return this;
    }

    public MessageBuilder uid(MessageUid uid) {
        this.uid = uid;
        return this;
    }

    public MessageBuilder internalDate(Date internalDate) {
        this.internalDate = internalDate;
        return this;
    }

    public MessageBuilder body(byte[] body) {
        this.body = body;
        return this;
    }
    
    public MailboxMessage build() throws Exception {
        return build(new DefaultMessageId());
    }

    public MailboxMessage build(MessageId messageId) throws Exception {
        byte[] headerContent = getHeaderContent();
        ThreadId threadId = ThreadId.fromBaseMessageId(messageId);
        SimpleMailboxMessage mailboxMessage = new SimpleMailboxMessage(messageId, threadId, internalDate, size, headerContent.length,
            new ByteContent(Bytes.concat(headerContent, body)), flags, new PropertyBuilder().build(), mailboxId, NO_ATTACHMENTS, Mono.error(new NotImplementedException()), saveDate);
        mailboxMessage.setUid(uid);
        return mailboxMessage;
    }

    private byte[] getHeaderContent() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final Writer writer = new OutputStreamWriter(baos, StandardCharsets.US_ASCII);

        for (Map.Entry<String, String> header : headers.entrySet()) {
            writer.write(header.getKey());
            writer.write(": ");
            writer.write(header.getValue());
            writer.write(NEW_LINE);
        }
        writer.write(NEW_LINE);
        writer.flush();
        return baos.toByteArray();
    }

    public MessageBuilder size(int size) {
        this.size = size;
        return this;
    }

    public MessageBuilder header(String field, String value) {
        headers.put(field, value);
        return this;
    }

    public MessageBuilder headers(Map<String, String> headers) {
        this.headers.putAll(headers);
        return this;
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

    public MessageBuilder flags(Flags flags) {
        this.flags = flags;
        return this;
    }
}
