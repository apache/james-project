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

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;

import com.google.common.base.Objects;


public class SimpleMessageMetaData implements MessageMetaData {
    private final MessageUid uid;
    private final Flags flags;
    private final long size;
    private final Date internalDate;
    private final long modSeq;
    private final MessageId messageId;

    public SimpleMessageMetaData(MessageUid uid, long modSeq, Flags flags, long size, Date internalDate, MessageId messageId) {
        this.uid = uid;
        this.flags = flags;
        this.size = size;
        this.modSeq = modSeq;
        this.internalDate = internalDate;
        this.messageId = messageId;
    }
    
    @Override
    public Flags getFlags() {
        return flags;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public Date getInternalDate() {
        return internalDate;
    }

    @Override
    public MessageUid getUid() {
        return uid;
    }

    @Override
    public MessageId getMessageId() {
        return messageId;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleMessageMetaData) {
            return uid.equals(((SimpleMessageMetaData) obj).getUid());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uid);
    }

    @Override
    public long getModSeq() {
        return modSeq;
    }

}
