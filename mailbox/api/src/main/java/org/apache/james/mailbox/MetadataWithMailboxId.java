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

package org.apache.james.mailbox;

import java.util.Objects;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;

public class MetadataWithMailboxId {

    public static MetadataWithMailboxId from(MessageMetaData messageMetaData, MailboxId mailboxId) {
        return new MetadataWithMailboxId(messageMetaData.getMessageId(), messageMetaData.getSize(), mailboxId);
    }

    private final MessageId messageId;
    private final long size;
    private final MailboxId mailboxId;

    public MetadataWithMailboxId(MessageId messageId, long size, MailboxId mailboxId) {
        this.messageId = messageId;
        this.size = size;
        this.mailboxId = mailboxId;
    }

    public MessageId getMessageId() {
        return messageId;
    }

    public long getSize() {
        return size;
    }

    public MailboxId getMailboxId() {
        return mailboxId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MetadataWithMailboxId) {
            MetadataWithMailboxId that = (MetadataWithMailboxId) o;

            return Objects.equals(this.messageId, that.messageId)
                && Objects.equals(this.size, that.size)
                && Objects.equals(this.mailboxId, that.mailboxId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(messageId, size, mailboxId);
    }
}