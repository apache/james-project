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

import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.store.mail.model.Message;


public class SimpleMessageMetaData implements MessageMetaData{
    private long uid;
    private Flags flags;
    private long size;
    private Date internalDate;
    private long modSeq;

    public SimpleMessageMetaData(long uid, long modSeq, Flags flags, long size, Date internalDate) {
        this.uid = uid;
        this.flags = flags;
        this.size = size;
        this.modSeq = modSeq;
        this.internalDate = internalDate;
    }
    
    public SimpleMessageMetaData(Message<?> message) {
        this(message.getUid(), message.getModSeq(), message.createFlags(), message.getFullContentOctets(), message.getInternalDate());
    }
    
    /**
     * @see org.apache.james.mailbox.model.MessageMetaData#getFlags()
     */
    public Flags getFlags() {
        return flags;
    }

    /**
     * @see org.apache.james.mailbox.model.MessageMetaData#getSize()
     */
    public long getSize() {
        return size;
    }

    /**
     * @see org.apache.james.mailbox.model.MessageMetaData#getInternalDate()
     */
    public Date getInternalDate() {
        return internalDate;
    }

    /**
     * @see org.apache.james.mailbox.model.MessageMetaData#getUid()
     */
    public long getUid() {
        return uid;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof SimpleMessageMetaData) {
            return uid == ((SimpleMessageMetaData) obj).getUid();
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (int) (uid ^ (uid >>> 32));
        return result;
    }

    /**
     * @see org.apache.james.mailbox.model.MessageMetaData#getModSeq()
     */
    public long getModSeq() {
        return modSeq;
    }

}
