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

import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Objects;

public class TestMessageId implements MessageId {

    public static class Factory implements MessageId.Factory {
        
        private AtomicLong counter = new AtomicLong();
        
        @Override
        public MessageId fromString(String serialized) {
            return of(Long.parseLong(serialized));
        }
        
        @Override
        public MessageId generate() {
            return of(counter.incrementAndGet());
        }

    }
    
    public static TestMessageId of(long value) {
        return new TestMessageId(value);
    }

    private final long value;

    private TestMessageId(long value) {
        this.value = value;
    }

    @Override
    public String serialize() {
        return String.valueOf(value);
    }

    @Override
    public boolean isSerializable() {
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TestMessageId) {
            TestMessageId other = (TestMessageId) obj;
            return Objects.equal(this.value, other.value);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}
