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

import org.apache.james.mailbox.model.MessageRange;

import com.google.common.base.Preconditions;

public class MessageUid implements Comparable<MessageUid> {

    public static final MessageUid MAX_VALUE = of(Long.MAX_VALUE);
    public static final MessageUid MIN_VALUE = of(1L);

    public static MessageUid of(long uid) {
        return new MessageUid(uid);
    }

    private final long uid;
    
    private MessageUid(long uid) {
        this.uid = uid;
    }
    
    public MessageRange toRange() {
        return MessageRange.one(this);
    }
    
    @Override
    public int compareTo(MessageUid o) {
        return Long.compare(uid, o.uid);
    }

    public long asLong() {
        return uid;
    }

    public MessageUid add(int offset) {
        return MessageUid.of(uid + offset);
    }
    
    @Override
    public int hashCode() {
        return Long.hashCode(uid);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageUid) {
            MessageUid other = (MessageUid) obj;
            return other.uid == this.uid;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return "MessageUid{uid=" + uid + "}";
    }

    public MessageUid next() {
        return next(1);
    }

    public MessageUid next(int count) {
        Preconditions.checkArgument(count > 0);
        return new MessageUid(uid + count);
    }

    public boolean isFirst() {
        return this.equals(MIN_VALUE);
    }

    public MessageUid previous() {
        if (this.compareTo(MIN_VALUE) > 0) {
            return new MessageUid(uid - 1);
        }
        return MIN_VALUE;
    }

    public long distance(MessageUid other) {
        Preconditions.checkNotNull(other);
        return other.uid - this.uid;
    }
}
