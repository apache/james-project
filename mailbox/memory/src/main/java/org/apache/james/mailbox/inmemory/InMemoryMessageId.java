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
package org.apache.james.mailbox.inmemory;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.mailbox.model.MessageId;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class InMemoryMessageId implements MessageId {

    public static class Factory implements MessageId.Factory {
        
        private AtomicLong counter = new AtomicLong();
        
        @Override
        public MessageId fromString(String serialized) {
            return of(Long.valueOf(serialized));
        }
        
        @Override
        public MessageId generate() {
            return of(counter.incrementAndGet());
        }
    }
    
    public static InMemoryMessageId of(long value) {
        return new InMemoryMessageId(value);
    }

    private final long value;

    private InMemoryMessageId(long value) {
        this.value = value;
    }

    @Override
    public String serialize() {
        return String.valueOf(value);
    }

    public long getRawId() {
        return value;
    }

    @Override
    public boolean isSerializable() {
        return true;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof InMemoryMessageId) {
            InMemoryMessageId other = (InMemoryMessageId) obj;
            return Objects.equal(this.value, other.value);
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(InMemoryMessageId.class)
                .add("value", value)
                .toString();
    }
}
