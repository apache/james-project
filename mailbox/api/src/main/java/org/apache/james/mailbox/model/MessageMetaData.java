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
package org.apache.james.mailbox.model;

import java.util.Date;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;

import com.google.common.base.Objects;

public class MessageMetaData {
    private final MessageUid uid;
    private final Flags flags;
    private final long size;
    private final Date internalDate;
    private final ModSeq modSeq;
    private final MessageId messageId;

    public MessageMetaData(MessageUid uid, ModSeq modSeq, Flags flags, long size, Date internalDate, MessageId messageId) {
        this.uid = uid;
        this.flags = flags;
        this.size = size;
        this.modSeq = modSeq;
        this.internalDate = internalDate;
        this.messageId = messageId;
    }

    public Flags getFlags() {
        return flags;
    }

    /**
     * Return the size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * <p>
     * IMAP defines this as the time when the message has arrived to the server
     * (by smtp). Clients are also allowed to set the internalDate on append.
     * </p>
     */
    public Date getInternalDate() {
        return internalDate;
    }

    public MessageUid getUid() {
        return uid;
    }

    public MessageId getMessageId() {
        return messageId;
    }

    /**
     * Return the modify-sequence number of the message. This is kind of optional and the mailbox
     * implementation may not support this. If so it will return -1
     */
    public ModSeq getModSeq() {
        return modSeq;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageMetaData) {
            return uid.equals(((MessageMetaData) obj).getUid());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uid);
    }

}
