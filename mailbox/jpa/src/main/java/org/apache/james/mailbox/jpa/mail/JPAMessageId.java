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

package org.apache.james.mailbox.jpa.mail;

import org.apache.james.mailbox.model.MessageId;

import java.io.Serializable;
import java.util.Objects;
import java.util.Random;

public class JPAMessageId implements MessageId {

    public static class Factory implements MessageId.Factory {
        @Override
        public MessageId fromString(String serialized) {
            return of(Long.valueOf(serialized));
        }

        @Override
        public MessageId generate() {
            return of(new Random().nextLong());
        }
    }

    private final long value;

    public JPAMessageId(long value) {
        this.value = value;
    }

    public static JPAMessageId of(long value) {return new JPAMessageId(value); }

    @Override
    public String serialize() {
        return String.valueOf(value);
    }

    @Override
    public boolean isSerializable() {
        return Serializable.class.isInstance(value);
    }

    @Override
    public String toString() {
        return "JPAMessageId{" +
                "value=" + value +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        JPAMessageId that = (JPAMessageId) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
