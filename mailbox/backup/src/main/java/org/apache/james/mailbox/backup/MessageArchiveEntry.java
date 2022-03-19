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

import java.io.InputStream;
import java.util.Date;

import jakarta.mail.Flags;

public class MessageArchiveEntry implements MailArchiveEntry {

    private final SerializedMessageId messageId;
    private final SerializedMailboxId mailboxId;
    private final long size;
    private final Date internalDate;
    private final Flags flags;
    private final InputStream content;

    public MessageArchiveEntry(SerializedMessageId messageId, SerializedMailboxId mailboxId, long size, Date internalDate, Flags flags, InputStream content) {
        this.messageId = messageId;
        this.mailboxId = mailboxId;
        this.size = size;
        this.internalDate = internalDate;
        this.flags = flags;
        this.content = content;
    }

    public SerializedMessageId getMessageId() {
        return messageId;
    }

    public SerializedMailboxId getMailboxId() {
        return mailboxId;
    }

    public long getSize() {
        return size;
    }

    public Date getInternalDate() {
        return internalDate;
    }

    public Flags getFlags() {
        return flags;
    }

    public InputStream getContent() {
        return content;
    }

    @Override
    public ArchiveEntryType getType() {
        return ArchiveEntryType.MESSAGE;
    }
}
